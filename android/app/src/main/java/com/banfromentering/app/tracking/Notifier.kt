package com.banfromentering.app.tracking

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.banfromentering.app.R
import com.banfromentering.app.geo.ProximityAlert
import com.banfromentering.app.geo.Zone
import com.banfromentering.app.ui.MainActivity
import kotlin.math.roundToInt

object Notifier {

    private const val CHANNEL_TRACKING = "tracking"
    private const val CHANNEL_ALERTS = "proximity_alerts"
    const val TRACKING_NOTIFICATION_ID = 1
    private const val ALERT_NOTIFICATION_BASE_ID = 1_000

    fun createChannels(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(CHANNEL_TRACKING, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName(context.getString(R.string.channel_tracking_name))
                    .setDescription(context.getString(R.string.channel_tracking_description))
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_ALERTS, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName(context.getString(R.string.channel_alerts_name))
                    .setDescription(context.getString(R.string.channel_alerts_description))
                    .setVibrationEnabled(true)
                    .setVibrationPattern(longArrayOf(0, 400, 200, 400, 200, 800))
                    .build(),
            ),
        )
    }

    fun trackingNotification(context: Context): Notification {
        val stopIntent = PendingIntent.getService(
            context,
            0,
            Intent(context, LocationTrackingService::class.java).setAction(LocationTrackingService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_TRACKING)
            .setSmallIcon(R.drawable.ic_stat_ban)
            .setContentTitle(context.getString(R.string.tracking_notification_title))
            .setContentText(context.getString(R.string.tracking_notification_text))
            .setContentIntent(openAppIntent(context))
            .addAction(0, context.getString(R.string.tracking_notification_stop), stopIntent)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    @SuppressLint("MissingPermission") // checked via areNotificationsEnabled()
    fun notifyProximity(context: Context, alert: ProximityAlert) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val name = alert.ban.name
        val (title, text) = when (alert.zone) {
            Zone.INSIDE -> context.getString(R.string.alert_inside_title, name) to
                context.getString(R.string.alert_inside_text)
            else -> context.getString(R.string.alert_warning_title, name) to
                context.getString(R.string.alert_warning_text, formatDistance(alert.distanceToBoundaryMeters))
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_ban)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()

        // One notification slot per ban, so a later "inside" alert replaces the earlier warning.
        manager.notify(ALERT_NOTIFICATION_BASE_ID + alert.ban.id.toInt(), notification)
    }

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )

    fun formatDistance(meters: Double): String =
        if (meters >= 1_000) "%.1f km".format(meters / 1_000) else "${meters.roundToInt()} m"
}
