package org.krug.app.core.sos

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.krug.app.MainActivity
import org.krug.app.R

/**
 * Lokalne SOS notifikacije — radi dok je FGS živ. Bez Cloud Functions / FCM-a
 * (Spark plan). Kad LocationTrackingService observe-uje novi SOS od člana krug-a,
 * fire-uje notifikaciju sa vibration + alarm sound.
 */
@Singleton
class SosNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun ensureChannel() {
        val mgr = NotificationManagerCompat.from(context)
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val channel = NotificationChannelCompat.Builder(
            CHANNEL_ID,
            NotificationManager.IMPORTANCE_HIGH,
        )
            .setName(context.getString(R.string.sos_notif_channel))
            .setDescription(context.getString(R.string.sos_notif_channel_desc))
            .setVibrationEnabled(true)
            .setVibrationPattern(longArrayOf(0, 500, 200, 500, 200, 500))
            .setSound(soundUri, attrs)
            .setShowBadge(true)
            .build()
        mgr.createNotificationChannel(channel)
    }

    fun notifySos(uid: String, displayName: String) {
        ensureChannel()
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, uid.hashCode(), openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = context.getString(R.string.sos_notif_title, displayName.ifBlank { "Član" })
        val body = context.getString(R.string.sos_notif_body)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setColor(0xFFDC2626.toInt())
            .setColorized(true)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setOngoing(false)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(notificationIdFor(uid), notification)
        }
    }

    fun cancelSos(uid: String) {
        NotificationManagerCompat.from(context).cancel(notificationIdFor(uid))
    }

    private fun notificationIdFor(uid: String): Int =
        SOS_NOTIFICATION_BASE_ID + (uid.hashCode() and 0x7FFF)

    companion object {
        const val CHANNEL_ID = "krug_sos"
        private const val SOS_NOTIFICATION_BASE_ID = 2_000
    }
}
