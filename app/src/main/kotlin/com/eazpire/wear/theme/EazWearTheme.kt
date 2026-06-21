package com.eazpire.wear.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val WearOrange = Color(0xFFFF7A1A)
private val WearPurple = Color(0xFFA78BFA)
private val WearBg = Color(0xFF07080D)
private val WearPanel = Color(0xFF111421)
private val WearText = Color(0xFFF8FAFC)
private val WearMuted = Color(0xFFA7ADBD)

private val WearColorScheme = darkColorScheme(
    primary = WearOrange,
    secondary = WearPurple,
    background = WearBg,
    surface = WearPanel,
    onPrimary = Color(0xFF1A0A02),
    onSecondary = Color.White,
    onBackground = WearText,
    onSurface = WearText,
    onSurfaceVariant = WearMuted,
)

@Composable
fun EazWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WearColorScheme,
        content = content,
    )
}
