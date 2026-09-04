package com.gs.payment.plugin.mdb.cashless

import com.gs.payment.plugin.mdb.CashlessConfig
import com.gs.payment.plugin.mdb.MdbCommand

/**
 * 刷卡器初始化配置命令。
 *
 * 发送：`11 00 02 00 00 02`（Level2）或 `11 00 03 00 00 00`（Level3）
 *
 * 响应（DATA_ONLY，含校验和）样例：
 * - NAYAY：`01 03 11 56 01 02 28 0D A3`
 * - Pax：`10 01 03 18 40 01 00 5A 09`（比 NAYAY 多一个 `0x10` 头）
 *
 * 去校验和后，若首字节为 `0x10` 则去掉该头，再按统一布局解析：
 * `[0x01, featureLevel, countryHigh, countryLow, scalingFactor, decimalPlaces, maxRespTime, ...]`
 */
class CashlessSetupCommand(private val level: Int = 3) : MdbCommand<CashlessConfig>() {

    override val desc = "刷卡器初始化(Level$level)"

    override fun encode(): ByteArray = when (level) {
        2 -> byteArrayOf(0x11, 0x00, 0x02, 0x00, 0x00, 0x02)
        else -> byteArrayOf(0x11, 0x00, 0x03, 0x00, 0x00, 0x00)
    }

    override val responseMode = ResponseMode.DATA_ONLY
    override val dataTimeout = 4000L

    /**
     * 此处为兼容不同品牌的刷卡器返回值的差异
     *
     * 010311560102280DA3  NAYAY刷卡器  标准MDB协议（4.2）
     * 100103184001005A09  Pax刷卡器
     *
     * @param data ByteArray
     * @return Boolean
     */
    override fun matchData(data: ByteArray): Boolean =
        data.size >= 9 && ((data[0] == 0x10.toByte() && data[1] == 0x01.toByte()) || (data[0] == 0x01.toByte() && data[1].toInt() and 0xFF == level))

    override fun decode(data: ByteArray): CashlessConfig {
        val raw = data.payload()
        val p = if (raw.isNotEmpty() && raw[0] == 0x10.toByte()) {
            raw.copyOfRange(1, raw.size)
        } else {
            raw
        }
        val featureLevel = if (p.size > 1) p[1].toInt() and 0xFF else 0
        val countryCode = if (p.size > 3) {
            ((p[2].toInt() and 0xFF) shl 8) or (p[3].toInt() and 0xFF)
        } else 0
        val scalingFactor = if (p.size > 4) p[4].toInt() and 0xFF else 1
        val decimalPlaces = if (p.size > 5) p[5].toInt() and 0xFF else 2
        val maxResponseTime = if (p.size > 6) p[6].toInt() and 0xFF else 0
        return CashlessConfig(featureLevel, countryCode, scalingFactor, decimalPlaces, maxResponseTime, raw)
    }

    override fun formatResult(result: CashlessConfig): String =
        "featureLevel=${result.featureLevel}, countryCode=${result.countryCode}, " +
            "scalingFactor=${result.scalingFactor}, decimalPlaces=${result.decimalPlaces}, " +
            "maxResponseTime=${result.maxResponseTime}"
}
