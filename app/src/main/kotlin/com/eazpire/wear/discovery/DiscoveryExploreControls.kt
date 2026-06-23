package com.eazpire.wear.discovery

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.eazpire.wear.R

@Composable
fun DiscoveryExploreControls(
    exploring: Boolean,
    onExploreChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var pendingStart by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val ok = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (ok && pendingStart) {
            DiscoveryExploreService.start(context)
            onExploreChange(true)
        }
        pendingStart = false
    }

    fun toggle() {
        if (exploring) {
            DiscoveryExploreService.stop(context)
            onExploreChange(false)
            return
        }
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
                ),
            )
        }
    }

    Column {
        Button(onClick = { toggle() }, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (exploring) {
                    stringResource(R.string.discovery_stop)
                } else {
                    stringResource(R.string.discovery_start)
                },
            )
        }
    }
}
