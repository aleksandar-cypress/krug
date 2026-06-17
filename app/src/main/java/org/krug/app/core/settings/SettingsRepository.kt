package org.krug.app.core.settings

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

@Singleton
class SettingsRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    private fun docFor(uid: String): DocumentReference =
        firestore.collection("users").document(uid).collection("settings").document("main")

    fun observe(uid: String): Flow<UserSettings> = callbackFlow {
        val reg = docFor(uid).addSnapshotListener { snap, error ->
            if (error != null) {
                trySend(UserSettings())
                return@addSnapshotListener
            }
            val data = snap?.data
            trySend(parse(data))
        }
        awaitClose { reg.remove() }
    }

    suspend fun updateBatteryMode(uid: String, mode: BatteryMode) {
        docFor(uid).set(mapOf("batteryMode" to mode.name), SetOptions.merge()).await()
    }

    suspend fun updateHybridThreshold(uid: String, pct: Int) {
        val clamped = pct.coerceIn(UserSettings.MIN_THRESHOLD, UserSettings.MAX_THRESHOLD)
        docFor(uid).set(mapOf("hybridThresholdPct" to clamped), SetOptions.merge()).await()
    }

    suspend fun updateShareGlobal(uid: String, share: Boolean) {
        docFor(uid).set(mapOf("shareLocationGlobal" to share), SetOptions.merge()).await()
    }

    private fun parse(data: Map<String, Any?>?): UserSettings {
        if (data == null) return UserSettings()
        return UserSettings(
            batteryMode = runCatching {
                BatteryMode.valueOf(data["batteryMode"] as? String ?: BatteryMode.HYBRID.name)
            }.getOrDefault(BatteryMode.HYBRID),
            hybridThresholdPct = (data["hybridThresholdPct"] as? Number)?.toInt() ?: 15,
            shareLocationGlobal = data["shareLocationGlobal"] as? Boolean ?: true,
            notificationsEnabled = data["notificationsEnabled"] as? Boolean ?: true,
            language = data["language"] as? String ?: "sr",
        )
    }
}
