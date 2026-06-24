package com.eazpire.wear.discovery

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.eazpire.wear.R
import com.eazpire.wear.theme.EazWearColors
import com.eazpire.wear.core.model.MoveToEarnWallet

@Composable
fun MoveToEarnWalletCard(
    wallet: MoveToEarnWallet?,
    converting: Boolean,
    convertMessage: String?,
    onConvert: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (wallet == null) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(EazWearColors.HubPanel.copy(alpha = 0.92f))
            .border(1.dp, EazWearColors.HubCardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(R.string.m2e_wallet_title),
            color = EazWearColors.HubText,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(
                R.string.m2e_wallet_available,
                wallet.balanceEazcAvailable,
            ),
            color = EazWearColors.HubText,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (wallet.balanceEazcLocked > 0) {
            Text(
                stringResource(R.string.m2e_wallet_locked, wallet.balanceEazcLocked),
                color = EazWearColors.HubMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            stringResource(R.string.m2e_wallet_convert_hint),
            color = EazWearColors.HubMuted,
            style = MaterialTheme.typography.labelSmall,
        )
        Button(
            onClick = onConvert,
            enabled = !converting && wallet.balanceEazcAvailable >= wallet.minConvertEaz,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = EazWearColors.HubOrange,
                contentColor = EazWearColors.HubText,
            ),
        ) {
            Text(
                if (converting) {
                    stringResource(R.string.m2e_wallet_converting)
                } else {
                    stringResource(R.string.m2e_wallet_convert)
                },
            )
        }
        convertMessage?.let {
            Text(it, color = EazWearColors.HubMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun DiscoveryExploreStatsBar(
    steps: String,
    distanceKm: String,
    eazToday: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(EazWearColors.HubPanel.copy(alpha = 0.92f))
            .border(1.dp, EazWearColors.HubCardBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatColumn(stringResource(R.string.move_stat_steps), steps)
        StatColumn(stringResource(R.string.move_stat_distance), distanceKm)
        StatColumn(stringResource(R.string.move_stat_eaz), eazToday)
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            color = EazWearColors.HubText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            label,
            color = EazWearColors.HubMuted,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
fun DiscoveryExploreControlsRow(
    exploring: Boolean,
    paused: Boolean,
    onExploreChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var pendingStart by remember { mutableStateOf(false) }
    var showConsent by remember {
        mutableStateOf(!DiscoveryConsentStore.hasConsent(context))
    }
    var locationDenied by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val ok = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (ok && pendingStart) {
            DiscoveryExploreService.start(context)
            onExploreChange(true)
        } else if (pendingStart) {
            locationDenied = true
        }
        pendingStart = false
    }

    fun startExplore() {
        locationDenied = false
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (fine) {
            DiscoveryExploreService.start(context)
            onExploreChange(true)
        } else {
            pendingStart = true
            launcher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACTIVITY_RECOGNITION,
                ),
            )
        }
    }

    if (showConsent) {
        DiscoveryConsentScreen(
            onAccept = {
                DiscoveryConsentStore.setConsent(context, true)
                showConsent = false
                startExplore()
            },
            onDecline = {
                DiscoveryConsentStore.setConsent(context, false)
                showConsent = false
            },
        )
        return
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!exploring) {
                Button(
                    onClick = { startExplore() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EazWearColors.HubOrange,
                        contentColor = EazWearColors.HubText,
                    ),
                ) {
                    Text(stringResource(R.string.discovery_start))
                }
            } else {
                OutlinedButton(
                    onClick = {
                        if (paused) {
                            DiscoveryExploreService.resume(context)
                        } else {
                            DiscoveryExploreService.pause(context)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = EazWearColors.HubText,
                    ),
                ) {
                    Text(
                        if (paused) {
                            stringResource(R.string.discovery_resume)
                        } else {
                            stringResource(R.string.discovery_pause)
                        },
                    )
                }
                Button(
                    onClick = {
                        DiscoveryExploreService.stop(context)
                        onExploreChange(false)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EazWearColors.HubButtonCharcoal,
                        contentColor = EazWearColors.HubText,
                    ),
                ) {
                    Text(stringResource(R.string.discovery_stop))
                }
            }
        }
        if (locationDenied) {
            Text(
                stringResource(R.string.discovery_location_required),
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (exploring && paused) {
            Text(
                stringResource(R.string.discovery_paused_hint),
                color = EazWearColors.HubMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
