package com.eazpire.wear.discovery

import android.graphics.Canvas
import android.graphics.Point
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import androidx.compose.ui.platform.ComposeView
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * Positions a map-attached view at a fixed geographic point on every map draw
 * (scroll, zoom, bearing, layout) so screen coords stay in sync with tiles.
 *
 * Screen size grows slightly when zooming in (and shrinks when zooming out) so the
 * 3D preview does not appear to shrink relative to map tiles.
 *
 * Taps are handled via [onSingleTapConfirmed] (tap vs pan). The visual [previewView]
 * must stay touch-transparent so drags reach the [MapView].
 */
internal class ArtifactGlbMapOverlay(
    private val previewView: View,
    private val baseHalfSizePx: Int,
    private val referenceZoom: Double,
    private val geoPointProvider: () -> GeoPoint?,
    private val enabledProvider: () -> Boolean,
    private val inRangeProvider: () -> Boolean,
    private val onInRangeClick: () -> Unit,
    private val onOutOfRangeClick: () -> Unit,
) : Overlay() {

    private fun scaledHalfPx(mapView: MapView): Int {
        val zoomScale = zoomScaleFor(mapView.zoomLevelDouble)
        return (baseHalfSizePx * zoomScale).toInt()
    }

    private fun previewScreenRect(mapView: MapView): Rect? {
        val geo = geoPointProvider() ?: return null
        val point = Point()
        mapView.projection.toPixels(OsmGeoPoint(geo.lat, geo.lng), point)
        val half = scaledHalfPx(mapView)
        return Rect(
            point.x - half,
            point.y - half,
            point.x + half,
            point.y + half,
        )
    }

    private fun hitTest(event: MotionEvent, mapView: MapView): Boolean {
        if (!enabledProvider()) return false
        val rect = previewScreenRect(mapView) ?: return false
        return rect.contains(event.x.toInt(), event.y.toInt())
    }

    override fun onSingleTapConfirmed(event: MotionEvent?, mapView: MapView?): Boolean {
        if (event == null || mapView == null || !hitTest(event, mapView)) {
            return false
        }
        if (inRangeProvider()) {
            onInRangeClick()
        } else {
            onOutOfRangeClick()
        }
        return true
    }

    override fun draw(canvas: Canvas?, mapView: MapView?, shadow: Boolean) {
        if (shadow || mapView == null || !enabledProvider()) {
            previewView.visibility = View.GONE
            return
        }
        val geo = geoPointProvider()
        if (geo == null) {
            previewView.visibility = View.GONE
            return
        }

        val point = Point()
        mapView.projection.toPixels(OsmGeoPoint(geo.lat, geo.lng), point)

        val zoomScale = zoomScaleFor(mapView.zoomLevelDouble)
        val scaledHalfPx = (baseHalfSizePx * zoomScale).toInt()

        val onScreen = point.x >= -scaledHalfPx &&
            point.x <= mapView.width + scaledHalfPx &&
            point.y >= -scaledHalfPx &&
            point.y <= mapView.height + scaledHalfPx

        if (!onScreen) {
            previewView.visibility = View.GONE
            return
        }

        previewView.visibility = View.VISIBLE
        previewView.pivotX = baseHalfSizePx.toFloat()
        previewView.pivotY = baseHalfSizePx.toFloat()
        previewView.scaleX = zoomScale
        previewView.scaleY = zoomScale
        previewView.x = point.x - baseHalfSizePx.toFloat()
        previewView.y = point.y - baseHalfSizePx.toFloat()
    }

    private fun zoomScaleFor(zoom: Double): Float {
        val raw = 1.0 + (zoom - referenceZoom) * ZOOM_SIZE_GAIN
        return raw.toFloat().coerceIn(MIN_ZOOM_SCALE, MAX_ZOOM_SCALE)
    }

    private companion object {
        /** Extra screen size per zoom level above [referenceZoom]. */
        const val ZOOM_SIZE_GAIN = 0.14
        const val MIN_ZOOM_SCALE = 0.65f
        const val MAX_ZOOM_SCALE = 1.9f
    }
}

internal data class MapGlbPreviewHost(
    val mapView: MapView,
    val previewView: ComposeView,
    val overlay: ArtifactGlbMapOverlay,
)
