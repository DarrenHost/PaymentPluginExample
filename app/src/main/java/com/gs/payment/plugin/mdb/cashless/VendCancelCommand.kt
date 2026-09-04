package com.gs.payment.plugin.mdb.cashless

import com.gs.payment.plugin.mdb.MdbCommand

/**
 * 取消扣款（Vend Cancel）。
 * 发送：`13 01` → ACK
 */
class VendCancelCommand : MdbCommand<Boolean>() {
    override val desc = "刷卡器取消扣款"
    override fun encode() = byteArrayOf(0x13, 0x01)
    override val responseMode = ResponseMode.ACK_ONLY
    override fun decode(data: ByteArray) = true
}
