package com.eazpire.wear.discovery

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.eazpire.wear.R
import com.eazpire.wear.theme.EazWearColors

/**
 * Camera-fixed hand aim reticle. Stays centered in the view (not world-locked).
 * Tap the hand or anywhere on the camera feed to place at the raycast hit point.
 */
@Composable
fun ArtifactArPlacementHandOverlay(
    hasValidSurface: Boolean,
    onPlaceTap: (screenX: Float, screenY: Float) -> Unit,
    modifier: Modifier = Modifier,
    handOffsetY: androidx.compose.ui.unit.Dp = 20.dp,
) {
    val contentDescription = stringResource(R.string.artifact_ar_hand_cd)
    val infiniteTransition = rememberInfiniteTransition(label = "ar-hand-pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (hasValidSurface) 1.18f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ar-hand-scale",
    )

    val iconAlpha = if (hasValidSurface) 1f else 0.45f
    val iconTint = if (hasValidSurface) EazWearColors.HubOrange else Color.White.copy(alpha = 0.7f)

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val aimScreenX = with(density) { configuration.screenWidthDp.dp.toPx() } * ARTIFACT_AR_HIT_TEST_X
    val aimScreenY = with(density) { configuration.screenHeightDp.dp.toPx() } * ARTIFACT_AR_HIT_TEST_Y
    val handInteraction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .zIndex(2f)
            .pointerInput(hasValidSurface) {
                detectTapGestures { offset ->
                    if (hasValidSurface) {
                        onPlaceTap(offset.x, offset.y)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .offset(y = handOffsetY)
                .size(96.dp)
                .clickable(
                    interactionSource = handInteraction,
                    indication = null,
                    enabled = hasValidSurface,
                    onClick = { onPlaceTap(aimScreenX, aimScreenY) },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PanTool,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier
                    .semantics { this.contentDescription = contentDescription }
                    .scale(pulseScale)
                    .alpha(iconAlpha)
                    .size(72.dp),
            )
        }
    }
}
