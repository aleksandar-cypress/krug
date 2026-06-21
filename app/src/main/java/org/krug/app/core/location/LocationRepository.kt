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
import timber.log.Timber

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
            "charging" to isCharging,
            "paused" to false,
            "updatedAt" to ServerValue.TIMESTAMP,
        )
        locationRef(uid).setValue(data).await()
    }

    /**
     * Označi self lokaciju kao pauziranu — peers odmah vide "Privatni mod" badge bez
     * čekanja 15min staleness threshold-a. Ne briše lat/lng (peers imaju last-known kao
     * fallback za "Otvori u Google Maps").
     */
    suspend fun setPaused(uid: String, paused: Boolean) {
        runCatching {
            locationRef(uid).child("paused").setValue(paused).await()
            // Kad se vraća iz pause-a, takođe ažuriraj updatedAt da signaliziramo "ponovo live".
            if (!paused) {
                locationRef(uid).child("updatedAt").setValue(ServerValue.TIMESTAMP).await()
            }
        }
    }

    fun observe(uid: String): Flow<LocationModel?> = callbackFlow {
        val ref = locationRef(uid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.getValue(LocationModel::class.java))
            }
            override fun onCancelled(error: DatabaseError) {
                // Tihi network/permission fail bi ostavio listener "mrtav" — peer-i ne
                // bi videli ažuriranja a app ne bi imao trag za debugging. Log za
                // Crashlytics breadcrumb + emit null da downstream UI zna da nema podataka.
                Timber.w(error.toException(), "RTDB observe(location/%s) cancelled", uid)
                trySend(null)
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /** Pošalji "ping" target user-u da pošalje svežu lokaciju. */
    suspend fun requestRefresh(targetUid: String, requesterUid: String) {
        requestEntry(targetUid, requesterUid).setValue(ServerValue.TIMESTAMP).await()
    }

    /** Sluša ping-ove poslate ovom user-u. Vraća mapu requesterUid → timestamp. */
    fun observeRefreshRequests(ownUid: String): Flow<Map<String, Long>> = callbackFlow {
        val ref = requestsRef(ownUid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val map = snapshot.children.mapNotNull { child ->
                    val uid = child.key ?: return@mapNotNull null
                    val ts = (child.value as? Long) ?: return@mapNotNull null
                    uid to ts
                }.toMap()
                trySend(map)
            }
            override fun onCancelled(error: DatabaseError) {
                Timber.w(error.toException(), "RTDB observe(locationRequests/%s) cancelled", ownUid)
                trySend(emptyMap())
            }
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

    /** GDPR — obriši sve RTDB tragove ovog usera (osim ping-ova koje su drugi poslali). */
    suspend fun deleteForUser(uid: String) {
        runCatching { locationRef(uid).removeValue().await() }
    }
}
