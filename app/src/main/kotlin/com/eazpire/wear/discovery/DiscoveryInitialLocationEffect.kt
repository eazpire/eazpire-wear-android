package com.eazpire.wear.discovery

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

@Composable
fun DiscoveryInitialLocationEffect(enabled: Boolean) {
    val context = LocalContext.current
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        if (DiscoveryExploreState.currentLocation.value != null) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return@LaunchedEffect
        val client = LocationServices.getFusedLocationProviderClient(context)
        runCatching {
            client.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                CancellationTokenSource().token,
            ).addOnSuccessListener { loc ->
                if (loc != null && DiscoveryExploreState.currentLocation.value == null) {
                    DiscoveryExploreState.addLocation(
                        loc.latitude,
                        loc.longitude,
                        if (loc.hasAltitude()) loc.altitude else null,
                    )
                }
            }
        }
    }
}
