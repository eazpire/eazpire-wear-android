package com.eazpire.wear.discovery

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Legacy wrapper — use [DiscoveryExploreControlsRow] in map overlay. */
@Composable
fun DiscoveryExploreControls(
    exploring: Boolean,
    onExploreChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    DiscoveryExploreControlsRow(
        exploring = exploring,
        paused = false,
        onExploreChange = onExploreChange,
        modifier = modifier,
    )
}
