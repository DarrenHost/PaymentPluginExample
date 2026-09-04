package com.gs.payment.plugin.mdb

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 分与 MDB Setup 缩放单位换算。
 *
 * 1 缩放单位 = scalingFactor × 10^(-decimalPlaces) 主币。
 * `scaled = (fen / 100) × 10^decimalPlaces / scalingFactor`
 */
internal object MdbScale {
    fun fenToScaledUnits(amountFen: Long, scalingFactor: Int, decimalPlaces: Int): Long {
        if (amountFen <= 0) return 0L
        val sf = scalingFactor.coerceAtLeast(1)
        val dp = decimalPlaces.coerceAtLeast(0)
        val multiplier = BigDecimal.TEN.pow(dp)
        val amountYuan = BigDecimal.valueOf(amountFen).divide(
            BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP
        )
        return amountYuan
            .multiply(multiplier)
            .divide(BigDecimal.valueOf(sf.toLong()), 0, RoundingMode.HALF_UP)
            .toLong()
    }

    fun scaledUnitsToFen(units: Long, scalingFactor: Int, decimalPlaces: Int): Long {
        if (units <= 0) return 0L
        val sf = scalingFactor.coerceAtLeast(1)
        val dp = decimalPlaces.coerceAtLeast(0)
        val multiplier = BigDecimal.TEN.pow(dp)
        return BigDecimal.valueOf(units)
            .multiply(BigDecimal.valueOf(sf.toLong()))
            .divide(multiplier, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()
    }
}
