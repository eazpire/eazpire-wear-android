package com.eazpire.wear.discovery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

@Composable
fun DiscoveryExploreMapView(
    currentLocation: GeoPoint?,
    trackPoints: List<GeoPoint>,
    artifactLocation: GeoPoint? = null,
    artifactInRange: Boolean = false,
    artifactName: String = "",
    onArtifactClick: () -> Unit = {},
    onArtifactOutOfRangeClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
        val base = File(context.cacheDir, "osmdroid")
        Configuration.getInstance().osmdroidBasePath = base
        Configuration.getInstance().osmdroidTileCache = File(base, "tiles")
    }

    var mapView by remember { mutableStateOf<MapView?>(null) }
    var trackOverlay by remember { mutableStateOf<Polyline?>(null) }
    var userMarkerOverlay by remember { mutableStateOf<Marker?>(null) }
    var artifactMarkerOverlay by remember { mutableStateOf<Marker?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView?.onDetach()
        }
    }

    LaunchedEffect(currentLocation, trackPoints, artifactLocation, artifactInRange, artifactName) {
        val map = mapView ?: return@LaunchedEffect
        val osmTrack = trackPoints.map { OsmGeoPoint(it.lat, it.lng) }

        trackOverlay?.let { map.overlays.remove(it) }
        if (osmTrack.size >= 2) {
            val polyline = Polyline(map).apply {
                setPoints(osmTrack)
                outlinePaint.color = 0xFFFF7A1A.toInt()
                outlinePaint.strokeWidth = 8f
            }
            trackOverlay = polyline
            map.overlays.add(0, polyline)
        } else {
            trackOverlay = null
        }

        userMarkerOverlay?.let { map.overlays.remove(it) }
        val loc = currentLocation
        if (loc != null) {
            val marker = Marker(map).apply {
                position = OsmGeoPoint(loc.lat, loc.lng)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = ""
                isDraggable = false
            }
            userMarkerOverlay = marker
            map.overlays.add(marker)
            map.controller.animateTo(OsmGeoPoint(loc.lat, loc.lng))
        } else {
            userMarkerOverlay = null
        }

        artifactMarkerOverlay?.let { map.overlays.remove(it) }
        val artifact = artifactLocation
        if (artifact != null) {
            val marker = Marker(map).apply {
                position = OsmGeoPoint(artifact.lat, artifact.lng)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = artifactName
                snippet = if (artifactInRange) "" else "too_far"
                icon = createArtifactMarkerDrawable(context, artifactInRange)
                isDraggable = false
                setOnMarkerClickListener { _, _ ->
                    if (artifactInRange) {
                        onArtifactClick()
                    } else {
                        onArtifactOutOfRangeClick()
                    }
                    true
                }
            }
            artifactMarkerOverlay = marker
            map.overlays.add(marker)
        } else {
            artifactMarkerOverlay = null
        }

        map.invalidate()
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(16.0)
                mapView = this
            }
        },
        update = { map ->
            mapView = map
            val loc = currentLocation
            if (loc != null && trackPoints.isEmpty()) {
                map.controller.setCenter(OsmGeoPoint(loc.lat, loc.lng))
            }
        },
    )
}
