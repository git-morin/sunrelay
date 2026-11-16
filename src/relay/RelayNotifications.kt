package dev.morin.sunrelay.relay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.morin.sunrelay.MainActivity
import dev.morin.sunrelay.R

internal class RelayNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java).apply {
        createNotificationChannel(NotificationChannel(CHANNEL, "Relay", NotificationManager.IMPORTANCE_LOW))
    }

    fun build(text: String): Notification = Notification.Builder(context, CHANNEL)
        .setSmallIcon(R.drawable.ic_relay)
        .setContentTitle("SunRelay running")
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(openAppIntent())
        .addAction(Notification.Action.Builder(null, "Stop", stopIntent()).build())
        .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        .build()

    fun update(text: String) = manager.notify(ID, build(text))

    private fun openAppIntent() = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
    )

    private fun stopIntent() = PendingIntent.getService(
        context, 1, RelayService.stopIntent(context), PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ID = 1
        private const val CHANNEL = "relay"
    }
}
