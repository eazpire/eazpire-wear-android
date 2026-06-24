package com.eazpire.wear.discovery

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.eazpire.wear.core.api.WearPlayerApi
import com.eazpire.wear.health.PhoneStepCounter
import com.eazpire.wear.health.StepSyncHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Tracks explore-session step delta (indoor-safe) via Health Connect or step counter sensor.
 */
class ExploreStepTracker(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val stepSync = StepSyncHelper(context)
    private val phoneStepCounter = PhoneStepCounter(context)

    private var pollJob: Job? = null
    private var syncJob: Job? = null
    private var segmentStart: Instant? = null
    private var accumulatedSteps: Long = 0
    private var segmentSensorSteps: Long = 0
    private var lastSyncedSteps: Long = 0
    private var running = false
    private var paused = false
    private var useHealthConnect = false
    private var api: WearPlayerApi? = null

    fun attachApi(wearApi: WearPlayerApi?) {
        api = wearApi
    }

    fun start() {
        if (running) return
        running = true
        paused = false
        accumulatedSteps = 0
        segmentSensorSteps = 0
        lastSyncedSteps = 0
        segmentStart = Instant.now()
        DiscoveryExploreState.updateSessionSteps(0)

        scope.launch {
            useHealthConnect = stepSync.hasPermissions()
            if (useHealthConnect) {
                phoneStepCounter.stop()
                startHealthConnectPolling()
            } else if (hasActivityRecognition() && phoneStepCounter.isAvailable()) {
                startSensorTracking()
            }
            startPeriodicSync()
        }
    }

    fun pause() {
        if (!running || paused) return
        paused = true
        stopHealthConnectPolling()
        phoneStepCounter.stop()
        scope.launch {
            accumulatedSteps = currentSessionSteps()
            DiscoveryExploreState.updateSessionSteps(accumulatedSteps)
        }
    }

    fun resume() {
        if (!running || !paused) return
        paused = false
        segmentStart = Instant.now()
        segmentSensorSteps = 0
        if (useHealthConnect) {
            startHealthConnectPolling()
        } else if (hasActivityRecognition() && phoneStepCounter.isAvailable()) {
            phoneStepCounter.resetSegment()
            startSensorTracking()
        }
    }

    fun stop() {
        if (!running) return
        running = false
        paused = false
        stopHealthConnectPolling()
        stopPeriodicSync()
        phoneStepCounter.stop()
        scope.launch {
            val finalSteps = currentSessionSteps()
            DiscoveryExploreState.updateSessionSteps(finalSteps)
            syncStepsDelta(finalSteps - lastSyncedSteps, finalSteps)
        }
    }

    private suspend fun currentSessionSteps(): Long {
        return if (useHealthConnect) {
            accumulatedSteps + readHealthConnectSegmentSteps()
        } else {
            accumulatedSteps + segmentSensorSteps
        }
    }

    private fun startSensorTracking() {
        phoneStepCounter.onStepsDelta = { delta ->
            segmentSensorSteps = delta
            DiscoveryExploreState.updateSessionSteps(accumulatedSteps + delta)
        }
        phoneStepCounter.start()
    }

    private fun startHealthConnectPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive && running && !paused) {
                DiscoveryExploreState.updateSessionSteps(currentSessionSteps())
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopHealthConnectPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun startPeriodicSync() {
        syncJob?.cancel()
        syncJob = scope.launch {
            while (isActive && running) {
                delay(SYNC_INTERVAL_MS)
                if (paused) continue
                val total = currentSessionSteps()
                val delta = total - lastSyncedSteps
                if (delta > 0) syncStepsDelta(delta, total)
            }
        }
    }

    private fun stopPeriodicSync() {
        syncJob?.cancel()
        syncJob = null
    }

    private suspend fun readHealthConnectSegmentSteps(): Long {
        val start = segmentStart ?: return 0L
        return stepSync.readStepsBetween(start, Instant.now())
    }

    private suspend fun syncStepsDelta(delta: Long, newTotal: Long) {
        if (delta <= 0) return
        runCatching {
            api?.moveToEarnSyncSteps(delta)
            lastSyncedSteps = newTotal
        }
    }

    private fun hasActivityRecognition(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACTIVITY_RECOGNITION,
        ) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val POLL_INTERVAL_MS = 3_000L
        private const val SYNC_INTERVAL_MS = 60_000L
    }
}
