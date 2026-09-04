package com.gs.payment.plugin.mdb.cashless

import com.gs.payment.plugin.mdb.MdbCommand

/**
 * 刷卡器重置（RESET）。
 * 发送：`10` → ACK(`00`)；业务成功以随后推送 Just Reset(`10 00`) 为准。
 */
class CashlessResetCommand : MdbCommand<Boolean>() {
    override val desc = "刷卡器重置"
    override fun encode() = byteArrayOf(0x10)
    override val responseMode = ResponseMode.ACK_ONLY
    override fun decode(data: ByteArray) = true
}
