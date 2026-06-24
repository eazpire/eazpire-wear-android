package com.eazpire.wear.discovery

import android.Manifest
import android.content.pm.PackageManager
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Fast GPS updates while AR is open so distance and altitude refresh as the user moves.
 */
@Composable
fun rememberArLiveLocation(seed: GeoPoint?): GeoPoint? {
    val context = LocalContext.current
    var location by remember(seed) { mutableStateOf(seed) }

    DisposableEffect(context, seed) {
        if (seed != null) {
            location = seed
        }

        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            onDispose { }
        } else {
            val fusedClient: FusedLocationProviderClient =
                LocationServices.getFusedLocationProviderClient(context)
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000L)
                .setMinUpdateIntervalMillis(1_000L)
                .setMinUpdateDistanceMeters(1f)
                .build()

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc = result.lastLocation ?: return
                    location = GeoPoint(
                        lat = loc.latitude,
                        lng = loc.longitude,
                        altitudeM = if (loc.hasAltitude()) loc.altitude else null,
                    )
                }
            }

            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
            onDispose { fusedClient.removeLocationUpdates(callback) }
        }
    }

    return location
}
