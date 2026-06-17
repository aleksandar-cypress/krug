package org.krug.app.core.location

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

@Singleton
class LocationRepository @Inject constructor(
    private val database: FirebaseDatabase,
) {
    private fun locationRef(uid: String) = database.getReference("locations/$uid")
    private fun requestsRef(targetUid: String) = database.getReference("locationRequests/$targetUid")
    private fun requestEntry(targetUid: String, requesterUid: String) =
        database.getReference("locationRequests/$targetUid/$requesterUid")

    suspend fun publish(
        uid: String,
        lat: Double,
        lng: Double,
        accuracy: Float,
        batteryPct: Int,
        isCharging: Boolean,
    ) {
        val data = mapOf(
            "lat" to lat,
            "lng" to lng,
            "accuracy" to accuracy,
            "batteryPct" to batteryPct,
            "isCharging" to isCharging,
            "updatedAt" to ServerValue.TIMESTAMP,
        )
        locationRef(uid).setValue(data).await()
    }

    fun observe(uid: String): Flow<LocationModel?> = callbackFlow {
        val ref = locationRef(uid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.getValue(LocationModel::class.java))
            }
            override fun onCancelled(error: DatabaseError) = Unit
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /** Pošalji "ping" target user-u da pošalje svežu lokaciju. */
    suspend fun requestRefresh(targetUid: String, requesterUid: String) {
        requestEntry(targetUid, requesterUid).setValue(ServerValue.TIMESTAMP).await()
    }

    /** Sluša ping-ove poslate ovom user-u. Vraća set requesterUid-ova. */
    fun observeRefreshRequests(ownUid: String): Flow<Set<String>> = callbackFlow {
        val ref = requestsRef(ownUid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val ids = snapshot.children.mapNotNull { it.key }.toSet()
                trySend(ids)
            }
            override fun onCancelled(error: DatabaseError) = Unit
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Obriši ping zahteve od konkretnih requester-a (posle što su processed).
     * Brišemo po child path-u jer rules dozvoljavaju write samo na
     * /locationRequests/{targetUid}/{requesterUid} — ne na parent path-u.
     */
    suspend fun clearRefreshRequests(ownUid: String, requesters: Set<String>) {
        requesters.forEach { requesterUid ->
            runCatching { requestEntry(ownUid, requesterUid).removeValue().await() }
        }
    }
}
