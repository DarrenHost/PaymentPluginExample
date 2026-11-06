package com.gs.payment.plugin

/**
 * 极客风配色的唯一来源。
 * 悬浮窗（原生 View）直接使用这里的 ARGB Int；Compose 侧由 ui/theme/Color.kt 转成 Color，
 * 保证 MainActivity 与悬浮窗配色始终一致。
 */
object GeekPalette {
    val PANEL = 0xFF101820.toInt()
    val SURFACE = 0xFF16232C.toInt()
    val FIELD = 0xFF08121A.toInt()
    val NEON = 0xFF00E5C0.toInt()
    val NEON_DIM = 0xFF1D5F57.toInt()
    val TEXT_PRIMARY = 0xFFD6F7F0.toInt()
    val TEXT_SECONDARY = 0xFF64838C.toInt()
    val DANGER = 0xFFFF5C7A.toInt()
    val ON_NEON = 0xFF06120F.toInt()
    val SCRIM = 0xE6000000.toInt()
}
