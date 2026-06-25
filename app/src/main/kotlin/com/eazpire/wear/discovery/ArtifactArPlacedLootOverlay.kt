package com.eazpire.wear.discovery

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.eazpire.wear.theme.EazWearColors
import io.github.sceneview.math.Rotation

/**
 * Camera-fixed loot card — shown when the 3D instance failed to load but placement succeeded.
 * Keeps feedback visible while the world anchor remains active for GPS/world-lock semantics.
 */
@Composable
fun ArtifactArPlacedBitmapOverlay(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(8f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(240.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(EazWearColors.HubPanel.copy(alpha = 0.92f))
                .border(2.dp, EazWearColors.HubOrange, RoundedCornerShape(16.dp))
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** AR anchor already faces the camera — map-only X tilt would hide the mesh edge-on. */
fun artifactArModelRotation(modelRotationY: Float): Rotation =
    Rotation(y = modelRotationY)
