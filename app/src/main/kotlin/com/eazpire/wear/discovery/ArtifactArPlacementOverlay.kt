package com.eazpire.wear.discovery

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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

    Box(
        modifier = modifier
            .pointerInput(hasValidSurface) {
                detectTapGestures { offset ->
                    if (hasValidSurface) {
                        onPlaceTap(offset.x, offset.y)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.PanTool,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier
                .offset(y = handOffsetY)
                .semantics { this.contentDescription = contentDescription }
                .scale(pulseScale)
                .alpha(iconAlpha)
                .size(72.dp),
        )
    }
}
