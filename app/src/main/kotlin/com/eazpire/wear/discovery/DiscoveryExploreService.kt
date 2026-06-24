package com.eazpire.wear.discovery

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.eazpire.wear.MainActivity
import com.eazpire.wear.R
import com.eazpire.wear.brand.WearNotificationIcon
import com.eazpire.wear.core.api.WearPlayerApi
import com.eazpire.wear.core.auth.SecureTokenStore
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DiscoveryExploreService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val trackBuffer = DiscoveryTrackBuffer(flushThreshold = 15)
    private lateinit var stepTracker: ExploreStepTracker
    private var api: WearPlayerApi? = null
    private lateinit var fusedClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var paused = false

    override fun onCreate() {
        super.onCreate()
        stepTracker = ExploreStepTracker(this, scope)
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        scope.launch { WearNotificationIcon.warmFromAdmin(this@DiscoveryExploreService) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                flushBuffer()
                stepTracker.stop()
                DiscoveryExploreState.markExploring(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                paused = true
                stopLocationUpdates()
                stepTracker.pause()
                DiscoveryExploreState.markPaused(true)
                updateNotification(getString(R.string.discovery_notification_paused))
                return START_STICKY
            }
            ACTION_RESUME -> {
                paused = false
                DiscoveryExploreState.markPaused(false)
                updateNotification(getString(R.string.discovery_notification_exploring))
                stepTracker.resume()
                startLocationUpdates()
                return START_STICKY
            }
        }

        val jwt = SecureTokenStore.get(this).getJwt()
        api = WearPlayerApi(jwt = jwt)
        stepTracker.attachApi(api)
        paused = false

        createChannel()
        DiscoveryExploreState.markExploring(true)
        DiscoveryExploreState.markPaused(false)
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.discovery_notification_exploring)))

        scope.launch {
            runCatching { api?.moveSessionStart() }
        }

        stepTracker.start()
        startLocationUpdates()
        return START_STICKY
    }

    private fun startLocationUpdates() {
        if (paused) return
        stopLocationUpdates()

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L)
            .setMinUpdateDistanceMeters(10f)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (paused) return
                for (loc in result.locations) {
                    trackBuffer.add(
                        loc.latitude,
                        loc.longitude,
                        loc.time,
                        loc.accuracy.toDouble(),
                    )
                    DiscoveryExploreState.addLocation(
                        loc.latitude,
                        loc.longitude,
                        if (loc.hasAltitude()) loc.altitude else null,
                    )
                }
                if (trackBuffer.shouldFlush()) flushBuffer()
            }
        }

        try {
            fusedClient.requestLocationUpdates(
                request,
                locationCallback!!,
                Looper.getMainLooper(),
            )
        } catch (_: SecurityException) {
            DiscoveryExploreState.markExploring(false)
            stopSelf()
        }
    }

    private fun stopLocationUpdates() {
        if (::fusedClient.isInitialized) {
            locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        }
        locationCallback = null
    }

    private fun flushBuffer() {
        val payload = trackBuffer.drainForUpload() ?: return
        val (batchId, points) = payload
        scope.launch {
            runCatching {
                api?.discoverySyncTrack(batchId, points)
            }
        }
    }

    override fun onDestroy() {
        stopLocationUpdates()
        flushBuffer()
        stepTracker.stop()
        DiscoveryExploreState.markExploring(false)
        scope.launch {
            runCatching { api?.moveSessionEnd() }
        }
        super.onDestroy()
    }

    private fun updateNotification(text: String) {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun createChannel() {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(CHANNEL_ID, "World Discovery", NotificationManager.IMPORTANCE_LOW)
        mgr.createNotificationChannel(ch)
    }

    private fun buildNotification(text: String): Notification {
        val pending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.discovery_notification_title))
            .setContentText(text)
            .setSmallIcon(WearNotificationIcon.smallIconRes())
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "discovery_explore"
        const val NOTIFICATION_ID = 7701
        const val ACTION_STOP = "com.eazpire.wear.discovery.STOP"
        const val ACTION_PAUSE = "com.eazpire.wear.discovery.PAUSE"
        const val ACTION_RESUME = "com.eazpire.wear.discovery.RESUME"

        fun start(context: Context) {
            val i = Intent(context, DiscoveryExploreService::class.java)
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            val i = Intent(context, DiscoveryExploreService::class.java).setAction(ACTION_STOP)
            context.startService(i)
        }

        fun pause(context: Context) {
            val i = Intent(context, DiscoveryExploreService::class.java).setAction(ACTION_PAUSE)
            context.startService(i)
        }

        fun resume(context: Context) {
            val i = Intent(context, DiscoveryExploreService::class.java).setAction(ACTION_RESUME)
            context.startService(i)
        }
    }
}
