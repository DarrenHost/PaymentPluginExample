package com.gs.payment.plugin.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val GeekColorScheme = darkColorScheme(
    primary = GeekNeon,
    onPrimary = GeekOnNeon,
    primaryContainer = GeekField,
    onPrimaryContainer = GeekNeon,
    secondary = GeekNeonDim,
    onSecondary = GeekText,
    background = GeekPanel,
    onBackground = GeekText,
    surface = GeekSurface,
    onSurface = GeekText,
    surfaceVariant = GeekField,
    onSurfaceVariant = GeekTextSecondary,
    outline = GeekNeonDim,
    error = GeekDanger,
    onError = GeekOnNeon
)

/**
 * 与悬浮窗一致的极客风深色主题。
 * 固定配色，不跟随系统深浅色或 Android 12+ 动态取色，避免两端观感不一致。
 */
@Composable
fun PaymentPluginScannerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GeekColorScheme,
        typography = Typography,
        content = content
    )
}
