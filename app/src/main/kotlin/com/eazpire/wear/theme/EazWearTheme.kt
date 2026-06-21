package com.eazpire.wear.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Brand tokens aligned with eazpire.com / eaz-redesign (orange #f97316, dark navy bg). */
object EazWearColors {
    val Orange = Color(0xFFF97316)
    val OrangeDark = Color(0xFFEA580C)
    val OrangeLight = Color(0xFFFF9A2A)
    val Purple = Color(0xFFA78BFA)
    val BgTop = Color(0xFF0F172A)
    val BgBottom = Color(0xFF0A0F18)
    val Panel = Color(0xFF111827)
    val PanelElevated = Color(0xFF1E293B)
    val TextPrimary = Color(0xFFF8FAFC)
    val TextMuted = Color(0xFF94A3B8)
    val TextSubtle = Color(0xFFCBD5E1)
}

private val WearColorScheme = darkColorScheme(
    primary = EazWearColors.Orange,
    onPrimary = Color.White,
    secondary = EazWearColors.Purple,
    background = EazWearColors.BgBottom,
    surface = EazWearColors.Panel,
    surfaceVariant = EazWearColors.PanelElevated,
    onSecondary = Color.White,
    onBackground = EazWearColors.TextPrimary,
    onSurface = EazWearColors.TextPrimary,
    onSurfaceVariant = EazWearColors.TextMuted,
)

val EazWearBackgroundBrush: Brush
    get() = Brush.linearGradient(
        colors = listOf(
            EazWearColors.BgTop,
            EazWearColors.BgBottom,
            Color(0xFF070B14),
        ),
    )

@Composable
fun EazWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WearColorScheme,
        content = content,
    )
}

/** Site-style dark gradient behind Wear screens (readable light text). */
@Composable
fun EazWearScreenBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(EazWearBackgroundBrush),
    ) {
        content()
    }
}
