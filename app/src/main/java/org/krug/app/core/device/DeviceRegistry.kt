package org.krug.app.core.device

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.krug.app.core.prefs.LocalPrefs
import timber.log.Timber

/**
 * Device registry — omogućava jednom Firebase user-u da se koristi na više uređaja
 * (telefon + tablet). Svaki uređaj registruje sebe u `users/{uid}/devices/{deviceId}`
 * subcollection sa svojim FCM tokenom i deviceModel-om. Cloud Function-i mogu čitati
 * sve tokene da bi fanout-ovali push notifikacije, umesto da imaju samo jedan token
 * na user doc-u (koji poslednji sign-in overwrite-uje).
 *
 * Publish gate: samo najskorije aktivan uređaj publish-uje lokaciju. `lastActiveMs`
 * timestamp se update-uje na svaki heartbeat iz FGS-a; drugi uređaji vide taj timestamp
 * kroz observe i skipuju publish ako nisu „primary". Bez ovog, dva uređaja bi
 * naizmenično prepisivala lokaciju u RTDB → peers bi videli jitter (kuća ↔ posao).
 */
@Singleton
class DeviceRegistry @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val localPrefs: LocalPrefs,
) {
    val deviceId: String get() = localPrefs.deviceId

    private fun devicesCol(uid: String) =
        firestore.collection("users").document(uid).collection("devices")

    /**
     * Register/upsert ovaj device-a u user-ov registry. Poziva se posle svakog sign-in-a
     * i pri svakom FCM token refresh-u (KrugMessagingService.onNewToken). Idempotentno.
     */
    suspend fun registerDevice(uid: String, fcmToken: String, deviceLabel: String) {
        val data = mapOf(
            "deviceId" to deviceId,
            "fcmToken" to fcmToken,
            "deviceModel" to deviceLabel,
            "lastActiveMs" to System.currentTimeMillis(),
            "registeredAt" to FieldValue.serverTimestamp(),
        )
        runCatching {
            devicesCol(uid).document(deviceId).set(data, SetOptions.merge()).await()
        }.onFailure { Timber.w(it, "DeviceRegistry: register failed uid=%s dev=%s", uid, deviceId) }
    }

    /**
     * Update samo lastActiveMs — poziva se iz FGS heartbeat loop-a (svakih ~60s).
     * Ne dirati fcmToken/deviceModel (registerDevice ih setuje). Lightweight write.
     */
    suspend fun heartbeat(uid: String) {
        val data = mapOf(
            "lastActiveMs" to System.currentTimeMillis(),
        )
        runCatching {
            devicesCol(uid).document(deviceId).set(data, SetOptions.merge()).await()
        }.onFailure { Timber.d(it, "DeviceRegistry: heartbeat failed uid=%s", uid) }
    }

    /** Ukloni device iz registry-ja (sign-out ili delete-account cleanup). */
    suspend fun unregisterDevice(uid: String) {
        runCatching {
            devicesCol(uid).document(deviceId).delete().await()
        }.onFailure { Timber.w(it, "DeviceRegistry: unregister failed uid=%s", uid) }
    }

    /**
     * Observe listu svih uređaja user-a. Koristi se u publish gate-u da odredi
     * najskorije aktivan device (primary), i buduće „Moji uređaji" screen-u u Settings-u.
     */
    fun observeDevices(uid: String): Flow<List<DeviceModel>> = callbackFlow {
        val reg = devicesCol(uid).addSnapshotListener { snap, error ->
            if (error != null) {
                Timber.w(error, "DeviceRegistry: observe failed uid=%s", uid)
                trySend(emptyList())
                return@addSnapshotListener
            }
            val list = snap?.documents.orEmpty().mapNotNull { d ->
                d.toObject(DeviceModel::class.java)?.copy(deviceId = d.id)
            }
            trySend(list)
        }
        awaitClose { reg.remove() }
    }
}

/**
 * Snapshot device zapisa u Firestore-u. `lastActiveMs` je milis timestamp (ne
 * @ServerTimestamp) jer koristimo klijentski clock — Firestore serverTimestamp bi
 * dao NULL dok write nije propagirao, a publish gate mora odmah da radi.
 */
data class DeviceModel(
    val deviceId: String = "",
    val fcmToken: String = "",
    val deviceModel: String = "",
    val lastActiveMs: Long = 0L,
)
