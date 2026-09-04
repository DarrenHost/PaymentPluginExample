package com.gs.payment.plugin.mdb

/**
 * 刷卡器配置（由 CashlessSetupCommand 解析）。
 *
 * 统一字段布局（去校验和；Pax 需先去掉可选的 `0x10` 头）：
 * `01 FL CC_H CC_L SF DP MT [...]`
 *
 * 样例：
 * - NAYAY `010311560102280DA3` → FL=3, CC=0x1156, SF=1, DP=2, MT=0x28
 * - Pax `100103184001005A09` → FL=3, CC=0x1840, SF=1, DP=0, MT=0x5A
 */
data class CashlessConfig(
    val featureLevel: Int,       // 功能等级 (03 = Level3)
    val countryCode: Int,        // 国家/货币码
    val scalingFactor: Int,      // 缩放系数
    val decimalPlaces: Int,      // 小数位
    val maxResponseTime: Int,    // 最大响应时间（×5ms）
    val rawPayload: ByteArray    // 原始载荷（不含校验和；Pax 仍含可选 0x10 头）
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CashlessConfig) return false
        return featureLevel == other.featureLevel && countryCode == other.countryCode
    }

    override fun hashCode(): Int = 31 * featureLevel + countryCode
}
