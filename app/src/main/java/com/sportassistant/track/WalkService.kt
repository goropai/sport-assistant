package com.sportassistant.track

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.sportassistant.MainActivity
import com.sportassistant.R

/**
 * Foreground service that keeps GPS updates flowing while the screen is off.
 * Uses the platform LocationManager (no Google Play services dependency, so it also works on
 * devices without them).
 */
class WalkService : Service() {
    private lateinit var locationManager: LocationManager

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            TrackRecorder.addFix(
                location.latitude,
                location.longitude,
                if (location.hasAccuracy()) location.accuracy else Float.MAX_VALUE,
                location.elapsedRealtimeNanos / 1_000_000,
            )
        }

        // Required on API < 30 where these are abstract.
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission") // checked via try/catch: permission is requested by the UI before start
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        TrackRecorder.start(SystemClock.elapsedRealtime())
        locationManager = getSystemService(LocationManager::class.java)
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 0f, listener, Looper.getMainLooper())
        } catch (e: SecurityException) {
            TrackRecorder.stop(SystemClock.elapsedRealtime())
            stopSelf()
        } catch (e: IllegalArgumentException) { // no GPS provider on this device
            TrackRecorder.stop(SystemClock.elapsedRealtime())
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (::locationManager.isInitialized) locationManager.removeUpdates(listener)
        super.onDestroy()
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.walk_channel_name), NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.walk_notif_title))
            .setContentText(getString(R.string.walk_notif_text))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
    }

    companion object {
        private const val CHANNEL_ID = "walk"
        private const val NOTIFICATION_ID = 100

        fun begin(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, WalkService::class.java))
        }

        fun end(context: Context) {
            context.stopService(Intent(context, WalkService::class.java))
        }
    }
}
