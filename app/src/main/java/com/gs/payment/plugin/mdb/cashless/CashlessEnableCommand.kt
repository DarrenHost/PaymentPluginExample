package com.gs.payment.plugin.mdb.cashless

import com.gs.payment.plugin.mdb.MdbCommand

/**
 * 启用刷卡器（Expansion Enable）。
 * 发送：`17 04 00 00 00 20` → ACK
 */
class CashlessEnableCommand : MdbCommand<Boolean>() {
    override val desc = "刷卡器启用"
    override fun encode() = byteArrayOf(0x17, 0x04, 0x00, 0x00, 0x00, 0x20)
    override val responseMode = ResponseMode.ACK_ONLY
    override fun decode(data: ByteArray) = true
}
