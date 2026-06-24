package com.eazpire.wear.discovery

import android.location.Location
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Random offset range so the artifact sits beside the user (meters). */
const val MAP_ARTIFACT_OFFSET_MIN_M = 2.0
const val MAP_ARTIFACT_OFFSET_MAX_M = 5.0

/** Distance at which the map marker becomes tappable. */
const val MAP_ARTIFACT_PROXIMITY_M = 12.0

/** Fallback floor when GPS altitude is missing (2 = second floor). */
const val MAP_ARTIFACT_ASSUMED_FLOOR = 2
const val MAP_ARTIFACT_METERS_PER_FLOOR = 3.0

/** GPS altitude when available; otherwise estimated height for [MAP_ARTIFACT_ASSUMED_FLOOR]. */
fun resolveArtifactAltitudeM(userAltitudeM: Double?): Double =
    userAltitudeM ?: (MAP_ARTIFACT_ASSUMED_FLOOR * MAP_ARTIFACT_METERS_PER_FLOOR).toDouble()

/** Place artifact a few meters from the user at the same floor / altitude. */
fun placeArtifactNearUser(
    lat: Double,
    lng: Double,
    altitudeM: Double? = null,
    random: Random = Random.Default,
): GeoPoint {
    val resolvedAlt = resolveArtifactAltitudeM(altitudeM)
    val distanceM = MAP_ARTIFACT_OFFSET_MIN_M +
        random.nextDouble() * (MAP_ARTIFACT_OFFSET_MAX_M - MAP_ARTIFACT_OFFSET_MIN_M)
    val bearingRad = random.nextDouble() * 2.0 * PI
    val northM = distanceM * cos(bearingRad)
    val eastM = distanceM * sin(bearingRad)
    return offsetFromUser(lat, lng, northM, eastM, altitudeM = resolvedAlt)
}

fun offsetFromUser(
    lat: Double,
    lng: Double,
    northM: Double,
    eastM: Double,
    altitudeM: Double? = null,
): GeoPoint {
    val earthRadius = 6_371_000.0
    val dLat = northM / earthRadius
    val dLng = eastM / (earthRadius * cos(Math.toRadians(lat)).coerceAtLeast(0.0001))
    return GeoPoint(
        lat + Math.toDegrees(dLat),
        lng + Math.toDegrees(dLng),
        altitudeM = altitudeM,
    )
}

/** Bearing from [from] to [to] in degrees (0 = north, clockwise). */
fun bearingDegrees(from: GeoPoint, to: GeoPoint): Float {
    val fromLoc = Location("from").apply {
        latitude = from.lat
        longitude = from.lng
    }
    val toLoc = Location("to").apply {
        latitude = to.lat
        longitude = to.lng
    }
    return (fromLoc.bearingTo(toLoc) + 360f) % 360f
}

/** Normalize degrees to -180..180 for compass-relative rotation. */
fun normalizeAngleDegrees(degrees: Float): Float {
    var angle = degrees % 360f
    if (angle > 180f) angle -= 360f
    if (angle < -180f) angle += 360f
    return angle
}

fun distanceMeters(a: GeoPoint, b: GeoPoint): Double =
    haversineM(a.lat, a.lng, b.lat, b.lng)

fun isWithinArtifactRange(user: GeoPoint?, artifact: GeoPoint?): Boolean {
    if (user == null || artifact == null) return false
    return distanceMeters(user, artifact) <= MAP_ARTIFACT_PROXIMITY_M
}

private fun haversineM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
    return 2 * r * asin(sqrt(a))
}
