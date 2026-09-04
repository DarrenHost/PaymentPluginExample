package com.gs.payment.plugin.mdb

import com.gs.payment.plugin.domain.SerialPortManager
import com.gs.payment.plugin.mdb.event.MdbEvent
import com.gs.payment.plugin.mdb.event.MdbEventDispatcher
import com.gs.payment.plugin.utils.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * MDB 刷卡支付
 *
 * 与原版状态机对齐：
 * - StartPayReceiver 收到 PAY_ACTION 后：初始化/恢复刷卡器 -> VendRequest -> 等待 10 05；
 * - 收到 10 05 后回调“刷卡成功”，但暂不结算，等待业务制作反馈；
 * - FeedbackPayReceiver 收到成功/失败后下发 VendSuccess/VendFail + SessionComplete；
 * - CancelPayReceiver 收到取消后撤销进行中的 Vend 并结束会话。
 */
object MdbCardPaymentManager {

    private const val TAG = "MdbCardPayment"

    /** 与原版 MdbPaymentEngine 一致的刷卡批准等待超时。 */
    private const val APPROVAL_TIMEOUT_MS = 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dispatcher = MdbEventDispatcher()
    private val cashless = MdbCashlessController(SerialPortManager)
    private val mutex = Mutex()

    private var observerJob: Job? = null
    private var paymentJobRef = AtomicReference<Job?>(null)
    private var cancelRequested = AtomicBoolean(false)

    private var current: ActivePayment? = null
    private var phase: Phase = Phase.IDLE

    private enum class Phase {
        IDLE,
        WAITING_APPROVAL,
        APPROVED
    }

    private data class ActivePayment(
        val sessionId: String,
        val orderId: String,
        val amountFen: Long,
        val itemId: Int
    )

    private sealed class Outcome {
        data object Approved : Outcome()
        data class Failed(val reason: String, val needVendCancel: Boolean) : Outcome()
    }

    /**
     * 串口服务连接后调用，空闲时先完成一次刷卡器初始化，避免首次 PAY_ACTION 等太久。
     */
    fun prepare(cardLevel: Int = 3) {
        scope.launch {
            mutex.withLock {
                cashless.setCardLevel(cardLevel)
                if (!ensureRegisteredLocked()) return@withLock
                if (cashless.state == MdbDeviceState.READY) return@withLock
                runCatching { cashless.initialize() }
                    .onSuccess { Logger.i(TAG, "刷卡器初始化完成: ${it.featureLevel}") }
                    .onFailure { Logger.w(TAG, "刷卡器初始化失败: ${it.message}") }
            }
        }
    }

    /**
     * 切换 MDB 刷卡等级并重新初始化刷卡器。
     *
     * @param onResult 初始化结果回调；等级保存由调用方负责。
     */
    fun changeCardLevelAndReinitialize(
        level: Int,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        scope.launch {
            mutex.withLock {
                paymentJobRef.getAndSet(null)?.cancel()
                cancelRequested.set(false)
                current = null
                phase = Phase.IDLE

                cashless.setCardLevel(level)
                cashless.resetForReconnect()

                if (!ensureRegisteredLocked()) {
                    onResult(false, "MDB串口未连接，已保存等级，服务重连后将按 Level$level 初始化")
                    return@withLock
                }

                runCatching { cashless.initialize() }
                    .onSuccess {
                        Logger.i(TAG, "MDB等级切换完成: Level$level")
                        onResult(true, "MDB等级已切换为 Level$level，初始化完成")
                    }
                    .onFailure {
                        Logger.w(TAG, "MDB等级 Level$level 初始化失败: ${it.message}")
                        onResult(false, "MDB等级切换失败: ${it.message}")
                    }
            }
        }
    }

    /** 串口关闭时清空旧会话与旧连接上的刷卡器状态。 */
    fun onSerialClosed() {
        scope.launch {
            mutex.withLock {
                paymentJobRef.getAndSet(null)?.cancel()
                cancelRequested.set(false)
                current = null
                phase = Phase.IDLE
                cashless.resetForReconnect()
                SerialPortManager.unregisterMdbEventDispatcher()
            }
        }
    }

