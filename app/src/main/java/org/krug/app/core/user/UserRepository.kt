package org.krug.app.core.user

import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.krug.app.core.util.DeviceNames
import timber.log.Timber

@Singleton
class UserRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    private fun userDoc(uid: String) = firestore.collection("users").document(uid)

    suspend fun upsertOnSignIn(user: FirebaseUser, deviceLabel: String) {
        val ref = userDoc(user.uid)
        // Sačuvaj postojeće ime ako ga je user već postavio (nickname).
        val existingName = runCatching {
            ref.get().await().getString("displayName")
        }.getOrNull().orEmpty()

        // Fallback name iz device-a koristi FRIENDLY oblik ("Galaxy A37 5G" umesto
        // sirovog "SM-A376B") da bi novi anonimni user-i imali čitljivo ime odmah.
        val computedName = user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore('@')?.takeIf { it.isNotBlank() }
            ?: DeviceNames.friendly(deviceLabel)
        val finalName = existingName.ifBlank { computedName }

        val data = mapOf(
            "uid" to user.uid,
            "displayName" to finalName,
            "email" to (user.email.orEmpty()),
            "photoUrl" to user.photoUrl?.toString(),
            "deviceModel" to deviceLabel,
            "lastSeenAt" to FieldValue.serverTimestamp(),
            // createdAt set only if doc is new (merge=true preserves existing).
            "createdAt" to FieldValue.serverTimestamp(),
        )
        ref.set(data, SetOptions.merge()).await()
    }

    suspend fun markOnboardingCompleted(uid: String) {
        userDoc(uid).set(mapOf("onboardingCompleted" to true), SetOptions.merge()).await()
    }

    suspend fun updateFcmToken(uid: String, token: String) {
        userDoc(uid).set(mapOf("fcmToken" to token), SetOptions.merge()).await()
    }

    /**
     * Validacija na nivou repository-ja (defense-in-depth): trim + max length 40 + odbij
     * blank. Bez ovog, ako neki budući caller propusti validaciju, mogli bismo da
     * upišemo prazan/whitespace-only display name u Firestore. Trim u repo garantuje da
     * UI prikazi i SOS payload-ovi nikad nemaju leading/trailing space.
     */
    suspend fun updateDisplayName(uid: String, name: String) {
        val cleaned = name.trim().take(MAX_DISPLAY_NAME_LENGTH)
        if (cleaned.isBlank()) {
            Timber.w("updateDisplayName: rejected blank name for $uid")
            return
        }
        userDoc(uid).set(mapOf("displayName" to cleaned), SetOptions.merge()).await()
    }

    private companion object {
        const val MAX_DISPLAY_NAME_LENGTH = 40
    }

    fun observeUser(uid: String): Flow<UserModel?> = callbackFlow {
        val reg = userDoc(uid).addSnapshotListener { snap, error ->
            if (error != null) {
                Timber.w(error, "observeUser error for $uid")
                trySend(null)
                return@addSnapshotListener
            }
            trySend(snap?.toObject(UserModel::class.java))
        }
        awaitClose { reg.remove() }
    }

    /**
     * GDPR — obriši sve user subcolection-e + user doc.
     *
     * Cleanup targets (per firestore.rules /users/{uid}/... subcollection-e):
     *  - settings — per-user prefs
     *  - locationHistory — 30d GPS point-i
     *  - trips — driving reports (v1.2.0)
     *  - devices — multi-device FCM tokens (v1.2.0). Bez ovog, tokeni obrisanog
     *    user-a ostaju u Firestore i troše storage; Cloud Function-i mogu i dalje
     *    da fetch-uju stale tokene ako bi neko slao push „unatrag" (obično nema
     *    caller-a jer smo ukloni-li user-a iz svih krugova, ali GDPR incomplete).
     */
    suspend fun deleteUser(uid: String) {
        runCatching {
            userDoc(uid).collection("settings").get().await().documents.forEach { d ->
                runCatching { d.reference.delete().await() }
            }
        }
        deleteSubcollectionInBatches(uid, "locationHistory")
        deleteSubcollectionInBatches(uid, "trips")
        deleteSubcollectionInBatches(uid, "devices")
        runCatching { userDoc(uid).delete().await() }
            .onFailure { Timber.w(it, "deleteUser doc failed for $uid") }
    }

    private suspend fun deleteSubcollectionInBatches(uid: String, name: String) {
        runCatching {
            val col = userDoc(uid).collection(name)
            var deleted = 0
            while (true) {
                val batch = col.limit(500).get().await().documents
                if (batch.isEmpty()) break
                batch.forEach { runCatching { it.reference.delete().await() } }
                deleted += batch.size
                if (batch.size < 500) break
            }
            if (deleted > 0) Timber.i("deleteUser: cleaned %d %s docs for %s", deleted, name, uid)
        }.onFailure { Timber.w(it, "deleteUser $name cleanup failed for $uid") }
    }
}
