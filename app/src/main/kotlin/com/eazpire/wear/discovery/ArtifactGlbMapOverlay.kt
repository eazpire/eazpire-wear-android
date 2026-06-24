package com.eazpire.wear.discovery

import android.graphics.Canvas
import android.graphics.Point
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import androidx.compose.ui.platform.ComposeView
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay

/**
 * Geo-anchored GLB preview on the map via osmdroid [MapView.LayoutParams] (same mechanism as
 * built-in map markers). Taps use [ArtifactGlbTapOverlay.onSingleTapConfirmed] plus a transparent
 * [Marker] at the same [GeoPoint]; the preview view forwards touches to the [MapView].
 */
internal data class MapGlbPreviewHost(
    val id: String,
    val mapView: MapView,
    val previewView: ComposeView,
    val tapMarker: Marker,
    val tapOverlay: ArtifactGlbTapOverlay,
)

/**
 * Single-tap hit test over the geo-anchored GLB preview (tap vs pan). Fires after touch
 * forwarding from the preview [ComposeView] so osmdroid overlay dispatch runs reliably.
 */
internal class ArtifactGlbTapOverlay(
    private val geoPointProvider: () -> GeoPoint?,
    private val enabledProvider: () -> Boolean,
    private val sizePxProvider: () -> Int,
    private val inRangeProvider: () -> Boolean,
    private val onInRangeClick: () -> Unit,
    private val onOutOfRangeClick: () -> Unit,
) : Overlay() {

    private fun hitRect(mapView: MapView): Rect? {
        if (!enabledProvider()) return null
        val geo = geoPointProvider() ?: return null
        val point = Point()
        mapView.projection.toPixels(OsmGeoPoint(geo.lat, geo.lng), point)
        val half = sizePxProvider() / 2
        return Rect(
            point.x - half,
            point.y - half,
            point.x + half,
            point.y + half,
        )
    }

    override fun onSingleTapConfirmed(event: MotionEvent?, mapView: MapView?): Boolean {
        if (event == null || mapView == null) return false
        val rect = hitRect(mapView) ?: return false
        if (!rect.contains(event.x.toInt(), event.y.toInt())) return false
        if (inRangeProvider()) {
            onInRangeClick()
        } else {
            onOutOfRangeClick()
        }
        return true
    }

    override fun draw(canvas: Canvas?, mapView: MapView?, shadow: Boolean) = Unit
}

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
    marker.isEnabled = true
    bringGlbTapMarkerToFront(marker)
}

internal fun MapView.bringGlbTapMarkerToFront(marker: Marker) {
    overlays.remove(marker)
    overlays.add(marker)
}

private const val GLB_PREVIEW_ZOOM_SIZE_GAIN = 0.14
private const val GLB_PREVIEW_MIN_ZOOM_SCALE = 0.65f
private const val GLB_PREVIEW_MAX_ZOOM_SCALE = 1.9f
