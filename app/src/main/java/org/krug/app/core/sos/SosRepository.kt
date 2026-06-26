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
import timber.log.Timber

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
        senderName: String? = null,
        circleName: String? = null,
        message: String? = null,
    ) {
        // RTDB rules odbacuju null vrednosti za string polja, pa filter-ujemo. Samo prisutne
        // (lat/lng/triggeredAt) idu uvek, ostalo opciono.
        val data = buildMap<String, Any> {
            put("lat", lat)
            put("lng", lng)
            put("triggeredAt", ServerValue.TIMESTAMP)
            if (message != null) put("message", message)
            if (circleId != null) put("circleId", circleId)
            if (!senderName.isNullOrBlank()) put("senderName", senderName)
            if (!circleName.isNullOrBlank()) put("circleName", circleName)
        }
        ref(uid).setValue(data).await()
        Timber.i("SOS stored uid=%s circleId=%s", uid, circleId ?: "(none)")
    }

    suspend fun clear(uid: String) {
        ref(uid).removeValue().await()
        Timber.i("SOS cleared uid=%s", uid)
    }

    fun observe(uid: String): Flow<SosModel?> = callbackFlow {
        val r = ref(uid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.getValue(SosModel::class.java))
            }
            override fun onCancelled(error: DatabaseError) {
                // Bez ovog, tihi network/permission fail bi sakrio razlog što SOS
                // notifikacije ne dolaze peer-u — Crashlytics breadcrumb daje trag.
                Timber.w(error.toException(), "RTDB observe(sos/%s) cancelled", uid)
                trySend(null)
            }
        }
        r.addValueEventListener(listener)
        awaitClose { r.removeEventListener(listener) }
    }
}
