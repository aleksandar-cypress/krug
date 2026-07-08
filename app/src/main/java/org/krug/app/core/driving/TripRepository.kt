package org.krug.app.core.driving

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
 * Trip subcollection u `users/{uid}/trips/{tripId}`. Vlasnik piše samo sopstvene trip-ove
 * (firestore.rules to enforce-uje), članovi kruga mogu da čitaju trip-ove drugih članova.
 */
@Singleton
class TripRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {

    private fun tripsCol(uid: String) =
        firestore.collection("users").document(uid).collection("trips")

    suspend fun saveTrip(uid: String, trip: TripModel) {
        // Firestore auto-id — startAt će služiti za sort DESC.
        tripsCol(uid).add(trip).await()
        Timber.d("Trip saved for %s (dist=%.2f km)", uid, trip.distanceKm)
    }

    /**
     * Observe trip-ove za jednog člana u vremenskom prozoru. Sortirano DESC po startAt-u,
     * limit 100 (dovoljno za nedeljni pregled prosečnog user-a).
     */
    fun observeTrips(uid: String, fromMs: Long, toMs: Long): Flow<List<TripModel>> = callbackFlow {
        val query = tripsCol(uid)
            .whereGreaterThanOrEqualTo("startAt", Date(fromMs))
            .whereLessThanOrEqualTo("startAt", Date(toMs))
            .orderBy("startAt", Query.Direction.DESCENDING)
            .limit(100)
        val reg = query.addSnapshotListener { snap, error ->
            if (error != null) {
                Timber.w(error, "observeTrips error uid=%s", uid)
                trySend(emptyList())
                return@addSnapshotListener
            }
            val trips = snap?.documents.orEmpty().mapNotNull { doc ->
                doc.toObject(TripModel::class.java)?.copy(id = doc.id)
            }
            trySend(trips)
        }
        awaitClose { reg.remove() }
    }
}
