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
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
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
    val previewSizePx = with(density) { previewSizeDp.roundToPx() }
    val previewHalfPx = previewSizePx / 2
    val mapReferenceZoom = 16.0
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
    var glbPreviewHost by remember { mutableStateOf<MapGlbPreviewHost?>(null) }
    var trackOverlay by remember { mutableStateOf<Polyline?>(null) }
    var userMarkerOverlay by remember { mutableStateOf<Marker?>(null) }
    var artifactMarkerOverlay by remember { mutableStateOf<Marker?>(null) }
    val showGlbPreview = !artifactModelUrl.isNullOrBlank()

    val latestArtifact by rememberUpdatedState(artifactLocation)
    val latestShowGlbPreview by rememberUpdatedState(showGlbPreview)
    val latestInRange by rememberUpdatedState(artifactInRange)
    val latestOnArtifactClick by rememberUpdatedState(onArtifactClick)
    val latestOnOutOfRangeClick by rememberUpdatedState(onArtifactOutOfRangeClick)

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
        if (artifact != null && !showGlbPreview) {
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

    LaunchedEffect(mapView) {
        val map = mapView ?: return@LaunchedEffect
        while (true) {
            when (val reading = latestCompassReading) {
                null -> {
                    orientationFilter.reset()
                    if (map.mapOrientation != 0f) {
                        map.mapOrientation = 0f
                        map.invalidate()
                    }
                }
                else -> if (reading.isReliable) {
                    val orientation = orientationFilter.filter(
                        azimuthToMapOrientation(reading.azimuthDegrees),
                    )
                    if (map.mapOrientation != orientation) {
                        map.mapOrientation = orientation
                        map.invalidate()
                    }
                }
                // Unreliable tilt: keep last map orientation (Google Maps style).
            }
            kotlinx.coroutines.delay(50L)
        }
    }

    LaunchedEffect(glbPreviewHost, showGlbPreview, artifactModelUrl, artifactInRange) {
        val host = glbPreviewHost ?: return@LaunchedEffect
        val modelUrl = artifactModelUrl
        if (!showGlbPreview || modelUrl.isNullOrBlank()) {
            host.previewView.visibility = View.GONE
            host.mapView.invalidate()
            return@LaunchedEffect
        }
        host.previewView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
        )
        host.previewView.setContent {
            ArtifactMapModelPreview(
                modelUrl = modelUrl,
                inRange = artifactInRange,
                onClick = onArtifactClick,
                onOutOfRangeClick = onArtifactOutOfRangeClick,
                size = previewSizeDp,
            )
        }
        host.mapView.invalidate()
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    val map = MapView(ctx).apply {
                        setBackgroundColor(Color.parseColor("#E8EEF2"))
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(mapReferenceZoom)
                    }
                    addView(
                        map,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        ),
                    )

                    val composeView = ComposeView(ctx).apply {
                        visibility = View.GONE
                        layoutParams = FrameLayout.LayoutParams(previewSizePx, previewSizePx)
                        isClickable = false
                        isFocusable = false
                        setOnTouchListener { _, event ->
                            map.dispatchTouchEvent(event)
                            true
                        }
                    }
                    addView(composeView)

                    val overlay = ArtifactGlbMapOverlay(
                        previewView = composeView,
                        baseHalfSizePx = previewHalfPx,
                        referenceZoom = mapReferenceZoom,
                        geoPointProvider = { latestArtifact },
                        enabledProvider = { latestShowGlbPreview },
                        inRangeProvider = { latestInRange },
                        onInRangeClick = { latestOnArtifactClick() },
                        onOutOfRangeClick = { latestOnOutOfRangeClick() },
                    )
                    map.overlays.add(overlay)

                    mapView = map
                    glbPreviewHost = MapGlbPreviewHost(map, composeView, overlay)
                }
            },
            update = { _ ->
                val loc = currentLocation
                val map = mapView
                if (loc != null && trackPoints.isEmpty() && map != null) {
                    map.controller.setCenter(OsmGeoPoint(loc.lat, loc.lng))
                }
            },
        )
    }
}
