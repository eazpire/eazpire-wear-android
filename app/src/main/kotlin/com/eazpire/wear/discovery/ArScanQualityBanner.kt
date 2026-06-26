package com.eazpire.wear.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eazpire.wear.R
import com.eazpire.wear.theme.EazWearColors

@Composable
fun ArScanQualityBanner(
    quality: ArPlacementQuality,
    vpsAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    val (messageRes, tint) = when (quality) {
        ArPlacementQuality.Poor -> R.string.ar_scan_quality_poor to EazWearColors.HubMuted
        ArPlacementQuality.Medium -> R.string.ar_scan_quality_medium to EazWearColors.HubText
        ArPlacementQuality.Good -> R.string.ar_scan_quality_good to EazWearColors.HubOrange
        ArPlacementQuality.Excellent -> R.string.ar_scan_quality_excellent to EazWearColors.HubOrange
    }
    val vpsHint = if (!vpsAvailable) {
        stringResource(R.string.ar_scan_vps_unavailable)
    } else {
        null
    }
    Text(
        text = buildString {
            append(stringResource(messageRes))
            vpsHint?.let { append("\n").append(it) }
        },
        style = MaterialTheme.typography.bodySmall,
        color = tint,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(EazWearColors.HubPanel.copy(alpha = 0.88f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
