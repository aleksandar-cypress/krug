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

        val computedName = when {
            user.displayName.orEmpty().isNotBlank() -> user.displayName!!
            user.email.orEmpty().isNotBlank() -> user.email!!.substringBefore('@')
            else -> deviceLabel
        }
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

    suspend fun updateDisplayName(uid: String, name: String) {
        userDoc(uid).set(mapOf("displayName" to name), SetOptions.merge()).await()
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

    /** GDPR — obriši settings subcollection + user doc. */
    suspend fun deleteUser(uid: String) {
        // Settings subcollection (samo `main` doc, ali general-purpose za buduće dokumente).
        runCatching {
            userDoc(uid).collection("settings").get().await().documents.forEach { d ->
                runCatching { d.reference.delete().await() }
            }
        }
        runCatching { userDoc(uid).delete().await() }
            .onFailure { Timber.w(it, "deleteUser doc failed for $uid") }
    }
}
