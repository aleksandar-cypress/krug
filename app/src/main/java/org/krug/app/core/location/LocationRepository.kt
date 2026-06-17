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
}
