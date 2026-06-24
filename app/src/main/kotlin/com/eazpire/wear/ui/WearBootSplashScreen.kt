package com.eazpire.wear.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazpire.wear.R
import com.eazpire.wear.core.brand.BrandAssetSlots
import com.eazpire.wear.theme.EazWearColors
import kotlinx.coroutines.delay

private const val SEGMENTS = 16
private const val WEAR_LOGO_ASPECT = 1290f / 450f
private const val LOGO_GLOW_MS = 2200

/** Matches wear-web `wear-boot-loader.js` + `community.css` (16-segment bar, logo glow, dark gradient). */
@Composable
fun WearBootSplashScreen(
    targetProgress: Int,
    modifier: Modifier = Modifier,
) {
    var progress by remember { mutableIntStateOf(0) }
    val statusLines = listOf(
        stringResource(R.string.splash_status_loading),
        stringResource(R.string.splash_status_artifacts),
        stringResource(R.string.splash_status_feed),
        stringResource(R.string.splash_status_ready),
    )
    val statusIndex = ((progress.toFloat() / SEGMENTS) * statusLines.size)
        .toInt()
        .coerceIn(0, statusLines.lastIndex)

    val infiniteTransition = rememberInfiniteTransition(label = "wearBootLogo")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.012f,
        animationSpec = infiniteRepeatable(
            animation = tween(LOGO_GLOW_MS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "wearBootLogoScale",
    )
    val glowStrength by infiniteTransition.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.42f,
        animationSpec = infiniteRepeatable(
            animation = tween(LOGO_GLOW_MS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "wearBootLogoGlow",
    )
    val activePulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.72f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "wearBootSegPulse",
    )

    LaunchedEffect(targetProgress) {
        val goal = targetProgress.coerceIn(0, SEGMENTS)
        while (progress < goal) {
            delay(70)
            progress += 1
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF030407),
                            Color(0xFF0A0C14),
                            Color(0xFF07080D),
                        ),
                        startY = 0f,
                        endY = size.height,
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0x29FF7A1A),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.28f),
                        radius = size.width * 0.32f,
                    ),
                    radius = size.width * 0.32f,
                    center = Offset(size.width * 0.5f, size.height * 0.28f),
                )
            },
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WearBrandSlotImage(
                slot = BrandAssetSlots.WEAR_LOGO,
                fallbackResId = R.drawable.eazpire_wear_logo,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                preferLocalDrawable = true,
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .aspectRatio(WEAR_LOGO_ASPECT)
                    .drawBehind {
                        val glowRadius = size.width * 0.55f
                        val glowAlpha = glowStrength * 0.85f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    EazWearColors.HubOrange.copy(alpha = glowAlpha),
                                    Color.Transparent,
                                ),
                                center = Offset(size.width / 2f, size.height / 2f),
                                radius = glowRadius,
                            ),
                            radius = glowRadius,
                            center = Offset(size.width / 2f, size.height / 2f),
                        )
                    }
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                    },
            )
            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0x0AFFFFFF))
                    .border(
                        width = 1.dp,
                        color = EazWearColors.HubOrange.copy(alpha = 0.28f),
                        shape = RoundedCornerShape(999.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    repeat(SEGMENTS) { index ->
                        val lit = index < progress
                        val active = lit && index == (progress - 1).coerceAtLeast(0)
                        BootSegmentBar(
                            index = index,
                            lit = lit,
                            active = active,
                            activePulse = if (active) activePulse else 1f,
                            modifier = Modifier
                                .weight(1f)
                                .height(11.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = statusLines[statusIndex].uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.54.sp,
                color = EazWearColors.HubSoft,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun BootSegmentBar(
    index: Int,
    lit: Boolean,
    active: Boolean,
    activePulse: Float,
    modifier: Modifier = Modifier,
) {
    val fillScale by animateFloatAsState(
        targetValue = if (lit) 1f else 0f,
        animationSpec = tween(
            durationMillis = 280,
            easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f),
        ),
        label = "bootSegFill$index",
    )
    val fillBrush = when {
        index % 3 == 0 -> Brush.horizontalGradient(
            listOf(EazWearColors.HubCyan, EazWearColors.Purple),
        )
        index % 3 == 2 -> Brush.horizontalGradient(
            listOf(EazWearColors.HubOrangeCopper, EazWearColors.HubCyan),
        )
        else -> Brush.horizontalGradient(
            listOf(EazWearColors.HubOrange, EazWearColors.HubOrangeCopper, EazWearColors.HubCyan),
        )
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0x0FFFFFFF))
            .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(999.dp)),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = fillScale
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                    alpha = activePulse
                }
                .clip(RoundedCornerShape(999.dp))
                .background(fillBrush),
        )
    }
}
