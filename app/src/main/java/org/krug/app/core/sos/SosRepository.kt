package org.krug.app.core.sos

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
class SosRepository @Inject constructor(
    private val database: FirebaseDatabase,
) {
    private fun ref(uid: String) = database.getReference("sos/$uid")

    suspend fun trigger(
        uid: String,
        lat: Double,
        lng: Double,
        circleId: String?,
        message: String? = null,
    ) {
        val data = mapOf(
            "lat" to lat,
            "lng" to lng,
            "triggeredAt" to ServerValue.TIMESTAMP,
            "message" to message,
            "circleId" to circleId,
        )
        ref(uid).setValue(data).await()
    }

    suspend fun clear(uid: String) {
        ref(uid).removeValue().await()
    }

    fun observe(uid: String): Flow<SosModel?> = callbackFlow {
        val r = ref(uid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.getValue(SosModel::class.java))
            }
            override fun onCancelled(error: DatabaseError) = Unit
        }
        r.addValueEventListener(listener)
        awaitClose { r.removeEventListener(listener) }
    }
}
