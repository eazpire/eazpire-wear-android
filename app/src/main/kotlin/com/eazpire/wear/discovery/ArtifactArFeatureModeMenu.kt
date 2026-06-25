package com.eazpire.wear.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eazpire.wear.R
import com.eazpire.wear.theme.EazWearColors

@Composable
fun ArtifactArFeatureModeMenu(
    selectedMode: ArtifactArFeatureMode,
    onModeSelected: (ArtifactArFeatureMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(EazWearColors.HubPanel.copy(alpha = 0.92f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.artifact_ar_mode_menu_title),
            style = MaterialTheme.typography.labelSmall,
            color = EazWearColors.HubMuted,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModeIconButton(
                selected = selectedMode == ArtifactArFeatureMode.Viewer,
                label = stringResource(R.string.artifact_ar_mode_viewer),
                onClick = { onModeSelected(ArtifactArFeatureMode.Viewer) },
            ) {
                Icon(
                    Icons.Outlined.Visibility,
                    contentDescription = null,
                    tint = if (selectedMode == ArtifactArFeatureMode.Viewer) {
                        EazWearColors.HubOrange
                    } else {
                        EazWearColors.HubText
                    },
                )
            }
            ModeIconButton(
                selected = selectedMode == ArtifactArFeatureMode.Hand,
                label = stringResource(R.string.artifact_ar_mode_hand),
                onClick = { onModeSelected(ArtifactArFeatureMode.Hand) },
            ) {
                Icon(
                    Icons.Filled.PanTool,
                    contentDescription = null,
                    tint = if (selectedMode == ArtifactArFeatureMode.Hand) {
                        EazWearColors.HubOrange
                    } else {
                        EazWearColors.HubText
                    },
                )
            }
            ModeIconButton(
                selected = selectedMode == ArtifactArFeatureMode.Canvas,
                label = stringResource(R.string.artifact_ar_mode_canvas),
                onClick = { onModeSelected(ArtifactArFeatureMode.Canvas) },
            ) {
                Icon(
                    Icons.Filled.Brush,
                    contentDescription = null,
                    tint = if (selectedMode == ArtifactArFeatureMode.Canvas) {
                        EazWearColors.HubOrange
                    } else {
                        EazWearColors.HubText
                    },
                )
            }
        }
    }
}

@Composable
private fun ModeIconButton(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .semantics { contentDescription = label }
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .background(
                if (selected) EazWearColors.HubButtonCharcoal else EazWearColors.HubPanel.copy(alpha = 0.5f),
            )
            .padding(8.dp),
    ) {
        BoxIcon(icon)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) EazWearColors.HubOrange else EazWearColors.HubMuted,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun BoxIcon(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
        content()
    }
}
