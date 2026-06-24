package com.eazpire.wear.discovery

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoPoint(
    val lat: Double,
    val lng: Double,
    /** GPS altitude in meters above WGS84 ellipsoid, when available. */
    val altitudeM: Double? = null,
)

/**
 * In-memory explore session state shared between [DiscoveryExploreService] and Move UI.
 */
object DiscoveryExploreState {
    private val _exploring = MutableStateFlow(false)
    val exploring: StateFlow<Boolean> = _exploring.asStateFlow()

    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    private val _currentLocation = MutableStateFlow<GeoPoint?>(null)
    val currentLocation: StateFlow<GeoPoint?> = _currentLocation.asStateFlow()

    private val _trackPoints = MutableStateFlow<List<GeoPoint>>(emptyList())
    val trackPoints: StateFlow<List<GeoPoint>> = _trackPoints.asStateFlow()

    private val _sessionDistanceM = MutableStateFlow(0.0)
    val sessionDistanceM: StateFlow<Double> = _sessionDistanceM.asStateFlow()

    private val _sessionSteps = MutableStateFlow(0L)
    val sessionSteps: StateFlow<Long> = _sessionSteps.asStateFlow()

    fun markExploring(active: Boolean) {
        _exploring.value = active
        if (!active) resetSession()
    }

    fun markPaused(paused: Boolean) {
        _paused.value = paused
    }

    fun addLocation(lat: Double, lng: Double, altitudeM: Double? = null) {
        val point = GeoPoint(lat, lng, altitudeM)
        val prev = _currentLocation.value
        if (prev != null) {
            _sessionDistanceM.value += haversineM(prev.lat, prev.lng, lat, lng)
        }
        _currentLocation.value = point
        _trackPoints.value = _trackPoints.value + point
    }

    fun updateSessionSteps(steps: Long) {
        _sessionSteps.value = steps.coerceAtLeast(0)
    }

    fun resetSession() {
        _paused.value = false
        _currentLocation.value = null
        _trackPoints.value = emptyList()
        _sessionDistanceM.value = 0.0
        _sessionSteps.value = 0L
    }

    private fun haversineM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }
}