    /**
     * 启动一笔 MDB 刷卡支付。
     *
     * @param onResult 刷卡结果回调：仅在批准/失败/超时时调用一次；用户主动取消不回调失败。
     */
    fun startCardPayment(
        orderId: String,
        amountFen: Long,
        itemId: Int,
        onResult: (Boolean, String) -> Unit
    ) {
        paymentJobRef.getAndSet(null)?.cancel()
        cancelRequested.set(false)

        val job = scope.launch(start = CoroutineStart.LAZY) {
            runStartPayment(orderId, amountFen, itemId, onResult)
        }
        paymentJobRef.set(job)
        job.start()
    }

    /** 取消进行中的刷卡支付；若已经批准则由后续制作反馈决定冲正。 */
    fun cancelCardPayment() {
        val job = paymentJobRef.get()
        if (job?.isActive == true) {
            cancelRequested.set(true)
            job.cancel()
            return
        }
        // 已批准的会话不再由取消入口撤销，等待 FeedbackPayReceiver 冲正。
        Logger.i(TAG, "当前没有等待批准中的 MDB 支付会话")
    }

    /**
     * 制作结果反馈：成功时下发 VendSuccess，失败时下发 VendFail，随后结束 MDB 会话。
     */
    fun finishCardPayment(success: Boolean, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        scope.launch {
            mutex.withLock {
                val session = current
                if (session == null) {
                    onResult(false, "没有待结算的 MDB 刷卡会话")
                    return@withLock
                }
                if (phase != Phase.APPROVED) {
                    onResult(false, "刷卡会话尚未进入已批准状态，无法结算")
                    return@withLock
                }

                val notifyResult = if (success) {
                    runCatching { cashless.vendSuccess(session.itemId) }
                } else {
                    runCatching { cashless.vendFail() }
                }
                val notifyOk = notifyResult.onFailure {
                    Logger.w(TAG, "通知刷卡器出货结果失败: ${it.message}")
                }.isSuccess

                if (!notifyOk) {
                    onResult(false, if (success) "通知刷卡器出货成功失败" else "通知刷卡器出货失败/冲正失败")
                    return@withLock
                }

                runCatching { cashless.sessionComplete() }
                    .onFailure { Logger.w(TAG, "刷卡结算结束会话失败: ${it.message}") }

                current = null
                phase = Phase.IDLE
                Logger.i(TAG, "MDB 刷卡结算完成 success=$success orderId=${session.orderId}")
                onResult(true, if (success) "MDB刷卡交易结算成功" else "MDB刷卡交易已冲正")
            }
        }
    }

