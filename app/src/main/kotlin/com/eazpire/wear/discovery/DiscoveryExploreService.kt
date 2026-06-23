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
import com.eazpire.wear.core.api.WearPlayerApi
import com.eazpire.wear.core.auth.SecureTokenStore
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
    private var api: WearPlayerApi? = null
    private var fusedClient = LocationServices.getFusedLocationProviderClient(this)
    private var locationCallback: LocationCallback? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                flushBuffer()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val jwt = SecureTokenStore.get(this).getJwt()
        api = WearPlayerApi(jwt = jwt)

        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Exploring…"))

        scope.launch {
            runCatching { api?.moveSessionStart() }
        }

        startLocationUpdates()
        return START_STICKY
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L)
            .setMinUpdateDistanceMeters(10f)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (loc in result.locations) {
                    trackBuffer.add(
                        loc.latitude,
                        loc.longitude,
                        loc.time,
                        loc.accuracy.toDouble(),
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
            stopSelf()
        }
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
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        flushBuffer()
        scope.launch {
            runCatching { api?.moveSessionEnd() }
        }
        super.onDestroy()
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
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "discovery_explore"
        const val NOTIFICATION_ID = 7701
        const val ACTION_STOP = "com.eazpire.wear.discovery.STOP"

        fun start(context: Context) {
            val i = Intent(context, DiscoveryExploreService::class.java)
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            val i = Intent(context, DiscoveryExploreService::class.java).setAction(ACTION_STOP)
            context.startService(i)
        }
    }
}
