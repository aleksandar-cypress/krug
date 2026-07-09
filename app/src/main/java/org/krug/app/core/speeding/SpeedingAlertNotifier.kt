package org.krug.app.core.speeding

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.krug.app.MainActivity
import org.krug.app.R
import timber.log.Timber

/**
 * Lokalna notifikacija kad član kruga prekorači svoj speeding threshold.
 * DEFAULT importance — nije alarm kao SOS, informativno je i poštuje silent hours
 * (LocationTrackingService filter, isti kao Place event-i i Battery alerts).
 */
@Singleton
class SpeedingAlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun ensureChannel() {
        val mgr = NotificationManagerCompat.from(context)
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannelCompat.Builder(
            CHANNEL_ID,
            NotificationManager.IMPORTANCE_DEFAULT,
        )
            .setName(context.getString(R.string.speeding_alert_channel))
            .setDescription(context.getString(R.string.speeding_alert_channel_desc))
            .setVibrationEnabled(true)
            .setShowBadge(true)
            .build()
        mgr.createNotificationChannel(channel)
    }

    fun notifySpeeding(event: SpeedingEventModel) {
        ensureChannel()
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, event.id.hashCode(), openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val name = event.userName.ifBlank {
            context.getString(R.string.speeding_alert_unknown_sender)
        }
        val title = context.getString(R.string.speeding_alert_title, name, event.maxSpeedKmh)
        val body = context.getString(R.string.speeding_alert_body, event.thresholdKmh)
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setColor(0xFFEF4444.toInt())
            .setColorized(true)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching {
            @Suppress("MissingPermission")
            NotificationManagerCompat.from(context).notify(event.id.hashCode(), notif)
        }.onFailure { Timber.w(it, "speeding alert failed") }
    }

    companion object {
        const val CHANNEL_ID = "krug_speeding_alerts"
    }
}
