package com.eazpire.wear.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eazpire.wear.R
import com.eazpire.wear.theme.EazWearColors
import kotlin.math.abs
import kotlin.math.roundToInt

private const val SAME_LEVEL_THRESHOLD_M = 3.0

@Composable
fun ArtifactArNavigationOverlay(
    userLocation: GeoPoint?,
    artifactLocation: GeoPoint?,
    modifier: Modifier = Modifier,
) {
    val liveUser = rememberArLiveLocation(userLocation)
    val deviceAzimuth = rememberDeviceAzimuth()

    if (liveUser == null || artifactLocation == null) return

    val distanceM = distanceMeters(liveUser, artifactLocation).roundToInt().coerceAtLeast(0)
    val bearing = bearingDegrees(liveUser, artifactLocation)
    val relativeBearing = deviceAzimuth?.let { normalizeAngleDegrees(bearing - it) }

    val verticalLabel = resolveVerticalLabel(liveUser, artifactLocation)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (relativeBearing != null) {
            Icon(
                imageVector = Icons.Filled.ArrowUpward,
                contentDescription = stringResource(R.string.artifact_ar_direction_cd),
                tint = EazWearColors.HubOrange,
                modifier = Modifier
                    .size(56.dp)
                    .rotate(relativeBearing),
            )
        } else {
            Text(
                text = stringResource(R.string.artifact_ar_direction_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = EazWearColors.HubText,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(R.string.artifact_ar_distance, distanceM),
            style = MaterialTheme.typography.titleMedium,
            color = EazWearColors.HubText,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = verticalLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = EazWearColors.HubText,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun resolveVerticalLabel(user: GeoPoint, artifact: GeoPoint): String {
    val userAlt = user.altitudeM
    val artifactAlt = artifact.altitudeM
    if (userAlt == null || artifactAlt == null) {
        return stringResource(R.string.artifact_ar_level_approximate)
    }

    val delta = artifactAlt - userAlt
    return when {
        abs(delta) < SAME_LEVEL_THRESHOLD_M ->
            stringResource(R.string.artifact_ar_same_level)
        delta > 0 ->
            stringResource(R.string.artifact_ar_above, delta.roundToInt().coerceAtLeast(1))
        else ->
            stringResource(R.string.artifact_ar_below, abs(delta).roundToInt().coerceAtLeast(1))
    }
}