    private suspend fun CoroutineScope.runStartPayment(
        orderId: String,
        amountFen: Long,
        itemId: Int,
        onResult: (Boolean, String) -> Unit
    ) {
        val session = ActivePayment(
            sessionId = UUID.randomUUID().toString(),
            orderId = orderId,
            amountFen = amountFen,
            itemId = itemId
        )
        var delivered = false

        fun deliver(success: Boolean, message: String) {
            if (delivered) return
            delivered = true
            onResult(success, message)
        }

        var requestSent = false
        var approval: Deferred<MdbEvent>? = null
        try {
            val started = mutex.withLock {
                if (current != null) {
                    deliver(false, "已有进行中的MDB支付")
                    return@withLock false
                }
                if (!ensureRegisteredLocked()) {
                    deliver(false, "MDB串口未连接")
                    return@withLock false
                }
                val ready = runCatching { cashless.ensureReadyForVend() }.isSuccess
                if (!ready) {
                    deliver(false, MdbCashlessController.RESET_FAILED_FOR_PAY_MSG)
                    return@withLock false
                }
                current = session
                phase = Phase.WAITING_APPROVAL
                true
            }
            if (!started) return

            // 先订阅批准/拒绝事件，再下发 VendRequest，避免批准推送在订阅前到达而丢失。
            approval = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(APPROVAL_TIMEOUT_MS) {
                    dispatcher.events.first { event ->
                        terminalOutcome(event) != null
                    }
                }
            }

            mutex.withLock {
                if (current?.sessionId != session.sessionId) return@withLock
                cashless.vendRequest(cashless.fenToScaled(session.amountFen), session.itemId)
                requestSent = true
                Logger.i(
                    TAG,
                    "MDB VendRequest 已下发 orderId=$orderId amountFen=$amountFen itemId=$itemId"
                )
            }

            val terminalEvent = approval.await()
            when (val outcome = terminalOutcome(terminalEvent)) {
                is Outcome.Approved -> {
                    mutex.withLock { phase = Phase.APPROVED }
                    deliver(true, "刷卡支付成功")
                }
                is Outcome.Failed -> {
                    withContext(NonCancellable) {
                        settleFailed(session, outcome.needVendCancel)
                    }
                    deliver(false, outcome.reason)
                }
                null -> Unit
            }
        } catch (e: TimeoutCancellationException) {
            withContext(NonCancellable) {
                settleFailed(session, needVendCancel = true)
            }
            deliver(false, "刷卡支付超时")
        } catch (e: CancellationException) {
            val external = cancelRequested.compareAndSet(true, false)
            withContext(NonCancellable) {
                cancelWaitingSession(session, requestSent)
            }
            if (!external) throw e
            Logger.i(TAG, "MDB 支付已被用户取消 orderId=$orderId")
        } catch (e: Exception) {
            withContext(NonCancellable) {
                if (requestSent) {
                    cancelWaitingSession(session, requestSent)
                } else {
                    mutex.withLock {
                        if (current?.sessionId == session.sessionId) {
                            current = null
                            phase = Phase.IDLE
                        }
                    }
                }
            }
            deliver(false, "发起MDB支付失败: ${e.message}")
        } finally {
            approval?.cancel()
        }
    }

    private suspend fun settleFailed(session: ActivePayment, needVendCancel: Boolean) {
        mutex.withLock {
            if (current?.sessionId != session.sessionId) return@withLock
            if (needVendCancel) {
                runCatching { cashless.vendCancel() }
                    .onFailure { Logger.w(TAG, "刷卡失败收尾 VendCancel 失败: ${it.message}") }
            }
            runCatching { cashless.sessionComplete() }
                .onFailure { Logger.w(TAG, "刷卡失败收尾 SessionComplete 失败: ${it.message}") }
            current = null
            phase = Phase.IDLE
        }
    }

    private suspend fun cancelWaitingSession(session: ActivePayment, requestSent: Boolean) {
        mutex.withLock {
            if (current?.sessionId != session.sessionId) return@withLock
            if (requestSent) {
                runCatching { cashless.vendCancel() }
                    .onFailure { Logger.w(TAG, "取消支付 VendCancel 失败: ${it.message}") }
            }
            runCatching { cashless.sessionComplete() }
                .onFailure { Logger.w(TAG, "取消支付 SessionComplete 失败: ${it.message}") }
            current = null
            phase = Phase.IDLE
        }
    }

    private fun terminalOutcome(event: MdbEvent): Outcome? {
        return when (event) {
            is MdbEvent.CashlessVendApproved -> Outcome.Approved
            is MdbEvent.CashlessVendDenied -> Outcome.Failed("刷卡拒绝", needVendCancel = false)
            is MdbEvent.CashlessSessionCancel -> Outcome.Failed(
                "刷卡会话取消",
                needVendCancel = true
            )

            is MdbEvent.CashlessReset -> Outcome.Failed("刷卡器复位", needVendCancel = true)
            is MdbEvent.CashlessOffline -> Outcome.Failed("刷卡器离线", needVendCancel = true)
        }
    }
    private fun ensureRegisteredLocked(): Boolean {
        if (!SerialPortManager.isConnected()) {
            Logger.w(TAG, "MDB 串口未连接")
            return false
        }
        SerialPortManager.registerMdbEventDispatcher(dispatcher)
        if (observerJob?.isActive != true) {
            observerJob = cashless.observeEvents(dispatcher.events, scope)
        }
        return true
    }
}