package org.krug.app.core.eta

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import timber.log.Timber

@Singleton
class EtaRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    private fun shares(circleId: String) =
        firestore.collection("circles").document(circleId).collection("etaShares")

    /**
     * Kreira ili nadograđuje aktivan share za usera u svim krugovima. DocId je userId
     * — jedan aktivan share po user-u po krugu (novi start prepisuje stari).
     */
    suspend fun startShare(
        circleIds: List<String>,
        userId: String,
        userName: String,
        destinationLat: Double,
        destinationLng: Double,
        destinationLabel: String,
        currentLat: Double,
        currentLng: Double,
        initialEtaMinutes: Int,
        initialRemainingKm: Double,
    ) {
        if (circleIds.isEmpty()) return
        val data = mapOf(
            "userId" to userId,
            "userName" to userName,
            "destinationLat" to destinationLat,
            "destinationLng" to destinationLng,
            "destinationLabel" to destinationLabel,
            "etaMinutes" to initialEtaMinutes,
            "remainingKm" to initialRemainingKm,
            "currentLat" to currentLat,
            "currentLng" to currentLng,
            "startedAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
            "arrivedAt" to null,
        )
        circleIds.forEach { cid ->
            runCatching { shares(cid).document(userId).set(data).await() }
                .onFailure { Timber.w(it, "EtaRepository: start failed circleId=%s", cid) }
        }
        Timber.i("EtaShare started uid=%s dest=%s circles=%d", userId, destinationLabel, circleIds.size)
    }

    /** Live update za aktivan share — svaki fix (throttled u service-u). */
    suspend fun updateShare(
        circleIds: List<String>,
        userId: String,
        currentLat: Double,
        currentLng: Double,
        etaMinutes: Int,
        remainingKm: Double,
    ) {
        if (circleIds.isEmpty()) return
        val data = mapOf(
            "currentLat" to currentLat,
            "currentLng" to currentLng,
            "etaMinutes" to etaMinutes,
            "remainingKm" to remainingKm,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        circleIds.forEach { cid ->
            runCatching { shares(cid).document(userId).set(data, com.google.firebase.firestore.SetOptions.merge()).await() }
                .onFailure { Timber.w(it, "EtaRepository: update failed circleId=%s", cid) }
        }
    }

    /** Označava dolazak — observers vide arrivedAt i emit-uju „stigao/la" notif. */
    suspend fun markArrived(circleIds: List<String>, userId: String) {
        if (circleIds.isEmpty()) return
        val data = mapOf(
            "arrivedAt" to FieldValue.serverTimestamp(),
            "etaMinutes" to 0,
            "remainingKm" to 0.0,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        circleIds.forEach { cid ->
            runCatching { shares(cid).document(userId).set(data, com.google.firebase.firestore.SetOptions.merge()).await() }
                .onFailure { Timber.w(it, "EtaRepository: arrived mark failed circleId=%s", cid) }
        }
        Timber.i("EtaShare arrived uid=%s circles=%d", userId, circleIds.size)
    }

    /** User eksplicitno prekida share pre nego što stigne — brišemo doc iz svih krugova. */
    suspend fun cancelShare(circleIds: List<String>, userId: String) {
        circleIds.forEach { cid ->
            runCatching { shares(cid).document(userId).delete().await() }
                .onFailure { Timber.w(it, "EtaRepository: cancel failed circleId=%s", cid) }
        }
        Timber.i("EtaShare cancelled uid=%s circles=%d", userId, circleIds.size)
    }

    fun observeActiveShares(circleId: String): Flow<List<EtaShareModel>> = callbackFlow {
        val reg = shares(circleId).addSnapshotListener { snap, error ->
            if (error != null) {
                Timber.w(error, "observeActiveShares error circleId=%s", circleId)
                trySend(emptyList())
                return@addSnapshotListener
            }
            val list = snap?.documents.orEmpty().mapNotNull { d ->
                d.toObject(EtaShareModel::class.java)?.copy(id = d.id)
            }
            trySend(list)
        }
        awaitClose { reg.remove() }
    }

    fun observeMyShare(circleId: String, userId: String): Flow<EtaShareModel?> = callbackFlow {
        val reg = shares(circleId).document(userId).addSnapshotListener { snap, error ->
            if (error != null) {
                Timber.w(error, "observeMyShare error circleId=%s", circleId)
                trySend(null)
                return@addSnapshotListener
            }
            val model = snap?.toObject(EtaShareModel::class.java)?.copy(id = snap.id)
            trySend(model)
        }
        awaitClose { reg.remove() }
    }
}
