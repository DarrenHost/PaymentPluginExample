package com.gs.payment.plugin.mdb.cashless

import com.gs.payment.plugin.mdb.MdbCommand

/**
 * 设置最大/最小价格。
 * 发送：`11 01 FF FF 00 00` → ACK
 */
class CashlessMaxPriceCommand(
    private val maxPrice: Int = 0xFFFF,
    private val minPrice: Int = 0x0000
) : MdbCommand<Boolean>() {

    override val desc = "刷卡器设置最大最小价格"

    override fun encode(): ByteArray = byteArrayOf(
        0x11, 0x01,
        ((maxPrice shr 8) and 0xFF).toByte(),
        (maxPrice and 0xFF).toByte(),
        ((minPrice shr 8) and 0xFF).toByte(),
        (minPrice and 0xFF).toByte()
    )

    override val responseMode = ResponseMode.ACK_ONLY
    override fun decode(data: ByteArray) = true
}
