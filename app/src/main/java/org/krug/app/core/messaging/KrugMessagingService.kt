package org.krug.app.core.messaging

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.krug.app.core.location.LocationTrackingService
import org.krug.app.core.user.UserRepository
import timber.log.Timber

/**
 * FCM entry point. Dva glavna zadatka:
 *
 *  1. **onNewToken** — kad Firebase generiše svež FCM token (fresh install, app data cleared,
 *     token rotation koji Firebase periodično radi), upišemo ga u Firestore
 *     `users/{uid}.fcmToken`. Cloud Functions ga čita da bi znala na koji device da šalje push.
 *
 *  2. **onMessageReceived** — kad Cloud Functions pošalje push data message tipa "refresh",
 *     forsiramo FGS one-shot fresh GPS fix. Ovo rešava fundamentalni problem "Slobodan
 *     ispada offline":
 *     - Aleksandar klikne Refresh za Slobodana
 *     - Krug piše u `locationRequests/{slobodanUid}/{aleksandarUid}` (RTDB)
 *     - Cloud Function trigger fajruje, čita Slobodanov FCM token, šalje push data message
 *     - Slobodanov telefon (čak i u Doze) primi push kao high-priority → probudi Krug proces
 *     - onMessageReceived pozove LocationTrackingService.refreshSelf → fresh GPS fix
 *     - Aleksandar odmah vidi Slobodana na svežoj poziciji
 *
 *     Bez push mehanizma, RTDB write ka locationRequests-u može biti "silent" — Slobodanov
 *     FGS je moguće suspend-ovan (Doze), RTDB listener queue-ovan dok se telefon ne
 *     probudi. Push zaobilazi Doze restrikcije jer je Google-owned service.
 *
 * High-priority FCM messages su ograničeni na ~1 per 10min (Google throttle). Ne šaljemo
 * push za automatske ping-ove; samo user-inicijalizovan Refresh + SOS trigger + kritični
 * evente ako budu implementirani.
 */
@AndroidEntryPoint
class KrugMessagingService : FirebaseMessagingService() {

    @Inject lateinit var userRepository: UserRepository
    @Inject lateinit var firebaseAuth: FirebaseAuth
    @Inject lateinit var deviceRegistry: org.krug.app.core.device.DeviceRegistry

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        Timber.i("KrugMessagingService: onNewToken (len=%d)", token.length)
        val uid = firebaseAuth.currentUser?.uid
        if (uid == null) {
            // User nije signed-in — token će biti upisan na sledeći sign-in
            // (AuthRepository forsira FcmTokenSyncer.refresh() posle sign-in).
            Timber.d("onNewToken: no auth user, deferring upload")
            return
        }
        scope.launch {
            // Legacy fcmToken na user doc-u (single-device fallback za Cloud Function pre
            // multi-device migracije) + novi per-device zapis. Cloud Function čita oba,
            // preferira devices subcollection ako postoji.
            runCatching { userRepository.updateFcmToken(uid, token) }
                .onSuccess { Timber.i("FCM token uploaded to Firestore for uid=%s", uid) }
                .onFailure { Timber.w(it, "onNewToken upload failed") }
            val label = android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercaseChar() } +
                " " + android.os.Build.MODEL
            deviceRegistry.registerDevice(uid, token, label)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val type = message.data["type"]
        Timber.i(
            "KrugMessagingService: onMessageReceived type=%s from=%s data=%s",
            type, message.from, message.data,
        )
        when (type) {
            TYPE_REFRESH -> {
                // Cloud Functions je forwardovao Aleksandar-ov ping. Probudi FGS i forsiraj
                // sveži GPS fix — high-priority FCM je već probudio proces, sad samo trigger
                // one-shot HIGH_ACCURACY (isto što bi user tap "Osveži" radio na sopstvenom
                // telefonu). Zahtev je user-initiated (drugi user) pa preskače cooldown.
                LocationTrackingService.refreshSelf(applicationContext)
            }
            else -> {
                Timber.d("Unknown FCM data type: %s", type)
            }
        }
    }

    companion object {
        const val TYPE_REFRESH = "refresh"
    }
}
