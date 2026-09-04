package com.gs.payment.plugin.mdb.cashless

import com.gs.payment.plugin.mdb.MdbCommand

/**
 * 结束刷卡会话（Session Complete）。
 * 发送：`13 04` → 数据帧 `10 07 17`（EndSession，DATA_ONLY）
 */
class SessionCompleteCommand : MdbCommand<Boolean>() {

    override val desc = "刷卡器结束会话"
    override fun encode() = byteArrayOf(0x13, 0x04)

    override val responseMode = ResponseMode.DATA_ONLY
    override val dataTimeout = 3000L

    // 匹配 EndSession 响应：10 07 + checksum（3字节）
    override fun matchData(data: ByteArray): Boolean =
        data.size >= 2 && data[0] == 0x10.toByte() && data[1] == 0x07.toByte()

    override fun decode(data: ByteArray) = true
}
