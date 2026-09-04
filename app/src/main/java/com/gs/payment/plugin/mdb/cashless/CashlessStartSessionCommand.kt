package com.gs.payment.plugin.mdb.cashless

import com.gs.payment.plugin.mdb.MdbCommand


/**
 * 开启刷卡会话（Reader Enable）。
 * 发送：`14 01` → ACK
 */
class CashlessStartSessionCommand : MdbCommand<Boolean>() {
    override val desc = "刷卡器开启会话"
    override fun encode() = byteArrayOf(0x14, 0x01)
    override val responseMode = ResponseMode.ACK_ONLY
    override fun decode(data: ByteArray) = true
}
