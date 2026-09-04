package com.gs.payment.plugin.mdb.event

import com.gs.payment.plugin.mdb.MdbFrameType
import com.gs.payment.plugin.mdb.MdbHex
import com.gs.payment.plugin.utils.Logger
import com.ok.serialport.data.Request
import com.ok.serialport.data.Response
import com.ok.serialport.data.ResponseProcess
import com.ok.serialport.data.ResponseRule
import com.ok.serialport.listener.OnResponseListener
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * MDB 刷卡器推送事件分发器。
 *
 * 注册为串口的全局 ResponseProcess（infiniteResponse），只处理无校验的
 * Cashless 主动上报帧，解析后通过 [events] Flow 广播给控制器与支付会话编排层。
 */
class MdbEventDispatcher {

    private val _events = MutableSharedFlow<MdbEvent>(replay = 0, extraBufferCapacity = 64)

    /** 解析后的刷卡器事件流 */
    val events: SharedFlow<MdbEvent> = _events.asSharedFlow()

    /**
     * 创建注册到 OkSerialPort 的全局 [ResponseProcess]。
     *
     * 单字节 FF 既是 NAK 也可能是离线事件，为避免抢走 ACK_ONLY 请求的 NAK，
     * 不在此全局规则中匹配；上层串口原始数据监听可按需调用 [handleFrame]。
     */
    fun createResponseProcess(): ResponseProcess {
        return ResponseProcess()
            .infiniteResponse()
            .addResponseRule(object : ResponseRule {
                override fun match(request: Request?, receive: ByteArray): Boolean {
                    return isUnsolicitedCashlessFrame(receive)
                }
            })
            .onResponseListener(object : OnResponseListener {
                override fun onResponse(response: Response) {
                    handleFrame(response.data)
                }

                override fun onFailure(request: Request?, e: Exception) {
                    Logger.w("MdbEventDispatcher", "[MDB事件] 全局帧接收失败: ${e.message}")
                }
            })
    }

    /** 是否为应交给本分发器的刷卡器无校验上报帧。 */
    fun isUnsolicitedCashlessFrame(data: ByteArray): Boolean {
        if (data.size < 2 || data[0].toInt() and 0xFF != 0x10) return false
        val opcode = data[1].toInt() and 0xFF
        // 这些 opcode 是已知的请求应答，不是主动上报：
        // 10 01 = Cashless Setup 应答
        // 10 07 = Session Complete/EndSession 应答
        // 10 09 = Read ID 应答
        if (opcode == 0x01 || opcode == 0x07 || opcode == 0x09) {
            return false
        }
        return MdbFrameType.isUnsolicitedPush(data)
    }

    /** 兼容旧调用名，判断是否应进入本分发器。 */
    fun isUnsolicitedEventFrame(data: ByteArray): Boolean = isUnsolicitedCashlessFrame(data)

    /**
     * 解析原始帧并广播事件。未识别的 Cashless 帧只记录日志。
     */
    fun handleFrame(data: ByteArray) {
        if (data.isEmpty()) return
        val hex = MdbHex.encode(data)

        val event = when {
            data.size == 1 && data[0] == 0xFF.toByte() -> {
                Logger.i("MdbEventDispatcher", "[MDB事件] 刷卡器离线(FF)")
                MdbEvent.CashlessOffline
            }
            data.size >= 2 && data[0].toInt() and 0xFF == 0x10 &&
                data[1].toInt() and 0xFF == 0x00 -> {
                Logger.i("MdbEventDispatcher", "[MDB事件] 刷卡器复位(1000)")
                MdbEvent.CashlessReset
            }
            data.size >= 2 && data[0].toInt() and 0xFF == 0x10 &&
                data[1].toInt() and 0xFF == 0x04 -> {
                Logger.i("MdbEventDispatcher", "[MDB事件] 刷卡会话取消请求")
                MdbEvent.CashlessSessionCancel
            }
            data.size >= 2 && data[0].toInt() and 0xFF == 0x10 &&
                data[1].toInt() and 0xFF == 0x05 -> {
                val amountScaled = if (data.size >= 4) {
                    ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
                } else {
                    0
                }
                Logger.i("MdbEventDispatcher", "[MDB事件] 刷卡批准(amountScaled=$amountScaled)")
                MdbEvent.CashlessVendApproved(amountScaled)
            }
            data.size >= 2 && data[0].toInt() and 0xFF == 0x10 &&
                data[1].toInt() and 0xFF == 0x06 -> {
                Logger.i("MdbEventDispatcher", "[MDB事件] 刷卡拒绝")
                MdbEvent.CashlessVendDenied
            }
            else -> {
                Logger.i("MdbEventDispatcher", "[MDB事件] 未识别刷卡帧 hex=$hex")
                null
            }
        }

        event?.let { _events.tryEmit(it) }
    }
}