package com.eazpire.wear.discovery

import android.view.View
import androidx.compose.ui.platform.ComposeView
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import kotlin.math.pow

/**
 * Geo-anchored GLB preview on the map via osmdroid [MapView.LayoutParams] (same mechanism as
 * built-in map markers). Taps use a transparent [Marker] at the same [GeoPoint].
 */
internal data class MapGlbPreviewHost(
    val mapView: MapView,
    val previewView: ComposeView,
    val tapMarker: Marker,
)

internal fun scaledGlbPreviewSizePx(
    baseSizePx: Int,
    zoom: Double,
    referenceZoom: Double,
): Int {
    val raw = 1.0 + (zoom - referenceZoom) * GLB_PREVIEW_ZOOM_SIZE_GAIN
    val scale = raw.toFloat().coerceIn(GLB_PREVIEW_MIN_ZOOM_SCALE, GLB_PREVIEW_MAX_ZOOM_SCALE)
    return (baseSizePx * scale).toInt().coerceAtLeast(1)
}

internal fun MapView.layoutGlbPreviewAt(
    previewView: View,
    geo: GeoPoint,
    sizePx: Int,
) {
    val osmPoint = OsmGeoPoint(geo.lat, geo.lng)
    val lp = previewView.layoutParams as? MapView.LayoutParams
    if (lp != null) {
        lp.width = sizePx
        lp.height = sizePx
        lp.geoPoint = osmPoint
        lp.alignment = MapView.LayoutParams.CENTER
        updateViewLayout(previewView, lp)
    } else {
        addView(
            previewView,
            MapView.LayoutParams(
                sizePx,
                sizePx,
                osmPoint,
                MapView.LayoutParams.CENTER,
                0,
                0,
            ),
        )
    }
    previewView.rotation = mapOrientation
    previewView.scaleX = 1f
    previewView.scaleY = 1f
    requestLayout()
    invalidate()
}

internal fun MapView.syncGlbTapMarker(
    marker: Marker,
    geo: GeoPoint,
    sizePx: Int,
    context: android.content.Context,
) {
    marker.position = OsmGeoPoint(geo.lat, geo.lng)
    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
    marker.icon = createTransparentHitDrawable(context, sizePx)
}

private const val GLB_PREVIEW_ZOOM_SIZE_GAIN = 0.14
private const val GLB_PREVIEW_MIN_ZOOM_SCALE = 0.65f
private const val GLB_PREVIEW_MAX_ZOOM_SCALE = 1.9f
