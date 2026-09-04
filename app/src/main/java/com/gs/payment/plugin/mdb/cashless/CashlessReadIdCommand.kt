package com.gs.payment.plugin.mdb.cashless

import com.gs.payment.plugin.mdb.MdbCommand
import com.gs.payment.plugin.mdb.MdbHex

/**
 * 读取刷卡器设备 ID。
 * 发送：`17 00 4E 45 43 30 30 30 30 30 30 30 30 30 30 30 30 20 20 20 53 4F 4C 49 53 54 41 20 20 11`
 * 响应（DATA_ONLY）：约 34 字节，如 `10 09 50 41 58 46...`
 */
class CashlessReadIdCommand : MdbCommand<ByteArray>() {

    override val desc = "刷卡器读取设备ID"

    override fun encode(): ByteArray = byteArrayOf(
        0x17, 0x00, 0x4E, 0x45, 0x43, 0x30, 0x30, 0x30,
        0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30,
        0x30, 0x20, 0x20, 0x20, 0x53, 0x4F, 0x4C, 0x49,
        0x53, 0x54, 0x41, 0x20, 0x20,0x00, 0x11)

    override val responseMode = ResponseMode.DATA_ONLY
    override val dataTimeout = 4000L

    override fun matchData(data: ByteArray): Boolean =
        data.size >= 31 && ((data[0] == 0x10.toByte() && data[1] == 0x09.toByte()) || data[0] == 0x09.toByte())

    override fun decode(data: ByteArray): ByteArray = data.payload()

    override fun formatResult(result: ByteArray): String = MdbHex.encode(result)
}
