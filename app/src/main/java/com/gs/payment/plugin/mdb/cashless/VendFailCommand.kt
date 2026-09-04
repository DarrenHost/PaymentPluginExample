package com.gs.payment.plugin.mdb.cashless

import com.gs.payment.plugin.mdb.MdbCommand

/**
 * 出货失败通知（Vend Failure），刷卡器执行冲正/退款。
 * 发送：`13 03` → ACK
 */
class VendFailCommand : MdbCommand<Boolean>() {
    override val desc = "刷卡器出货失败/冲正"
    override fun encode() = byteArrayOf(0x13, 0x03)
    override val responseMode = ResponseMode.ACK_ONLY
    override fun decode(data: ByteArray) = true
}
