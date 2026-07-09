package org.krug.app.core.checkin

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import timber.log.Timber

@Singleton
class CheckInRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    private fun events(circleId: String) =
        firestore.collection("circles").document(circleId).collection("checkIns")

    /**
     * Broadcast check-in u sve krugove. Ako write u jedan krug faila, ostali prolaze
     * (partial delivery je bolji od potpunog fail-a — user vidi „poslato" i ne pokušava
     * ponovo, a drugi članovi tog kruga ionako nemaju kontekst).
     */
    suspend fun logCheckIn(
        circleIds: List<String>,
        userId: String,
        userName: String,
        lat: Double,
        lng: Double,
        placeLabel: String,
    ) {
        if (circleIds.isEmpty()) return
        val data = mapOf(
            "userId" to userId,
            "userName" to userName,
            "lat" to lat,
            "lng" to lng,
            "placeLabel" to placeLabel,
            "timestamp" to FieldValue.serverTimestamp(),
        )
        circleIds.forEach { cid ->
            runCatching { events(cid).add(data).await() }
                .onFailure { Timber.w(it, "CheckInRepository: log failed circleId=%s", cid) }
        }
        Timber.i(
            "CheckIn logged uid=%s label=%s circles=%d",
            userId, placeLabel.ifBlank { "(none)" }, circleIds.size,
        )
    }

    fun observeRecentEvents(circleId: String, limit: Long = 20): Flow<List<CheckInEventModel>> =
        callbackFlow {
            val reg = events(circleId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit)
                .addSnapshotListener { snap, error ->
                    if (error != null) {
                        Timber.w(error, "observeCheckIns error circleId=%s", circleId)
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    val list = snap?.documents.orEmpty().mapNotNull { d ->
                        d.toObject(CheckInEventModel::class.java)?.copy(id = d.id)
                    }
                    trySend(list)
                }
            awaitClose { reg.remove() }
        }
}
