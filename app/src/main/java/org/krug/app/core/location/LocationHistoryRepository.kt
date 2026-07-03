package org.krug.app.core.location

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * Location history — Firestore /users/{uid}/locationHistory/{pointId} subcolection.
 * TTL policy (podesiti u Firebase Console) briše dokumente starije od 30 dana.
 *
 * Ne piše se svaki fix — filter: značajno pomeranje (>25m) ILI prošlo >10min od poslednjeg
 * zapisa. Bez toga: ~4300 write/day/user (svakih 20s) što bi Firestore free tier srušio.
 * Sa filterom: ~50-200 write/day/user.
 */
@Singleton
class LocationHistoryRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    private fun historyCol(uid: String) =
        firestore.collection("users").document(uid).collection("locationHistory")

    suspend fun writePoint(
        uid: String,
        lat: Double,
        lng: Double,
        accuracy: Float,
        batteryPct: Int,
        speed: Float,
        bearing: Float,
    ) {
        val data = mapOf(
            "lat" to lat,
            "lng" to lng,
            "accuracy" to accuracy,
            "batteryPct" to batteryPct,
            "speed" to speed,
            "bearing" to bearing,
            "timestamp" to FieldValue.serverTimestamp(),
        )
        runCatching {
            historyCol(uid).add(data).await()
        }.onFailure { Timber.w(it, "writeHistoryPoint failed uid=%s", uid) }
    }

    /**
     * Vraća history point-e za user-a između from i to timestamp-a (server timestamp ms).
     * Poziva se sa PlaceScreen ili HistoryScreen-om za render trag-a na mapi.
     */
    fun observeHistory(uid: String, fromMs: Long, toMs: Long): Flow<List<LocationHistoryPoint>> =
        callbackFlow {
            val reg = historyCol(uid)
                .whereGreaterThanOrEqualTo("timestamp", Date(fromMs))
                .whereLessThanOrEqualTo("timestamp", Date(toMs))
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener { snap, error ->
                    if (error != null) {
                        Timber.w(error, "observeHistory error uid=%s", uid)
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    val list = snap?.documents.orEmpty().mapNotNull { d ->
                        d.toObject(LocationHistoryPoint::class.java)
                    }
                    trySend(list)
                }
            awaitClose { reg.remove() }
        }
}

data class LocationHistoryPoint(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val accuracy: Float = 0f,
    val batteryPct: Int = -1,
    val speed: Float = 0f,
    val bearing: Float = 0f,
    val timestamp: Date? = null,
)
