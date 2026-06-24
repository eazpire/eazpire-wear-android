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
    val previewSizePx = with(density) { previewSizeDp.roundToPx() }
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

    fun syncGlbPreviewLayout() {
        val host = glbPreviewHost ?: return
        val map = host.mapView
        val geo = latestArtifact
        if (!latestShowGlbPreview || geo == null) {
            host.previewView.visibility = View.GONE
            host.tapMarker.isEnabled = false
            map.invalidate()
            return
        }
        val sizePx = scaledGlbPreviewSizePx(previewSizePx, map.zoomLevelDouble, mapReferenceZoom)
        map.layoutGlbPreviewAt(host.previewView, geo, sizePx)
        map.syncGlbTapMarker(host.tapMarker, geo, sizePx, context)
        host.tapMarker.isEnabled = true
        map.overlays.remove(host.tapOverlay)
        map.overlays.add(host.tapOverlay)
        host.previewView.visibility = View.VISIBLE
        map.invalidate()
    }

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

        glbPreviewHost?.let { host ->
            if (showGlbPreview) {
                if (!map.overlays.contains(host.tapOverlay)) {
                    map.overlays.add(host.tapOverlay)
                }
                if (!map.overlays.contains(host.tapMarker)) {
                    map.overlays.add(host.tapMarker)
                }
                map.bringGlbTapMarkerToFront(host.tapMarker)
            } else {
                map.overlays.remove(host.tapOverlay)
                map.overlays.remove(host.tapMarker)
            }
        }

        syncGlbPreviewLayout()
    }

    LaunchedEffect(mapView) {
        val map = mapView ?: return@LaunchedEffect
        while (true) {
            when (val reading = latestCompassReading) {
                null -> {
                    orientationFilter.reset()
                    if (map.mapOrientation != 0f) {
                        map.mapOrientation = 0f
                        glbPreviewHost?.previewView?.rotation = 0f
                        map.invalidate()
                    }
                }
                else -> if (reading.isReliable) {
                    val orientation = orientationFilter.filter(
                        azimuthToMapOrientation(reading.azimuthDegrees),
                    )
                    if (map.mapOrientation != orientation) {
                        map.mapOrientation = orientation
                        glbPreviewHost?.previewView?.rotation = orientation
                        map.invalidate()
                    }
                }
                // Unreliable tilt: keep last map orientation (Google Maps style).
            }
            kotlinx.coroutines.delay(50L)
        }
    }

    LaunchedEffect(glbPreviewHost, showGlbPreview, artifactModelUrl, artifactInRange, artifactLocation) {
        val host = glbPreviewHost ?: return@LaunchedEffect
        val modelUrl = artifactModelUrl
        if (!showGlbPreview || modelUrl.isNullOrBlank()) {
            host.previewView.visibility = View.GONE
            host.tapMarker.isEnabled = false
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
        syncGlbPreviewLayout()
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
                        addMapListener(
                            object : MapListener {
                                override fun onScroll(event: ScrollEvent?): Boolean {
                                    glbPreviewHost?.previewView?.rotation = mapOrientation
                                    invalidate()
                                    return false
                                }

                                override fun onZoom(event: ZoomEvent?): Boolean {
                                    glbPreviewHost?.let { host ->
                                        val geo = latestArtifact
                                        if (latestShowGlbPreview && geo != null) {
                                            val sizePx = scaledGlbPreviewSizePx(
                                                previewSizePx,
                                                zoomLevelDouble,
                                                mapReferenceZoom,
                                            )
                                            layoutGlbPreviewAt(host.previewView, geo, sizePx)
                                            syncGlbTapMarker(host.tapMarker, geo, sizePx, ctx)
                                        }
                                    }
                                    invalidate()
                                    return false
                                }
                            },
                        )
                    }
                    addView(
                        map,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        ),
                    )

                    val tapOverlay = ArtifactGlbTapOverlay(
                        geoPointProvider = { latestArtifact },
                        enabledProvider = { latestShowGlbPreview },
                        sizePxProvider = {
                            scaledGlbPreviewSizePx(
                                previewSizePx,
                                map.zoomLevelDouble,
                                mapReferenceZoom,
                            )
                        },
                        inRangeProvider = { latestInRange },
                        onInRangeClick = { latestOnArtifactClick() },
                        onOutOfRangeClick = { latestOnOutOfRangeClick() },
                    )
                    val composeView = ComposeView(ctx).apply {
                        visibility = View.GONE
                        isClickable = false
                        isFocusable = false
                        // Preview sits above tiles; forward so overlay tap + marker click run on MapView.
                        setOnTouchListener { _, event ->
                            map.dispatchTouchEvent(event)
                            true
                        }
                    }
                    val tapMarker = Marker(map).apply {
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        isDraggable = false
                        isEnabled = false
                        setOnMarkerClickListener { _, _ ->
                            if (latestInRange) {
                                latestOnArtifactClick()
                            } else {
                                latestOnOutOfRangeClick()
                            }
                            true
                        }
                    }
                    // Child of MapView (not FrameLayout sibling) — osmdroid re-layouts on pan/zoom.
                    map.addView(
                        composeView,
                        MapView.LayoutParams(
                            previewSizePx,
                            previewSizePx,
                            OsmGeoPoint(0.0, 0.0),
                            MapView.LayoutParams.CENTER,
                            0,
                            0,
                        ),
                    )

                    mapView = map
                    glbPreviewHost = MapGlbPreviewHost(map, composeView, tapMarker, tapOverlay)
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
