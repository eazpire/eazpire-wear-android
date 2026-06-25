package com.eazpire.wear.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawBehind

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

    /** Dark hub palette — wear-web community.css / Move to Earn aesthetic (auth only). */
    val HubBg = Color(0xFF07080D)
    val HubBgGradientStart = Color(0xFF05060A)
    val HubBgGradientMid = Color(0xFF0B0D16)
    val HubBgGradientEnd = Color(0xFF090B12)
    val HubPanel = Color(0xFF111421)
    val HubPanel2 = Color(0xFF181C2D)
    val HubCyan = Color(0xFF5EEAD4)
    val HubOrange = Color(0xFFFF7A1A)
    val HubOrangeCopper = Color(0xFFFFB067)
    val HubText = Color(0xFFF8FAFC)
    val HubMuted = Color(0xFFA7ADBD)
    val HubSoft = Color(0xFF697083)
    val HubStroke = Color(0x1FFFFFFF)
    val HubStroke2 = Color(0x38FFFFFF)
    val HubCardBorder = Color(0x47FF7A1A)
    val HubButtonCharcoal = Color(0xFF1A1D2B)
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
            .systemBarsPadding()
            .background(MaterialTheme.colorScheme.background),
    ) {
        content()
    }
}

/** Dark gradient + faint grid — matches wear-web community.css hub shell. */
@Composable
fun EazWearAuthBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            EazWearColors.HubBgGradientStart,
                            EazWearColors.HubBgGradientMid,
                            EazWearColors.HubBgGradientEnd,
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height),
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x52FF7A1A), Color.Transparent),
                        center = Offset(size.width * 0.12f, 0f),
                        radius = size.width * 0.55f,
                    ),
                    radius = size.width * 0.55f,
                    center = Offset(size.width * 0.12f, 0f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x3DEF1F27), Color.Transparent),
                        center = Offset(size.width * 0.82f, size.height * 0.03f),
                        radius = size.width * 0.45f,
                    ),
                    radius = size.width * 0.45f,
                    center = Offset(size.width * 0.82f, size.height * 0.03f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x215EEAD4), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height),
                        radius = size.width * 0.6f,
                    ),
                    radius = size.width * 0.6f,
                    center = Offset(size.width * 0.5f, size.height),
                )
                val gridStep = 58.dp.toPx()
                val gridColorH = Color(0x17FF7A1A)
                val gridColorV = Color(0x0BFFFFFF)
                var x = 0f
                while (x <= size.width) {
                    drawLine(gridColorV, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                    x += gridStep
                }
                var y = 0f
                while (y <= size.height) {
                    drawLine(gridColorH, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    y += gridStep
                }
            },
    ) {
        content()
    }
}

/** Dark hub shell for logged-in tabs — same gradient as auth / wear-web. */
@Composable
fun EazWearHubBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize().systemBarsPadding()) {
        EazWearAuthBackground(modifier = Modifier.fillMaxSize(), content = content)
    }
}
