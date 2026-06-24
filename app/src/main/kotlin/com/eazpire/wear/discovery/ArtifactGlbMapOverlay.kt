package com.eazpire.wear.discovery

import android.graphics.Canvas
import android.graphics.Point
import android.view.View
import androidx.compose.ui.platform.ComposeView
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * Positions a map-attached view at a fixed geographic point on every map draw
 * (scroll, zoom, bearing, layout) so screen coords stay in sync with tiles.
 */
internal class ArtifactGlbMapOverlay(
    private val previewView: View,
    private val halfSizePx: Int,
    private val geoPointProvider: () -> GeoPoint?,
    private val enabledProvider: () -> Boolean,
) : Overlay() {

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

        val onScreen = point.x >= -halfSizePx &&
            point.x <= mapView.width + halfSizePx &&
            point.y >= -halfSizePx &&
            point.y <= mapView.height + halfSizePx

        if (!onScreen) {
            previewView.visibility = View.GONE
            return
        }

        previewView.visibility = View.VISIBLE
        previewView.x = (point.x - halfSizePx).toFloat()
        previewView.y = (point.y - halfSizePx).toFloat()
    }
}

internal data class MapGlbPreviewHost(
    val mapView: MapView,
    val previewView: ComposeView,
    val overlay: ArtifactGlbMapOverlay,
)
