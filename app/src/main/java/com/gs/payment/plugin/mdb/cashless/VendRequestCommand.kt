package com.gs.payment.plugin.mdb.cashless

import com.gs.payment.plugin.mdb.MdbCommand

/**
 * 发起扣款请求（Vend Request）。
 * 发送：`13 00 PP PP II II` → ACK（批准/拒绝通过推送事件返回）
 *
 * @param priceScaled 缩放后的价格（MDB 单位），由业务层按比例系数换算
 * @param itemId 商品编号
 */
class VendRequestCommand(
    private val priceScaled: Int,
    private val itemId: Int = 0x0001
) : MdbCommand<Boolean>() {

    override val desc = "刷卡器扣款请求(price=$priceScaled, item=$itemId)"

    override fun encode(): ByteArray = byteArrayOf(
        0x13, 0x00,
        ((priceScaled shr 8) and 0xFF).toByte(),
        (priceScaled and 0xFF).toByte(),
        ((itemId shr 8) and 0xFF).toByte(),
        (itemId and 0xFF).toByte()
    )

    override val responseMode = ResponseMode.ACK_ONLY
    override val ackTimeout = 2000L
    override fun decode(data: ByteArray) = true
}
