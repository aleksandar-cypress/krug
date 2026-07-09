package org.krug.app.core.eta

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
 * Notifikacije za ETA share event-e:
 *  - „na putu" (kad neko iz kruga pokrene share)
 *  - „stigao/la" (kad user pređe arrival radius destinacije)
 * DEFAULT importance, poštuje silent hours (LocationTrackingService filter).
 */
@Singleton
class EtaNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun ensureChannel() {
        val mgr = NotificationManagerCompat.from(context)
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannelCompat.Builder(
            CHANNEL_ID,
            NotificationManager.IMPORTANCE_DEFAULT,
        )
            .setName(context.getString(R.string.eta_channel))
            .setDescription(context.getString(R.string.eta_channel_desc))
            .setVibrationEnabled(true)
            .setShowBadge(true)
            .build()
        mgr.createNotificationChannel(channel)
    }

    fun notifyStarted(share: EtaShareModel) {
        ensureChannel()
        val pi = openIntent(share.id.hashCode())
        val title = context.getString(R.string.eta_notif_started_title, share.userName)
        val body = context.getString(
            R.string.eta_notif_started_body,
            humanizeEta(share.etaMinutes),
            share.destinationLabel.ifBlank { "—" },
        )
        val notif = build(title, body, pi, notifIdStarted(share.userId))
        NotificationManagerCompat.from(context).let { mgr ->
            runCatching {
                @Suppress("MissingPermission")
                mgr.notify(notifIdStarted(share.userId), notif)
            }.onFailure { Timber.w(it, "eta started notif failed") }
        }
    }

    fun notifyArrived(share: EtaShareModel) {
        ensureChannel()
        val pi = openIntent(share.id.hashCode() + 1)
        val title = context.getString(R.string.eta_notif_arrived_title, share.userName)
        val body = context.getString(
            R.string.eta_notif_arrived_body,
            share.destinationLabel.ifBlank { "—" },
        )
        val notif = build(title, body, pi, notifIdArrived(share.userId))
        // Poništi „na putu" notif kad stigne — clutter cleanup.
        NotificationManagerCompat.from(context).cancel(notifIdStarted(share.userId))
        runCatching {
            @Suppress("MissingPermission")
            NotificationManagerCompat.from(context).notify(notifIdArrived(share.userId), notif)
        }.onFailure { Timber.w(it, "eta arrived notif failed") }
    }

    private fun openIntent(requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun build(title: String, body: String, pi: PendingIntent, id: Int) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setColor(0xFF3B82F6.toInt())
            .setColorized(true)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

    private fun humanizeEta(minutes: Int): String = when {
        minutes < 1 -> context.getString(R.string.eta_share_arrived)
        minutes < 60 -> "$minutes min"
        else -> {
            val h = minutes / 60
            val m = minutes % 60
            if (m == 0) "${h}h" else "${h}h ${m}m"
        }
    }

    private fun notifIdStarted(uid: String): Int = 4_000 + (uid.hashCode() and 0x7FFF)
    private fun notifIdArrived(uid: String): Int = 5_000 + (uid.hashCode() and 0x7FFF)

    companion object {
        const val CHANNEL_ID = "krug_eta_shares"
    }
}
