package com.gs.payment.plugin.mdb.cashless

import com.gs.payment.plugin.mdb.MdbCommand

/**
 * 出货成功通知（Vend Success）。
 * 发送：`13 02 II II` → ACK
 */
class VendSuccessCommand(
    private val itemId: Int = 0x0001
) : MdbCommand<Boolean>() {

    override val desc = "刷卡器出货成功"

    override fun encode(): ByteArray = byteArrayOf(
        0x13, 0x02,
        ((itemId shr 8) and 0xFF).toByte(),
        (itemId and 0xFF).toByte()
    )

    override val responseMode = ResponseMode.ACK_ONLY
    override fun decode(data: ByteArray) = true
}
