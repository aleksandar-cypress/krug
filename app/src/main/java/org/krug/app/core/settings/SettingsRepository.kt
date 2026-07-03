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

    suspend fun updateShareGlobal(uid: String, share: Boolean) {
        docFor(uid).set(mapOf("shareLocationGlobal" to share), SetOptions.merge()).await()
    }

    /**
     * Temporary sharing setter. `untilMs = null` briše field (trajno deljenje).
     * `untilMs = System.currentTimeMillis() + N` znači "deli još N ms pa auto-stop".
     * NE dira shareLocationGlobal — caller poziva zajedno sa updateShareGlobal za
     * kompletan flow (start temp: shareGlobal=true + untilMs=N; expire: shareGlobal=false
     * + untilMs=null).
     */
    suspend fun updatePlaceNotifs(uid: String, enabled: Boolean) {
        docFor(uid).set(mapOf("placeNotifsEnabled" to enabled), SetOptions.merge()).await()
    }

    suspend fun updateShareUntil(uid: String, untilMs: Long?) {
        val payload: Map<String, Any> = if (untilMs == null) {
            // Firestore null-safe brisanje polja — set(null) bi bilo tipska greška.
            mapOf("shareUntilMs" to com.google.firebase.firestore.FieldValue.delete())
        } else {
            mapOf("shareUntilMs" to untilMs)
        }
        docFor(uid).set(payload, SetOptions.merge()).await()
    }

    private fun parse(data: Map<String, Any?>?): UserSettings {
        if (data == null) return UserSettings()
        return UserSettings(
            batteryMode = migrateMode(data["batteryMode"] as? String),
            shareLocationGlobal = data["shareLocationGlobal"] as? Boolean ?: true,
            notificationsEnabled = data["notificationsEnabled"] as? Boolean ?: true,
            language = data["language"] as? String ?: "sr",
            shareUntilMs = (data["shareUntilMs"] as? Number)?.toLong(),
            placeNotifsEnabled = data["placeNotifsEnabled"] as? Boolean ?: true,
        )
    }

    // Migracija sa starih naziva: CONSTANT (uvek HIGH) -> MAX,
    // ADAPTIVE/HYBRID (uglavnom HIGH) -> BALANCED. Default je BALANCED.
    private fun migrateMode(raw: String?): BatteryMode = when (raw) {
        null -> BatteryMode.BALANCED
        "SAVER" -> BatteryMode.SAVER
        "BALANCED" -> BatteryMode.BALANCED
        "MAX" -> BatteryMode.MAX
        "CONSTANT" -> BatteryMode.MAX
        "ADAPTIVE", "HYBRID" -> BatteryMode.BALANCED
        else -> BatteryMode.BALANCED
    }
}
