package org.krug.app.core.speeding

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
class SpeedingRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    private fun events(circleId: String) =
        firestore.collection("circles").document(circleId).collection("speedingEvents")

    /**
     * Piše u sve krugove — svaki krug ima svoj feed. Ako write u jedan krug faila
     * (npr. rules), ostali prolaze. Cost: user u 3 kruga → 3 write-a po event-u.
     * Speeding event-i su retki (procena <10/dan po heavy driveru) pa je prihvatljivo.
     */
    suspend fun logEvent(
        circleIds: List<String>,
        userId: String,
        userName: String,
        maxSpeedKmh: Int,
        thresholdKmh: Int,
        durationSec: Int,
        lat: Double,
        lng: Double,
    ) {
        if (circleIds.isEmpty()) return
        val data = mapOf(
            "userId" to userId,
            "userName" to userName,
            "maxSpeedKmh" to maxSpeedKmh,
            "thresholdKmh" to thresholdKmh,
            "durationSec" to durationSec,
            "lat" to lat,
            "lng" to lng,
            "timestamp" to FieldValue.serverTimestamp(),
        )
        circleIds.forEach { cid ->
            runCatching { events(cid).add(data).await() }
                .onFailure { Timber.w(it, "SpeedingRepository: log failed circleId=%s", cid) }
        }
        Timber.i(
            "SpeedingEvent logged uid=%s max=%d thr=%d dur=%d circles=%d",
            userId, maxSpeedKmh, thresholdKmh, durationSec, circleIds.size,
        )
    }

    fun observeRecentEvents(circleId: String, limit: Long = 20): Flow<List<SpeedingEventModel>> =
        callbackFlow {
            val reg = events(circleId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit)
                .addSnapshotListener { snap, error ->
                    if (error != null) {
                        Timber.w(error, "observeRecentEvents error circleId=%s", circleId)
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    val list = snap?.documents.orEmpty().mapNotNull { d ->
                        d.toObject(SpeedingEventModel::class.java)?.copy(id = d.id)
                    }
                    trySend(list)
                }
            awaitClose { reg.remove() }
        }
}
