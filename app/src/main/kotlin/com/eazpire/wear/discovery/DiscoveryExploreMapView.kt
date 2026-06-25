package com.eazpire.wear.discovery

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import android.content.Context
import android.graphics.Color
import android.view.MotionEvent
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
    mapArtifacts: List<MapArtifactViewState> = emptyList(),
    mapArDrawings: List<MapArDrawingViewState> = emptyList(),
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
    val glbPreviewHosts = remember { mutableStateMapOf<String, MapGlbPreviewHost>() }
    var trackOverlay by remember { mutableStateOf<Polyline?>(null) }
    var userMarkerOverlay by remember { mutableStateOf<Marker?>(null) }
    var fallbackMarkerOverlays by remember { mutableStateOf<Map<String, Marker>>(emptyMap()) }
    var arDrawingMarkerOverlays by remember { mutableStateOf<Map<String, Marker>>(emptyMap()) }

    val latestArtifacts by rememberUpdatedState(mapArtifacts)
    val latestArDrawings by rememberUpdatedState(mapArDrawings)

    fun syncAllGlbPreviewLayouts() {
        val map = mapView ?: return
        latestArtifacts.forEach { artifact ->
            val host = glbPreviewHosts[artifact.id] ?: return@forEach
            if (artifact.modelUrl.isBlank()) {
                host.previewView.visibility = View.GONE
                host.tapMarker.isEnabled = false
                return@forEach
            }
            val sizePx = scaledGlbPreviewSizePx(previewSizePx, map.zoomLevelDouble, mapReferenceZoom)
            map.layoutGlbPreviewAt(host.previewView, artifact.location, sizePx)
            map.syncGlbTapMarker(host.tapMarker, artifact.location, sizePx, context)
            host.tapMarker.isEnabled = true
            map.overlays.remove(host.tapOverlay)
            map.overlays.add(host.tapOverlay)
            host.previewView.visibility = View.VISIBLE
        }
        map.invalidate()
    }

    fun ensureGlbPreviewHost(map: MapView, ctx: Context, artifactId: String): MapGlbPreviewHost {
        glbPreviewHosts[artifactId]?.let { return it }

        val tapOverlay = ArtifactGlbTapOverlay(
            geoPointProvider = {
                latestArtifacts.firstOrNull { it.id == artifactId }?.location
            },
            enabledProvider = {
                latestArtifacts.firstOrNull { it.id == artifactId }?.modelUrl?.isNotBlank() == true
            },
            sizePxProvider = {
                scaledGlbPreviewSizePx(
                    previewSizePx,
                    map.zoomLevelDouble,
                    mapReferenceZoom,
                )
            },
            inRangeProvider = {
                latestArtifacts.firstOrNull { it.id == artifactId }?.inRange == true
            },
            onInRangeClick = {
                latestArtifacts.firstOrNull { it.id == artifactId }?.onClick?.invoke()
            },
            onOutOfRangeClick = {
                latestArtifacts.firstOrNull { it.id == artifactId }?.onOutOfRangeClick?.invoke()
            },
        )
        val composeView = ComposeView(ctx).apply {
            visibility = View.GONE
            isClickable = true
            isFocusable = false
            var downX = 0f
            var downY = 0f
            // Do not forward touches to MapView — that re-dispatches to child views and StackOverflows.
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.x
                        downY = event.y
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val dx = event.x - downX
                        val dy = event.y - downY
                        if (dx * dx + dy * dy <= GLB_PREVIEW_TAP_SLOP_PX * GLB_PREVIEW_TAP_SLOP_PX) {
                            val artifact = latestArtifacts.firstOrNull { it.id == artifactId }
                            if (artifact != null) {
                                if (artifact.inRange) {
                                    artifact.onClick()
                                } else {
                                    artifact.onOutOfRangeClick()
                                }
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> true
                    else -> true
                }
            }
        }
        val tapMarker = Marker(map).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            isDraggable = false
            isEnabled = false
            setOnMarkerClickListener { _, _ ->
                val artifact = latestArtifacts.firstOrNull { it.id == artifactId } ?: return@setOnMarkerClickListener false
                if (artifact.inRange) {
                    artifact.onClick()
                } else {
                    artifact.onOutOfRangeClick()
                }
                true
            }
        }
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
        val host = MapGlbPreviewHost(artifactId, map, composeView, tapMarker, tapOverlay)
        glbPreviewHosts[artifactId] = host
        return host
    }

    fun removeGlbPreviewHost(host: MapGlbPreviewHost) {
        val map = host.mapView
        map.overlays.remove(host.tapOverlay)
        map.overlays.remove(host.tapMarker)
        map.removeView(host.previewView)
        glbPreviewHosts.remove(host.id)
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

    LaunchedEffect(currentLocation, trackPoints, mapArtifacts, mapArDrawings) {
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

        fallbackMarkerOverlays.values.forEach { map.overlays.remove(it) }
        val newFallbackMarkers = mutableMapOf<String, Marker>()
        mapArtifacts.forEach { artifact ->
            if (artifact.modelUrl.isNotBlank()) return@forEach
            val marker = Marker(map).apply {
                position = OsmGeoPoint(artifact.location.lat, artifact.location.lng)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = artifact.name
                snippet = if (artifact.inRange) "" else "too_far"
                icon = createArtifactMarkerDrawable(context, artifact.inRange)
                isDraggable = false
                setOnMarkerClickListener { _, _ ->
                    if (artifact.inRange) {
                        artifact.onClick()
                    } else {
                        artifact.onOutOfRangeClick()
                    }
                    true
                }
            }
            newFallbackMarkers[artifact.id] = marker
            map.overlays.add(marker)
        }
        fallbackMarkerOverlays = newFallbackMarkers

        arDrawingMarkerOverlays.values.forEach { map.overlays.remove(it) }
        val newDrawingMarkers = mutableMapOf<String, Marker>()
        mapArDrawings.forEach { drawing ->
            val marker = Marker(map).apply {
                position = OsmGeoPoint(drawing.location.lat, drawing.location.lng)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = drawing.title.ifBlank { "AR drawing" }
                snippet = if (drawing.inRange) "" else "too_far"
                icon = createArDrawingMarkerDrawable(context, drawing.inRange)
                isDraggable = false
                setOnMarkerClickListener { _, _ ->
                    if (drawing.inRange) {
                        drawing.onClick()
                    } else {
                        drawing.onOutOfRangeClick()
                    }
                    true
                }
            }
            newDrawingMarkers[drawing.id] = marker
            map.overlays.add(marker)
        }
        arDrawingMarkerOverlays = newDrawingMarkers

        val activeIds = mapArtifacts.filter { it.modelUrl.isNotBlank() }.map { it.id }.toSet()
        glbPreviewHosts.values.filter { it.id !in activeIds }.toList().forEach { removeGlbPreviewHost(it) }

        mapArtifacts.filter { it.modelUrl.isNotBlank() }.forEach { artifact ->
            val host = ensureGlbPreviewHost(map, context, artifact.id)
            if (!map.overlays.contains(host.tapOverlay)) {
                map.overlays.add(host.tapOverlay)
            }
            if (!map.overlays.contains(host.tapMarker)) {
                map.overlays.add(host.tapMarker)
            }
            map.bringGlbTapMarkerToFront(host.tapMarker)
        }

        syncAllGlbPreviewLayouts()
    }

    LaunchedEffect(mapView) {
        val map = mapView ?: return@LaunchedEffect
        while (true) {
            when (val reading = latestCompassReading) {
                null -> {
                    orientationFilter.reset()
                    if (map.mapOrientation != 0f) {
                        map.mapOrientation = 0f
                        glbPreviewHosts.values.forEach { it.previewView.rotation = 0f }
                        map.invalidate()
                    }
                }
                else -> if (reading.isReliable) {
                    val orientation = orientationFilter.filter(
                        azimuthToMapOrientation(reading.azimuthDegrees),
                    )
                    if (map.mapOrientation != orientation) {
                        map.mapOrientation = orientation
                        glbPreviewHosts.values.forEach { it.previewView.rotation = orientation }
                        map.invalidate()
                    }
                }
            }
            kotlinx.coroutines.delay(50L)
        }
    }

    LaunchedEffect(glbPreviewHosts.keys.toList(), mapArtifacts) {
        val map = mapView ?: return@LaunchedEffect
        mapArtifacts.filter { it.modelUrl.isNotBlank() }.forEach { artifact ->
            val host = ensureGlbPreviewHost(map, context, artifact.id)
            host.previewView.setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
            )
            host.previewView.setContent {
                ArtifactMapModelPreview(
                    modelUrl = artifact.modelUrl,
                    inRange = artifact.inRange,
                    autoAnimate = artifact.autoAnimate,
                    onClick = artifact.onClick,
                    onOutOfRangeClick = artifact.onOutOfRangeClick,
                    size = previewSizeDp,
                )
            }
        }
        syncAllGlbPreviewLayouts()
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
                                    glbPreviewHosts.values.forEach { it.previewView.rotation = mapOrientation }
                                    invalidate()
                                    return false
                                }

                                override fun onZoom(event: ZoomEvent?): Boolean {
                                    syncAllGlbPreviewLayouts()
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
                    mapView = map
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

private const val GLB_PREVIEW_TAP_SLOP_PX = 24f
