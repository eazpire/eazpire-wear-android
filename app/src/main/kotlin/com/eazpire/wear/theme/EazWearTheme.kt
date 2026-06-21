package com.eazpire.wear.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** Brand tokens aligned with eazpire.com (orange #f97316). Light surfaces for reliable rendering. */
object EazWearColors {
    val Orange = Color(0xFFF97316)
    val OrangeDark = Color(0xFFEA580C)
    val Purple = Color(0xFFA78BFA)
    val Background = Color(0xFFFAFAFA)
    val Panel = Color(0xFFFFFFFF)
    val PanelBorder = Color(0xFFE2E8F0)
    val TextPrimary = Color(0xFF0F172A)
    val TextMuted = Color(0xFF64748B)
    val TextSubtle = Color(0xFF475569)
    val AuthCard = Color(0xFFFFFFFF)
    val AuthCardMuted = Color(0xFF475569)
}

private val WearColorScheme = lightColorScheme(
    primary = EazWearColors.Orange,
    onPrimary = Color.White,
    secondary = EazWearColors.OrangeDark,
    onSecondary = Color.White,
    background = EazWearColors.Background,
    surface = EazWearColors.Panel,
    surfaceVariant = Color(0xFFF1F5F9),
    onBackground = EazWearColors.TextPrimary,
    onSurface = EazWearColors.TextPrimary,
    onSurfaceVariant = EazWearColors.TextMuted,
)

@Composable
fun EazWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = WearColorScheme, content = content)
}

@Composable
fun EazWearScreenBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        content()
    }
}
