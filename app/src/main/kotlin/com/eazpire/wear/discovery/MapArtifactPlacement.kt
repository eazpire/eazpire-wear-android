package com.eazpire.wear.discovery

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Demo placement offset from the user (meters). */
const val MAP_ARTIFACT_OFFSET_NORTH_M = 35.0
const val MAP_ARTIFACT_OFFSET_EAST_M = 18.0

/** Distance at which the map marker becomes tappable. */
const val MAP_ARTIFACT_PROXIMITY_M = 45.0

fun offsetFromUser(lat: Double, lng: Double, northM: Double, eastM: Double): GeoPoint {
    val earthRadius = 6_371_000.0
    val dLat = northM / earthRadius
    val dLng = eastM / (earthRadius * cos(Math.toRadians(lat)).coerceAtLeast(0.0001))
    return GeoPoint(
        lat + Math.toDegrees(dLat),
        lng + Math.toDegrees(dLng),
    )
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
