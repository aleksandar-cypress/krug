package org.krug.app.core.places

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

/**
 * Lokalna notifikacija kad neko iz kruga uđe/izađe iz place-a.
 * Nižeg prioriteta od SOS-a (DEFAULT, ne HIGH), nema alarm sound-a.
 */
@Singleton
class PlaceEventNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun ensureChannel() {
        val mgr = NotificationManagerCompat.from(context)
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        // Notification sound: default ringtone (nije alarm kao SOS — Places je informativno).
        // Bez explicit sound-a, LG i neki Xiaomi ROM-ovi ne emituju zvuk uopšte za IMPORTANCE_DEFAULT.
        val soundUri = android.media.RingtoneManager.getDefaultUri(
            android.media.RingtoneManager.TYPE_NOTIFICATION,
        )
        val attrs = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val channel = NotificationChannelCompat.Builder(
            CHANNEL_ID,
            NotificationManager.IMPORTANCE_DEFAULT,
        )
            .setName(context.getString(R.string.places_notif_channel))
            .setDescription(context.getString(R.string.places_notif_channel_desc))
            .setSound(soundUri, attrs)
            .setVibrationEnabled(true)
            .setVibrationPattern(longArrayOf(0, 200, 100, 200))
            .setShowBadge(false)
            .build()
        mgr.createNotificationChannel(channel)
    }

    fun notifyEvent(event: PlaceEventModel, circleId: String? = null) {
        ensureChannel()
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_FOCUS_PLACE_ID, event.placeId)
            if (circleId != null) putExtra(EXTRA_FOCUS_CIRCLE_ID, circleId)
        }
        val pi = PendingIntent.getActivity(
            context, event.id.hashCode(), openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val userName = event.userName.ifBlank { context.getString(R.string.places_notif_unknown_user) }
        val bodyRes = if (event.type == PlaceEventModel.TYPE_ENTER) {
            R.string.places_notif_body_enter
        } else {
            R.string.places_notif_body_exit
        }
        val body = context.getString(bodyRes, userName, event.placeName)
        // ic_notification je bela silueta na transparent — Android system status bar
        // ignoriše boju i koristi samo alpha. ic_launcher_foreground (koji je bio ovde)
        // je obojena launcher ikonica pa je sistem prikazivao kao sivi kvadratić.
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.places_notif_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .addAction(
                R.drawable.ic_notification,
                context.getString(R.string.places_notif_action_view),
                pi,
            )
            .build()
        NotificationManagerCompat.from(context).notify(event.id.hashCode(), notif)
    }

    companion object {
        const val CHANNEL_ID = "places_events"
        const val EXTRA_FOCUS_PLACE_ID = "focus_place_id"
        const val EXTRA_FOCUS_CIRCLE_ID = "focus_circle_id"
    }
}
