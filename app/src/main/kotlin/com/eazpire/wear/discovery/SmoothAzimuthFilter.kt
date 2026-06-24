package com.eazpire.wear.discovery

import kotlin.math.abs

/**
 * Low-pass filter for compass angles with correct wrap-around (359° → 1°).
 * Includes a dead zone so tiny sensor noise does not jitter the map.
 */
class SmoothAzimuthFilter(
    private val alpha: Float = 0.08f,
    private val deadZoneDegrees: Float = 2.5f,
) {
    private var value: Float? = null

    fun filter(targetDegrees: Float): Float {
        val normalizedTarget = normalizeDegrees360(targetDegrees)
        val current = value
        if (current == null) {
            value = normalizedTarget
            return normalizedTarget
        }
        val delta = normalizeAngleDegrees(normalizedTarget - current)
        if (abs(delta) < deadZoneDegrees) {
            return current
        }
        val next = normalizeDegrees360(current + delta * alpha)
        value = next
        return next
    }

    fun reset() {
        value = null
    }
}

/** Device heading (0 = north, clockwise) → OSMDroid map orientation. */
fun azimuthToMapOrientation(azimuthDegrees: Float): Float =
    normalizeDegrees360(360f - azimuthDegrees)

private fun normalizeDegrees360(degrees: Float): Float {
    var angle = degrees % 360f
    if (angle < 0f) angle += 360f
    return angle
}
