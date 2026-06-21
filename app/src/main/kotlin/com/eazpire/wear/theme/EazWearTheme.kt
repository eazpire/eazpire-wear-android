package com.eazpire.wear.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.LocalContentColor

/** Brand tokens aligned with eazpire.com (orange #f97316, navy bg). */
object EazWearColors {
    val Orange = Color(0xFFF97316)
    val OrangeDark = Color(0xFFEA580C)
    val Purple = Color(0xFFA78BFA)
    /** Solid app background — no gradient (reliable on all devices). */
    val Background = Color(0xFF0F172A)
    val Panel = Color(0xFF334155)
    val PanelBorder = Color(0xFF64748B)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextMuted = Color(0xFF94A3B8)
    val TextSubtle = Color(0xFFE2E8F0)
}

private val WearColorScheme = darkColorScheme(
    primary = EazWearColors.Orange,
    onPrimary = Color.White,
    secondary = EazWearColors.Purple,
    background = EazWearColors.Background,
    surface = EazWearColors.Panel,
    surfaceVariant = Color(0xFF1E293B),
    onSecondary = Color.White,
    onBackground = EazWearColors.TextPrimary,
    onSurface = EazWearColors.TextPrimary,
    onSurfaceVariant = EazWearColors.TextMuted,
)

@Composable
fun EazWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = WearColorScheme) {
        CompositionLocalProvider(LocalContentColor provides EazWearColors.TextPrimary) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = EazWearColors.Background,
            ) {
                content()
            }
        }
    }
}

/** Optional wrapper for tab screens — same solid bg. */
@Composable
fun EazWearScreenBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(EazWearColors.Background),
    ) {
        content()
    }
}
