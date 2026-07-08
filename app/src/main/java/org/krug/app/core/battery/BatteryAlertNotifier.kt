package org.krug.app.core.battery

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
 * Lokalne notifikacije za "član kruga ima nisku bateriju". Ovo NIJE alarm-level kao SOS,
 * već ambient signal — DEFAULT importance, bez alarm sound-a, poštuje silent hours i
 * DND settings.
 *
 * Trigger point (LocationTrackingService.observeCircleBattery): kad batteryPct člana
 * padne ispod 20% i nije na punjaču. Rate-limit 12h per member pomoću
 * `LocalPrefs.loadBatteryAlerted/saveBatteryAlerted`.
 */
@Singleton
class BatteryAlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun ensureChannel() {
        val mgr = NotificationManagerCompat.from(context)
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannelCompat.Builder(
            CHANNEL_ID,
            NotificationManager.IMPORTANCE_DEFAULT,
        )
            .setName(context.getString(R.string.battery_alert_channel))
            .setDescription(context.getString(R.string.battery_alert_channel_desc))
            .setVibrationEnabled(false)
            .setShowBadge(true)
            .build()
        mgr.createNotificationChannel(channel)
    }

    fun notifyLowBattery(uid: String, displayName: String, batteryPct: Int) {
        ensureChannel()
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_FOCUS_MEMBER_UID, uid)
        }
        val pi = PendingIntent.getActivity(
            context, uid.hashCode(), openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val resolvedName = displayName.ifBlank {
            context.getString(R.string.battery_alert_unknown_sender)
        }
        val title = context.getString(R.string.battery_alert_title, resolvedName, batteryPct)
        val body = context.getString(R.string.battery_alert_body)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setColor(0xFFF59E0B.toInt())
            .setColorized(true)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching {
            @Suppress("MissingPermission")
            NotificationManagerCompat.from(context).notify(notificationIdFor(uid), notification)
            Timber.d("battery alert posted for $uid ($resolvedName, $batteryPct%)")
        }.onFailure { Timber.w(it, "battery alert failed") }
    }

    fun cancel(uid: String) {
        NotificationManagerCompat.from(context).cancel(notificationIdFor(uid))
    }

    private fun notificationIdFor(uid: String): Int =
        NOTIFICATION_BASE_ID + (uid.hashCode() and 0x7FFF)

    companion object {
        const val CHANNEL_ID = "krug_battery_alerts"
        const val EXTRA_FOCUS_MEMBER_UID = "krug_focus_battery_uid"
        private const val NOTIFICATION_BASE_ID = 3_000
    }
}
