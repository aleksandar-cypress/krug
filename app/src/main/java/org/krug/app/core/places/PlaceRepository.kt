package org.krug.app.core.places

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
class PlaceRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    private fun places(circleId: String) =
        firestore.collection("circles").document(circleId).collection("places")

    private fun events(circleId: String) =
        firestore.collection("circles").document(circleId).collection("placeEvents")

    suspend fun createPlace(
        circleId: String,
        userId: String,
        name: String,
        lat: Double,
        lng: Double,
        radius: Int,
    ): String {
        val doc = places(circleId).document()
        val data = mapOf(
            "name" to name,
            "lat" to lat,
            "lng" to lng,
            "radius" to radius,
            "createdBy" to userId,
            "createdAt" to FieldValue.serverTimestamp(),
        )
        doc.set(data).await()
        Timber.i("Place created circleId=%s placeId=%s name=%s", circleId, doc.id, name)
        return doc.id
    }

    suspend fun updatePlace(
        circleId: String,
        placeId: String,
        name: String,
        radius: Int,
    ) {
        places(circleId).document(placeId).update(
            mapOf(
                "name" to name,
                "radius" to radius,
            ),
        ).await()
        Timber.i("Place updated circleId=%s placeId=%s", circleId, placeId)
    }

    suspend fun deletePlace(circleId: String, placeId: String) {
        places(circleId).document(placeId).delete().await()
        Timber.i("Place deleted circleId=%s placeId=%s", circleId, placeId)
    }

    fun observePlaces(circleId: String): Flow<List<PlaceModel>> = callbackFlow {
        val reg = places(circleId)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Timber.w(error, "observePlaces error circleId=%s", circleId)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snap?.documents.orEmpty().mapNotNull { d ->
                    d.toObject(PlaceModel::class.java)?.copy(id = d.id)
                }
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun logEvent(
        circleId: String,
        placeId: String,
        placeName: String,
        userId: String,
        userName: String,
        type: String,
    ) {
        val data = mapOf(
            "placeId" to placeId,
            "placeName" to placeName,
            "userId" to userId,
            "userName" to userName,
            "type" to type,
            "timestamp" to FieldValue.serverTimestamp(),
        )
        events(circleId).add(data).await()
        Timber.i(
            "PlaceEvent logged circleId=%s placeId=%s userId=%s type=%s",
            circleId, placeId, userId, type,
        )
    }

    /**
     * Observe najnovije event-e (poslednjih N). Klijent koristi za lokalnu notif —
     * ignoriše event-e starije od "join time" flow-a da izbegne replay pri restartu.
     */
    fun observeRecentEvents(circleId: String, limit: Long = 20): Flow<List<PlaceEventModel>> =
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
                        d.toObject(PlaceEventModel::class.java)?.copy(id = d.id)
                    }
                    trySend(list)
                }
            awaitClose { reg.remove() }
        }
}
