package org.krug.app.core.checkin

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
 * Lokalna notifikacija kad neko iz kruga pritisne „Stigao/la sam". Informativno je,
 * ne alarm-level: DEFAULT importance, poštuje silent hours.
 */
@Singleton
class CheckInNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun ensureChannel() {
        val mgr = NotificationManagerCompat.from(context)
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannelCompat.Builder(
            CHANNEL_ID,
            NotificationManager.IMPORTANCE_DEFAULT,
        )
            .setName(context.getString(R.string.checkin_channel))
            .setDescription(context.getString(R.string.checkin_channel_desc))
            .setVibrationEnabled(true)
            .setShowBadge(true)
            .build()
        mgr.createNotificationChannel(channel)
    }

    fun notifyCheckIn(event: CheckInEventModel) {
        ensureChannel()
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, event.id.hashCode(), openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = context.getString(R.string.checkin_notif_title, event.userName)
        val body = if (event.placeLabel.isNotBlank()) {
            context.getString(R.string.checkin_notif_body_with_place, event.placeLabel)
        } else {
            context.getString(R.string.checkin_notif_body_no_place)
        }
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setColor(0xFF10B981.toInt())
            .setColorized(true)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching {
            @Suppress("MissingPermission")
            NotificationManagerCompat.from(context).notify(NOTIF_TAG, event.id.hashCode(), notif)
        }.onFailure { Timber.w(it, "checkin notif failed") }
    }

    companion object {
        const val CHANNEL_ID = "krug_checkins"
        // Vidi SosNotifier o notif tag pattern-u.
        private const val NOTIF_TAG = "krug_checkin"
    }
}
