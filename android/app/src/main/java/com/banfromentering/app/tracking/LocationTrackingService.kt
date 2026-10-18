package com.banfromentering.app.tracking

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.banfromentering.app.BanApp
import com.banfromentering.app.data.Ban
import com.banfromentering.app.geo.ProximityTracker
import com.banfromentering.app.geo.TrackingCadence
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps receiving GPS fixes while the app is in the background
 * and raises a notification when the user approaches or enters a banned place.
 *
 * Checks every fix against the full local list of bans, so (unlike OS geofences,
 * capped at 100 per app) there is no limit on how many places can be monitored.
 */
class LocationTrackingService : LifecycleService() {

    private lateinit var fusedClient: FusedLocationProviderClient
    private val tracker = ProximityTracker()
    private var bans: List<Ban> = emptyList()
    private var cadence: TrackingCadence? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let(::onLocation)
        }
    }

    private val app get() = application as BanApp

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        lifecycleScope.launch {
            app.database.banDao().observeAll().collect { bans = it }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent?.action == ACTION_STOP) {
            app.settings.setTrackingEnabled(false)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Permissions.hasForegroundLocation(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        ServiceCompat.startForeground(
            this,
            Notifier.TRACKING_NOTIFICATION_ID,
            Notifier.trackingNotification(this),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
        )
        // Start precise; the first fix adapts the cadence to the distance of the nearest ban.
        requestUpdates(TrackingCadence.NEAR)
        return START_STICKY
    }

    private fun onLocation(location: Location) {
        val warningDistance = app.settings.warningDistanceMeters.value.toDouble()
        val accuracy = if (location.hasAccuracy()) location.accuracy.toDouble() else null

        tracker.update(location.latitude, location.longitude, accuracy, bans, warningDistance)
            .forEach { Notifier.notifyProximity(this, it) }

        val nearest = ProximityTracker.nearestWarningBoundaryMeters(
            location.latitude, location.longitude, bans, warningDistance,
        )
        requestUpdates(TrackingCadence.forDistance(nearest))
    }

    @SuppressLint("MissingPermission") // checked in onStartCommand
    private fun requestUpdates(newCadence: TrackingCadence) {
        if (newCadence == cadence) return
        cadence = newCadence
        val priority = if (newCadence.highAccuracy) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        val request = LocationRequest.Builder(priority, newCadence.intervalMs)
            .setMinUpdateIntervalMillis(newCadence.intervalMs / 2)
            .setMinUpdateDistanceMeters(newCadence.minDistanceMeters)
            .build()
        // Re-registering the same callback replaces the previous request.
        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    override fun onDestroy() {
        fusedClient.removeLocationUpdates(callback)
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.banfromentering.app.action.STOP_TRACKING"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, LocationTrackingService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LocationTrackingService::class.java))
        }
    }
}
