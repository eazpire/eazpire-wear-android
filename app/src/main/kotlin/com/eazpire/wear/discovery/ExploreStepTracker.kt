package com.eazpire.wear.discovery

import android.content.Context
import com.eazpire.wear.core.api.WearPlayerApi
import com.eazpire.wear.health.PhoneStepCounter
import com.eazpire.wear.health.StepSource
import com.eazpire.wear.health.StepSourceSelector
import com.eazpire.wear.health.StepSyncHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Tracks explore-session step delta via Health Connect (optional) or phone step counter sensor.
 * Automatically switches sources — Health Connect is never required.
 */
class ExploreStepTracker(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val stepSync = StepSyncHelper(context)
    private val sourceSelector = StepSourceSelector(context, stepSync)
    private val phoneStepCounter = PhoneStepCounter(context)

    private var pollJob: Job? = null
    private var syncJob: Job? = null
    private var segmentStart: Instant? = null
    private var sessionStartedAt: Long = 0L
    private var accumulatedSteps: Long = 0
    private var segmentSensorSteps: Long = 0
    private var lastSyncedSteps: Long = 0
    private var running = false
    private var paused = false
    private var activeSource = StepSource.NONE
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
        sessionStartedAt = System.currentTimeMillis()
        segmentStart = Instant.now()
        DiscoveryExploreState.updateSessionSteps(0)

        scope.launch {
            activeSource = sourceSelector.resolveInitialSource()
            applySource(activeSource)
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
        scope.launch {
            if (activeSource == StepSource.HEALTH_CONNECT && !sourceSelector.healthConnectStillUsable()) {
                switchSource(sourceSelector.resolveInitialSource())
            }
            when (activeSource) {
                StepSource.HEALTH_CONNECT -> startHealthConnectPolling()
                StepSource.PHONE_SENSOR -> {
                    phoneStepCounter.resetSegment()
                    startSensorTracking()
                }
                StepSource.NONE -> Unit
            }
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
        return when (activeSource) {
            StepSource.HEALTH_CONNECT -> accumulatedSteps + readHealthConnectSegmentSteps()
            StepSource.PHONE_SENSOR -> accumulatedSteps + segmentSensorSteps
            StepSource.NONE -> accumulatedSteps
        }
    }

    private suspend fun applySource(source: StepSource) {
        stopHealthConnectPolling()
        phoneStepCounter.stop()
        activeSource = source
        when (source) {
            StepSource.HEALTH_CONNECT -> startHealthConnectPolling()
            StepSource.PHONE_SENSOR -> startSensorTracking()
            StepSource.NONE -> DiscoveryExploreState.updateSessionSteps(accumulatedSteps)
        }
    }

    private suspend fun switchSource(newSource: StepSource) {
        if (newSource == activeSource) return
        accumulatedSteps = currentSessionSteps()
        segmentStart = Instant.now()
        segmentSensorSteps = 0
        applySource(newSource)
        DiscoveryExploreState.updateSessionSteps(accumulatedSteps)
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
                val total = currentSessionSteps()
                DiscoveryExploreState.updateSessionSteps(total)

                if (!sourceSelector.healthConnectStillUsable()) {
                    switchSource(fallbackSource())
                    break
                }

                val segmentSteps = readHealthConnectSegmentSteps()
                val elapsed = System.currentTimeMillis() - sessionStartedAt
                if (sourceSelector.shouldFallbackFromHealthConnect(segmentSteps, elapsed)) {
                    switchSource(StepSource.PHONE_SENSOR)
                    break
                }

                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun fallbackSource(): StepSource {
        if (sourceSelector.canUsePhoneSensor()) return StepSource.PHONE_SENSOR
        return StepSource.NONE
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
        return runCatching { stepSync.readStepsBetween(start, Instant.now()) }.getOrDefault(0L)
    }

    private suspend fun syncStepsDelta(delta: Long, newTotal: Long) {
        if (delta <= 0) return
        runCatching {
            api?.moveToEarnSyncSteps(delta)
            lastSyncedSteps = newTotal
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 3_000L
        private const val SYNC_INTERVAL_MS = 60_000L
    }
}
