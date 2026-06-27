package org.krug.app.core.sos

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.krug.app.MainActivity
import org.krug.app.R
import timber.log.Timber

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

    fun notifySos(uid: String, displayName: String, circleName: String? = null) {
        ensureChannel()
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // Deep-link payload — MapScreen čita ovaj extra preko Intent.consumeSosFocusUid()
            // i `flyTo(member.location)` + otvara MemberDetailSheet sa SOS pin-om u fokusu.
            putExtra(EXTRA_FOCUS_SOS_UID, uid)
        }
        val pi = PendingIntent.getActivity(
            context, uid.hashCode(), openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val resolvedName = displayName.ifBlank { context.getString(R.string.sos_notif_unknown_sender) }
        val title = context.getString(R.string.sos_notif_title, resolvedName)
        // Bogatiji body kad imamo ime kruga — "Iz kruga „Porodica"…". Bez kruga padamo
        // na generic poruku iz strings.xml.
        val body = if (!circleName.isNullOrBlank()) {
            context.getString(R.string.sos_notif_body_with_circle, circleName)
        } else {
            context.getString(R.string.sos_notif_body)
        }
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        // setLargeIcon namerno NIJE postavljen — Samsung One UI prikazuje launcher icon
        // levo. Druga large ikona desno je pravila redundantni duplikat.
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setColor(0xFFDC2626.toInt())
            .setColorized(true)
            .setAutoCancel(true)
            .setContentIntent(pi)
            // FullScreenIntent budi ekran kada je telefon zaključan — kritično za SOS.
            // Kombinacija sa MainActivity android:showWhenLocked + android:turnScreenOn.
            // CATEGORY_ALARM nam daje USE_FULL_SCREEN_INTENT permission auto-grant na A14+.
            .setFullScreenIntent(pi, true)
            .setOngoing(false)
            // Belt-and-suspenders za pre-O — na O+ channel je autoritativan, ali ne škodi.
            // Na O+ Samsung One UI ipak može da silence-uje sve, pa imamo i direktan
            // Vibrator poziv ispod.
            .setSound(soundUri)
            .setVibrate(VIBRATION_PATTERN)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(notificationIdFor(uid), notification)
            Timber.d("notifySos posted for $uid")
        }.onFailure { Timber.w(it, "notifySos failed") }
        // Direktan Vibrator poziv — radi i ako je channel/notification silent
        // (Samsung One UI "Silent category" za sideload debug APK-ove).
        triggerVibration()
    }

    private fun triggerVibration() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val mgr = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            mgr?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator == null || !vibrator.hasVibrator()) return
        runCatching {
            val effect = VibrationEffect.createWaveform(VIBRATION_PATTERN, -1)
            vibrator.vibrate(effect)
        }.onFailure { Timber.w(it, "Vibration failed") }
    }

    fun cancelSos(uid: String) {
        NotificationManagerCompat.from(context).cancel(notificationIdFor(uid))
    }

    private fun notificationIdFor(uid: String): Int =
        SOS_NOTIFICATION_BASE_ID + (uid.hashCode() and 0x7FFF)

    companion object {
        // Bumpovan ID — channel settings se ne mogu menjati posle prvog kreiranja, pa
        // moramo da pravimo novi channel kad menjamo importance/sound. v2 = HIGH + alarm.
        const val CHANNEL_ID = "krug_sos_v2"
        const val EXTRA_FOCUS_SOS_UID = "krug_focus_sos_uid"
        private const val SOS_NOTIFICATION_BASE_ID = 2_000
        private val VIBRATION_PATTERN = longArrayOf(0, 500, 200, 500, 200, 500)
    }
}
