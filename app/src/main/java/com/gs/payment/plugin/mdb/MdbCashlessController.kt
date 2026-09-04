package com.gs.payment.plugin.mdb

import com.gs.payment.plugin.domain.SerialPortManager
import com.gs.payment.plugin.mdb.cashless.CashlessEnableCommand
import com.gs.payment.plugin.mdb.cashless.CashlessMaxPriceCommand
import com.gs.payment.plugin.mdb.cashless.CashlessReadIdCommand
import com.gs.payment.plugin.mdb.cashless.CashlessResetCommand
import com.gs.payment.plugin.mdb.cashless.CashlessSetupCommand
import com.gs.payment.plugin.mdb.cashless.CashlessStartSessionCommand
import com.gs.payment.plugin.mdb.cashless.SessionCompleteCommand
import com.gs.payment.plugin.mdb.cashless.VendCancelCommand
import com.gs.payment.plugin.mdb.cashless.VendFailCommand
import com.gs.payment.plugin.mdb.cashless.VendRequestCommand
import com.gs.payment.plugin.mdb.cashless.VendSuccessCommand
import com.gs.payment.plugin.mdb.event.MdbEvent
import com.gs.payment.plugin.utils.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield

/**
 * MDB 刷卡器控制器（由 coffee-machine lib-hw-payment 的 Cashless 部分裁剪而来）。
 *
 * 职责：
 * 1. 初始化：Reset(等 Just Reset) -> Setup -> MaxPrice -> ReadId -> Enable -> StartSession
 * 2. 发起/取消/结束 Vend 会话
 * 3. 监听 CashlessReset/CashlessOffline 推送并恢复设备状态
 *
 * VendApproved/Denied 等结果由上层支付会话通过事件流编排，不在此控制器内结算。
 */
