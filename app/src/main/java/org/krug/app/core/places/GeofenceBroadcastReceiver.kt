package org.krug.app.core.places

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * Prima geofence enter/exit event od Google Play Services.
 *
 * Request ID format: `{circleId}:{placeId}`. Iz njega izvlačimo circle context,
 * čitamo placeName iz Firestore-a (za notif payload), i pišemo PlaceEvent u
 * `/circles/{circleId}/placeEvents/`. Ostali članovi kruga listen-uju subcollection
 * i pokazuju lokalnu notif (nema Cloud Functions u v1.1).
 *
 * Sopstveni event ne triggeruje sopstvenu notif (klijent filter u listener-u).
 */
@AndroidEntryPoint
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    @Inject lateinit var firestore: FirebaseFirestore
    @Inject lateinit var auth: FirebaseAuth
    @Inject lateinit var placeRepository: PlaceRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        /**
         * In-memory dedup: (placeId + type) → last event time. Defence layer preko fixera
         * u GeofenceManager (INITIAL_TRIGGER = 0) — čak i ako Google Play Services zbog
         * bug-a ili race-a fire-uje duplicate ENTER/EXIT unutar kratkog vremena, ne pišemo
         * duplicate write u Firestore. Cooldown 60s pokriva realne re-trigger scenarije
         * (npr. GPS jitter oko boundary-ja).
         */
        private const val DEDUP_WINDOW_MS = 60_000L
        private val lastEventTimes = mutableMapOf<String, Long>()
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        if (intent.action != GeofenceManager.ACTION_GEOFENCE_TRANSITION) return

        val event = GeofencingEvent.fromIntent(intent)
        if (event == null) {
            Timber.w("GeofenceReceiver: null GeofencingEvent")
            return
        }
        if (event.hasError()) {
            Timber.w("GeofenceReceiver: event error code=%d", event.errorCode)
            return
        }
        val transition = event.geofenceTransition
        val type = when (transition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> PlaceEventModel.TYPE_ENTER
            Geofence.GEOFENCE_TRANSITION_EXIT -> PlaceEventModel.TYPE_EXIT
            else -> {
                Timber.d("GeofenceReceiver: ignore transition=%d", transition)
                return
            }
        }
        val triggered = event.triggeringGeofences.orEmpty()
        if (triggered.isEmpty()) {
            Timber.w("GeofenceReceiver: no triggering geofences")
            return
        }
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Timber.w("GeofenceReceiver: no auth user, skip logging")
            return
        }

        val pendingResult = goAsync()
        scope.launch {
            try {
                val userName = fetchUserName(uid)
                triggered.forEach { fence ->
                    val (circleId, placeId) = parseRequestId(fence.requestId) ?: return@forEach
                    // Dedup: preskoči duplicate event unutar 60s (isti place, isti transition).
                    val key = "$placeId:$type"
                    val now = System.currentTimeMillis()
                    val last = synchronized(lastEventTimes) { lastEventTimes[key] } ?: 0L
                    if (now - last < DEDUP_WINDOW_MS) {
                        Timber.d("GeofenceReceiver: dedup skip $key (last ${now - last}ms ago)")
                        return@forEach
                    }
                    synchronized(lastEventTimes) { lastEventTimes[key] = now }
                    val placeName = fetchPlaceName(circleId, placeId) ?: placeId
                    placeRepository.logEvent(
                        circleId = circleId,
                        placeId = placeId,
                        placeName = placeName,
                        userId = uid,
                        userName = userName,
                        type = type,
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "GeofenceReceiver: logging failed")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun parseRequestId(requestId: String): Pair<String, String>? {
        val parts = requestId.split(":", limit = 2)
        if (parts.size != 2) {
            Timber.w("GeofenceReceiver: malformed requestId=%s", requestId)
            return null
        }
        return parts[0] to parts[1]
    }

    private suspend fun fetchUserName(uid: String): String {
        return runCatching {
            firestore.collection("users").document(uid).get().await()
                .getString("displayName").orEmpty()
        }.getOrDefault("").ifBlank { "Član" }
    }

    private suspend fun fetchPlaceName(circleId: String, placeId: String): String? {
        return runCatching {
            firestore.collection("circles").document(circleId)
                .collection("places").document(placeId).get().await()
                .getString("name")
        }.getOrNull()
    }
}
