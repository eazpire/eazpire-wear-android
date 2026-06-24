package com.eazpire.wear.discovery

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import android.graphics.Color
import android.graphics.Point
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.roundToInt
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
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
    artifactModelUrl: String? = null,
    artifactInRange: Boolean = false,
    artifactName: String = "",
    onArtifactClick: () -> Unit = {},
    onArtifactOutOfRangeClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val previewSizeDp = 56.dp
    val previewHalfPx = with(density) { (previewSizeDp / 2).roundToPx() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val compassReading = rememberDeviceCompassReading()
    val orientationFilter = remember { SmoothAzimuthFilter() }
    val latestCompassReading by rememberUpdatedState(compassReading)

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
    var artifactMarkerScreenPx by remember { mutableStateOf<Offset?>(null) }
    val showGlbPreview = !artifactModelUrl.isNullOrBlank()

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

    val latestArtifact by rememberUpdatedState(artifactLocation)
    val latestShowGlbPreview by rememberUpdatedState(showGlbPreview)

    fun updateArtifactScreenPosition(map: MapView) {
        val artifact = latestArtifact
        if (!latestShowGlbPreview || artifact == null) {
            artifactMarkerScreenPx = null
            return
        }
        val point = Point()
        map.projection.toPixels(OsmGeoPoint(artifact.lat, artifact.lng), point)
        artifactMarkerScreenPx = Offset(point.x.toFloat(), point.y.toFloat())
    }

    DisposableEffect(mapView, showGlbPreview) {
        val map = mapView
        if (map == null) return@DisposableEffect onDispose {}

        val mapListener = object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                updateArtifactScreenPosition(map)
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                updateArtifactScreenPosition(map)
                return false
            }
        }
        map.addMapListener(mapListener)

        val layoutListener =
            android.view.View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                updateArtifactScreenPosition(map)
            }
        map.addOnLayoutChangeListener(layoutListener)
        updateArtifactScreenPosition(map)

        onDispose {
            map.removeMapListener(mapListener)
            map.removeOnLayoutChangeListener(layoutListener)
        }
    }

    LaunchedEffect(currentLocation, trackPoints, artifactLocation, artifactInRange, artifactName, showGlbPreview) {
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
                icon = if (showGlbPreview) {
                    createInvisibleMarkerDrawable(context)
                } else {
                    createArtifactMarkerDrawable(context, artifactInRange)
                }
                isDraggable = false
                if (!showGlbPreview) {
                    setOnMarkerClickListener { _, _ ->
                        if (artifactInRange) {
                            onArtifactClick()
                        } else {
                            onArtifactOutOfRangeClick()
                        }
                        true
                    }
                }
            }
            artifactMarkerOverlay = marker
            map.overlays.add(marker)
        } else {
            artifactMarkerOverlay = null
        }

        map.invalidate()
        updateArtifactScreenPosition(map)
    }

    LaunchedEffect(mapView) {
        val map = mapView ?: return@LaunchedEffect
        while (true) {
            when (val reading = latestCompassReading) {
                null -> {
                    orientationFilter.reset()
                    if (map.mapOrientation != 0f) {
                        map.mapOrientation = 0f
                        updateArtifactScreenPosition(map)
                    }
                }
                else -> if (reading.isReliable) {
                    val orientation = orientationFilter.filter(
                        azimuthToMapOrientation(reading.azimuthDegrees),
                    )
                    if (map.mapOrientation != orientation) {
                        map.mapOrientation = orientation
                        updateArtifactScreenPosition(map)
                    }
                }
                // Unreliable tilt: keep last map orientation (Google Maps style).
            }
            if (latestShowGlbPreview) {
                updateArtifactScreenPosition(map)
            }
            kotlinx.coroutines.delay(50L)
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setBackgroundColor(Color.parseColor("#E8EEF2"))
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

        val markerScreen = artifactMarkerScreenPx
        if (showGlbPreview && markerScreen != null) {
            ArtifactMapModelPreview(
                modelUrl = artifactModelUrl!!,
                inRange = artifactInRange,
                onClick = onArtifactClick,
                onOutOfRangeClick = onArtifactOutOfRangeClick,
                modifier = Modifier.offset {
                    IntOffset(
                        (markerScreen.x - previewHalfPx).roundToInt(),
                        (markerScreen.y - previewHalfPx).roundToInt(),
                    )
                },
                size = previewSizeDp,
            )
        }
    }
}
