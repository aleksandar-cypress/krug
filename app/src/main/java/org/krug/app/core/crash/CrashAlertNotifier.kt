package org.krug.app.core.crash

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
import org.krug.app.R
import timber.log.Timber

/**
 * Heads-up notifikacija sa countdown-om posle sumnje na sudar. Prikazuje se sa
 * dva action-a: „Dobro sam" (cancel) i „Pošalji odmah" (skip countdown → SOS trigger).
 * Ako user ne reaguje, LocationTrackingService okida SOS po scheduled delay-u.
 *
 * HIGH priority + full-screen intent alarm channel — mora da probije DND i ekran
 * kako bi user u autu odmah video signal.
 */
@Singleton
class CrashAlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun ensureChannel() {
        val mgr = NotificationManagerCompat.from(context)
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannelCompat.Builder(
            CHANNEL_ID,
            NotificationManager.IMPORTANCE_HIGH,
        )
            .setName(context.getString(R.string.crash_countdown_title))
            .setVibrationEnabled(true)
            .setVibrationPattern(longArrayOf(0, 400, 200, 400, 200, 400))
            .setShowBadge(true)
            .build()
        mgr.createNotificationChannel(channel)
    }

    fun postCountdown(remainingSec: Int) {
        ensureChannel()
        val cancelIntent = Intent(ACTION_CRASH_CANCEL).setPackage(context.packageName)
        val cancelPi = PendingIntent.getBroadcast(
            context, 0, cancelIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val sendIntent = Intent(ACTION_CRASH_SEND_NOW).setPackage(context.packageName)
        val sendPi = PendingIntent.getBroadcast(
            context, 1, sendIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = context.getString(R.string.crash_countdown_title)
        val body = context.getString(R.string.crash_countdown_body, remainingSec)
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setColor(0xFFEF4444.toInt())
            .setColorized(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(
                R.drawable.ic_notification,
                context.getString(R.string.crash_countdown_cancel),
                cancelPi,
            )
            .addAction(
                R.drawable.ic_notification,
                context.getString(R.string.crash_countdown_send_now),
                sendPi,
            )
            .build()
        runCatching {
            @Suppress("MissingPermission")
            NotificationManagerCompat.from(context).notify(NOTIF_ID, notif)
        }.onFailure { Timber.w(it, "crash countdown notif failed") }
    }

    fun cancel() {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID)
    }

    companion object {
        const val CHANNEL_ID = "krug_crash_alerts"
        const val NOTIF_ID = 6_001
        const val ACTION_CRASH_CANCEL = "org.krug.app.CRASH_CANCEL"
        const val ACTION_CRASH_SEND_NOW = "org.krug.app.CRASH_SEND_NOW"
    }
}
