package com.eazpire.wear.discovery

import android.util.Log
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Earth
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import kotlin.math.cos
import kotlin.math.sin
import org.json.JSONObject

private const val LOG_TAG = "ArGeospatial"

/** ARCore Geospatial / VPS snapshot at placement or resolve time. */
data class ArGeospatialSnapshot(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val headingDegrees: Double,
    val horizontalAccuracyM: Double,
    val verticalAccuracyM: Double,
    val headingAccuracyDeg: Double,
    val vpsAvailable: Boolean,
    val earthTracking: Boolean,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("latitude", latitude)
        .put("longitude", longitude)
        .put("altitude", altitude)
        .put("heading", headingDegrees)
        .put("horizontal_accuracy_m", horizontalAccuracyM)
        .put("vertical_accuracy_m", verticalAccuracyM)
        .put("heading_accuracy_deg", headingAccuracyDeg)
        .put("vps_available", vpsAvailable)

    companion object {
        fun fromJson(raw: String?): ArGeospatialSnapshot? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val o = JSONObject(raw)
                ArGeospatialSnapshot(
                    latitude = o.getDouble("latitude"),
                    longitude = o.getDouble("longitude"),
                    altitude = o.getDouble("altitude"),
                    headingDegrees = o.optDouble("heading", 0.0),
                    horizontalAccuracyM = o.optDouble("horizontal_accuracy_m", 99.0),
                    verticalAccuracyM = o.optDouble("vertical_accuracy_m", 99.0),
                    headingAccuracyDeg = o.optDouble("heading_accuracy_deg", 99.0),
                    vpsAvailable = o.optBoolean("vps_available", false),
                    earthTracking = true,
                )
            }.getOrNull()
        }
    }
}

enum class ArPlacementQuality {
    Poor,
    Medium,
    Good,
    Excellent,
}

object ArGeospatialManager {
    const val MIN_HORIZONTAL_ACCURACY_GOOD_M = 5.0
    const val MIN_HORIZONTAL_ACCURACY_EXCELLENT_M = 2.0
    const val MIN_HEADING_ACCURACY_GOOD_DEG = 15.0
    const val MIN_HEADING_ACCURACY_EXCELLENT_DEG = 10.0

    fun enableInConfig(config: Config) {
        config.geospatialMode = Config.GeospatialMode.ENABLED
    }

    fun isVpsAvailable(session: Session): Boolean {
        val earth = session.earth ?: return false
        return earth.earthState == Earth.EarthState.ENABLED &&
            earth.trackingState == TrackingState.TRACKING
    }

    fun captureSnapshot(session: Session): ArGeospatialSnapshot? {
        val earth = session.earth ?: return null
        if (earth.trackingState != TrackingState.TRACKING) return null
        val pose = earth.cameraGeospatialPose
        return ArGeospatialSnapshot(
            latitude = pose.latitude,
            longitude = pose.longitude,
            altitude = pose.altitude,
            headingDegrees = pose.heading,
            horizontalAccuracyM = pose.horizontalAccuracy.toDouble(),
            verticalAccuracyM = pose.verticalAccuracy.toDouble(),
            headingAccuracyDeg = pose.headingAccuracy.toDouble(),
            vpsAvailable = earth.earthState == Earth.EarthState.ENABLED,
            earthTracking = true,
        )
    }

    fun evaluatePlacementQuality(
        snapshot: ArGeospatialSnapshot?,
        cameraTracking: Boolean,
        hasDetectedSurface: Boolean,
    ): ArPlacementQuality {
        if (!cameraTracking) return ArPlacementQuality.Poor
        if (snapshot == null) {
            return if (hasDetectedSurface) ArPlacementQuality.Medium else ArPlacementQuality.Poor
        }
        if (!snapshot.vpsAvailable) {
            return if (hasDetectedSurface) ArPlacementQuality.Medium else ArPlacementQuality.Poor
        }
        val h = snapshot.horizontalAccuracyM
        val head = snapshot.headingAccuracyDeg
        return when {
            h <= MIN_HORIZONTAL_ACCURACY_EXCELLENT_M &&
                head <= MIN_HEADING_ACCURACY_EXCELLENT_DEG -> ArPlacementQuality.Excellent
            h <= MIN_HORIZONTAL_ACCURACY_GOOD_M &&
                head <= MIN_HEADING_ACCURACY_GOOD_DEG -> ArPlacementQuality.Good
            h <= 12.0 -> ArPlacementQuality.Medium
            else -> ArPlacementQuality.Poor
        }
    }

    /** Create a VPS-backed anchor at stored WGS84 coordinates (outdoor rediscovery). */
    fun createGeospatialAnchor(
        session: Session,
        latitude: Double,
        longitude: Double,
        altitude: Double,
        headingDegrees: Double,
    ): Anchor? {
        val earth = session.earth ?: return null
        if (earth.trackingState != TrackingState.TRACKING) {
            Log.w(LOG_TAG, "earth not tracking — geospatial anchor skipped")
            return null
        }
        val q = headingToQuaternion(headingDegrees)
        return runCatching {
            earth.createAnchor(latitude, longitude, altitude, q)
        }.onFailure { error ->
            Log.w(LOG_TAG, "createAnchor geospatial failed", error)
        }.getOrNull()
    }

    fun createGeospatialAnchor(session: Session, snapshot: ArGeospatialSnapshot): Anchor? =
        createGeospatialAnchor(
            session,
            snapshot.latitude,
            snapshot.longitude,
            snapshot.altitude,
            snapshot.headingDegrees,
        )

    private fun headingToQuaternion(headingDegrees: Double): FloatArray {
        val half = Math.toRadians(headingDegrees).toFloat() / 2f
        return floatArrayOf(0f, sin(half), 0f, cos(half))
    }

    /** Preferred anchor strategy label for backend persistence. */
    fun anchorTypeForSave(cloudAnchorId: String?, snapshot: ArGeospatialSnapshot?): String =
        when {
            !cloudAnchorId.isNullOrBlank() -> "cloud_anchor"
            snapshot != null && snapshot.vpsAvailable -> "geospatial"
            else -> "local_fallback"
        }
}