class MdbCashlessController(
    private val transport: SerialPortManager = SerialPortManager,
    private val resetJustResetTimeoutMs: Long = RESET_JUST_RESET_TIMEOUT_MS
) {

    companion object {
        /** 单次重置：等待 Just Reset 的超时 */
        const val RESET_JUST_RESET_TIMEOUT_MS = 5_000L

        /** 支付前重置失败时对外提示 */
        const val RESET_FAILED_FOR_PAY_MSG = "刷卡器重置失败，无法进行支付"
    }

    var state: MdbDeviceState = MdbDeviceState.UNKNOWN
        private set

    var config: CashlessConfig? = null
        private set

    private var cardLevel: Int = 3

    var onStateChanged: ((MdbDeviceState) -> Unit)? = null

    /** 主动 Reset 后等待 Just Reset(10 00) 的挂起点 */
    private var resetJustResetWait: CompletableDeferred<Unit>? = null

    /** 是否正在等待主动 Reset 的 Just Reset，供上层区分主动复位与自发复位。 */
    fun isAwaitingJustReset(): Boolean = resetJustResetWait != null

    fun setCardLevel(level: Int) {
        cardLevel = level
    }

    /** 串口重开/断开后清空旧连接上的设备状态，下次支付前会重新初始化。 */
    fun resetForReconnect() {
        resetJustResetWait = null
        config = null
        setState(MdbDeviceState.UNKNOWN)
    }

    /**
     * 订阅刷卡器本地事件：主动 Reset 确认、自发复位重初始化、离线置位。
     */
    fun observeEvents(events: SharedFlow<MdbEvent>, scope: CoroutineScope): Job {
        return events.onEach { event ->
            when (event) {
                is MdbEvent.CashlessReset -> {
                    val waiting = resetJustResetWait
                    if (waiting != null) {
                        Logger.i("MdbPayment", "[Cashless] 收到复位事件，主动重置确认成功")
                        waiting.complete(Unit)
                    } else if (state == MdbDeviceState.INITIALIZING) {
                        Logger.i("MdbPayment", "[Cashless] 初始化中忽略复位事件")
                    } else {
                        Logger.i("MdbPayment", "[Cashless] 收到自发复位事件，重新初始化")
                        setState(MdbDeviceState.INITIALIZING)
                        scope.launch {
                            runCatching { initialize() }
                                .onFailure { Logger.w("MdbPayment", "[Cashless] 自发复位后重新初始化失败: ${it.message}") }
                        }
                    }
                }

                is MdbEvent.CashlessOffline -> {
                    Logger.w("MdbPayment", "[Cashless] 刷卡器离线")
                    setState(MdbDeviceState.OFFLINE)
                }

                else -> Unit
            }
        }.launchIn(scope)
    }

    /**
     * 完整初始化：单次发 Reset，等待 Just Reset 后继续 Setup -> StartSession。
     */
    suspend fun initialize(): CashlessConfig {
        setState(MdbDeviceState.INITIALIZING)
        try {
            awaitResetJustResetOnce()
            val cfg = transport.sendSuspend(CashlessSetupCommand(cardLevel))
            config = cfg
            Logger.i(
                "MdbPayment",
                "[Cashless] Setup完成: featureLevel=${cfg.featureLevel}, " +
                    "countryCode=${cfg.countryCode}, scalingFactor=${cfg.scalingFactor}, " +
                    "decimalPlaces=${cfg.decimalPlaces}, maxResponseTime=${cfg.maxResponseTime}"
            )

            transport.sendSuspend(CashlessMaxPriceCommand())
            transport.sendSuspend(CashlessReadIdCommand())
            transport.sendSuspend(CashlessEnableCommand())
            transport.sendSuspend(CashlessStartSessionCommand())

            setState(MdbDeviceState.READY)
            Logger.i("MdbPayment", "[Cashless] 初始化完成")
            return cfg
        } catch (e: Exception) {
            setState(MdbDeviceState.ERROR)
            throw e
        }
    }

    private suspend fun awaitResetJustResetOnce() {
        val wait = CompletableDeferred<Unit>()
        resetJustResetWait = wait
        try {
            transport.sendSuspend(CashlessResetCommand())
            yield()
            try {
                withTimeout(resetJustResetTimeoutMs) { wait.await() }
            } catch (e: Exception) {
                throw IllegalStateException(
                    "刷卡器重置超时(${resetJustResetTimeoutMs}ms内未收到Just Reset)",
                    e
                )
            }
            Logger.i("MdbPayment", "[Cashless] 重置成功(收到 Just Reset)")
        } finally {
            resetJustResetWait = null
        }
    }

    /**
     * 确保刷卡器处于可发起 VendRequest 的 READY 状态。
     */
    suspend fun ensureReadyForVend() {
        if (state == MdbDeviceState.READY) return

        Logger.w("MdbPayment", "[Cashless] 发起扣款前状态非 READY($state)，尝试恢复")
        val needsFullReset = state == MdbDeviceState.ERROR ||
            state == MdbDeviceState.OFFLINE ||
            state == MdbDeviceState.UNKNOWN ||
            state == MdbDeviceState.INITIALIZING
        if (!needsFullReset) {
            sessionComplete()
        }
        if (state != MdbDeviceState.READY) {
            Logger.w("MdbPayment", "[Cashless] 执行单次重初始化(当前状态=$state)")
            runCatching { initialize() }
                .getOrElse {
                    Logger.w("MdbPayment", "[Cashless] 重初始化失败: ${it.message}")
                    throw IllegalStateException(RESET_FAILED_FOR_PAY_MSG, it)
                }
            if (state != MdbDeviceState.READY) {
                throw IllegalStateException(RESET_FAILED_FOR_PAY_MSG)
            }
        }
    }

    /** 发起扣款请求，价格已按比例系数换算。 */
    suspend fun vendRequest(priceScaled: Int, itemId: Int = 1) {
        ensureReadyForVend()
        transport.sendSuspend(VendRequestCommand(priceScaled, itemId))
        setState(MdbDeviceState.ENABLED)
    }

    /** 通知出货成功。 */
    suspend fun vendSuccess(itemId: Int = 1) {
        transport.sendSuspend(VendSuccessCommand(itemId))
    }

    /** 通知出货失败（触发刷卡器冲正）。 */
    suspend fun vendFail() {
        transport.sendSuspend(VendFailCommand())
    }

    /** 取消扣款。 */
    suspend fun vendCancel() {
        transport.sendSuspend(VendCancelCommand())
    }

    /**
     * 结束会话并重新开启下一轮。13 04 无响应时仍继续 14 01；14 01 失败则轻量重初始化。
     */
    suspend fun sessionComplete() {
        val endOk = runCatching {
            transport.sendSuspend(SessionCompleteCommand())
        }.onFailure {
            Logger.w("MdbPayment", "[Cashless] 结束会话失败(继续 StartSession): ${it.message}")
        }.isSuccess

        val startOk = runCatching {
            transport.sendSuspend(CashlessStartSessionCommand())
            setState(MdbDeviceState.READY)
        }.onFailure {
            Logger.w("MdbPayment", "[Cashless] StartSession 失败: ${it.message}")
        }.isSuccess

        if (!startOk) {
            Logger.w("MdbPayment", "[Cashless] StartSession 失败，执行轻量重初始化")
            runCatching { initialize() }
                .onFailure { Logger.w("MdbPayment", "[Cashless] 轻量重初始化失败: ${it.message}") }
        } else if (!endOk) {
            Logger.i("MdbPayment", "[Cashless] 结束会话无响应但已重新 StartSession")
        }
    }

    /** 按金额(分)换算 MDB 缩放值并限制在 2 字节价格范围内。 */
    fun fenToScaled(amountFen: Long): Int {
        val sf = config?.scalingFactor ?: 1
        val dp = config?.decimalPlaces ?: 2
        return MdbScale.fenToScaledUnits(amountFen, sf, dp).toInt().coerceIn(1, 0xFFFF)
    }

    private fun setState(newState: MdbDeviceState) {
        if (state != newState) {
            state = newState
            onStateChanged?.invoke(newState)
        }
    }
}