package com.eazpire.wear.health

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Where step counts come from. Health Connect is optional — phone sensor is the reliable fallback.
 */
enum class StepSource {
    HEALTH_CONNECT,
    PHONE_SENSOR,
    NONE,
}

/**
 * Picks Health Connect vs [Sensor.TYPE_STEP_COUNTER] automatically.
 *
 * Priority:
 * 1. Health Connect when installed, permitted, and recently returning step data
 * 2. Phone step counter when [ACTIVITY_RECOGNITION] is granted
 * 3. None — explore still works, steps stay at 0
 */
class StepSourceSelector(
    private val context: Context,
    private val stepSync: StepSyncHelper = StepSyncHelper(context),
    private val phoneStepCounter: PhoneStepCounter = PhoneStepCounter(context),
) {
    fun canUsePhoneSensor(): Boolean =
        hasActivityRecognition() && phoneStepCounter.isAvailable()

    suspend fun resolveInitialSource(): StepSource {
        if (stepSync.canUseHealthConnect()) {
            if (stepSync.hasRecentStepData()) {
                return StepSource.HEALTH_CONNECT
            }
            if (canUsePhoneSensor()) {
                return StepSource.PHONE_SENSOR
            }
            // HC permitted but empty — still try HC in case data appears while walking
            return StepSource.HEALTH_CONNECT
        }
        if (canUsePhoneSensor()) {
            return StepSource.PHONE_SENSOR
        }
        return StepSource.NONE
    }

    /** Switch away from HC when it stays empty while the phone sensor could track steps. */
    suspend fun shouldFallbackFromHealthConnect(
        segmentSteps: Long,
        sessionElapsedMs: Long,
    ): Boolean {
        if (segmentSteps > 0) return false
        if (sessionElapsedMs < FALLBACK_GRACE_MS) return false
        if (!canUsePhoneSensor()) return false
        // HC is permitted but has not produced any steps this session — use sensor instead
        if (!stepSync.canUseHealthConnect()) return true
        return !stepSync.hasRecentStepData()
    }

    /** HC became unavailable or lost permission — fall back if possible. */
    suspend fun healthConnectStillUsable(): Boolean =
        stepSync.canUseHealthConnect()

    private fun hasActivityRecognition(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACTIVITY_RECOGNITION,
        ) == PackageManager.PERMISSION_GRANTED

    companion object {
        /** Wait before abandoning HC so a slow provider (Samsung Health) can catch up. */
        const val FALLBACK_GRACE_MS = 30_000L
    }
}
