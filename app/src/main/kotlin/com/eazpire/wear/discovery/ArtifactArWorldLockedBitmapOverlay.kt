package com.eazpire.wear.discovery

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * World-locked drawing preview via screen projection (no Filament [ImageNode]).
 * Avoids plane-renderer / transparent-texture crashes when placing canvas art.
 */
@Composable
fun ArtifactArWorldLockedBitmapOverlay(
    bitmap: Bitmap,
    centerXPx: Float,
    centerYPx: Float,
    size: Dp,
    modifier: Modifier = Modifier,
    zIndex: Float = 7f,
) {
    val density = LocalDensity.current
    val half = size / 2
    val xDp = with(density) { centerXPx.toDp() } - half
    val yDp = with(density) { centerYPx.toDp() } - half

    Box(modifier = modifier.fillMaxSize().zIndex(zIndex)) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .offset(x = xDp, y = yDp)
                .size(size),
        )
    }
}

/** Approximate on-screen size for a square world-locked canvas drawing. */
fun canvasDrawingOverlaySizeDp(placementScale: Float): Dp =
    (180f * placementScale.coerceIn(0.3f, 3f)).dp
