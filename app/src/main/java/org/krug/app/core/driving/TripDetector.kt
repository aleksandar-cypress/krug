package org.krug.app.core.driving

import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.krug.app.core.util.haversineMeters
import timber.log.Timber

/**
 * State machine za detekciju vožnje iz stream-a GPS fixes-a.
 *
 * IDLE → DRIVING: 2+ uzastopna fix-a sa speed >= TRIP_START_SPEED_MPS (~7 m/s = 25 km/h).
 * DRIVING → IDLE: nema fixes-a sa speed >= TRIP_KEEPALIVE_SPEED_MPS (1.4 m/s = 5 km/h)
 *   duže od TRIP_END_QUIET_MS (3 min). Kad se pređe, finalize-uje se trip i upisuje u
 *   Firestore.
 *
 * Radi in-memory unutar LocationTrackingService — trip u toku se GUBI ako FGS umre
 * (proces kill, force stop, reboot). Prihvatljiv trade-off jer:
 *   (a) FGS treba da bude živ (imamo notif),
 *   (b) trajanje vožnje je obično <60min pa je window kratak,
 *   (c) posle restart-a novi fix >25km/h opet starta trip.
 *
 * Nije singleton u Hilt smislu iako je @Singleton — koristi se samo iz FGS-a; injection
 * pattern zbog testabilnosti i konzistentnosti sa ostalim ovim komponentama.
 */
@Singleton
class TripDetector @Inject constructor(
    private val tripRepository: TripRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var currentState: State = State.Idle
    private var pendingStartHits: Int = 0

    private sealed class State {
        object Idle : State()
        data class Driving(
            val startAt: Long,
            val startLat: Double,
            val startLng: Double,
            var lastFixAt: Long,
            var lastLat: Double,
            var lastLng: Double,
            var distanceMeters: Double,
            var maxSpeedMps: Float,
            /** Trenutak poslednjeg fix-a sa speed >= keepalive threshold — koristi se za quiet timeout. */
            var lastActiveAt: Long,
        ) : State()
    }

    /**
     * Ubaci nov GPS fix. `uid` je vlasnik trip-a (self), `nowMs` je System.currentTimeMillis()
     * u trenutku obrade (izbegava se re-read unutar detektora radi determinizma testova).
     */
    fun onFix(
        uid: String,
        lat: Double,
        lng: Double,
        speedMps: Float,
        nowMs: Long,
    ) {
        when (val s = currentState) {
            is State.Idle -> {
                if (speedMps >= TRIP_START_SPEED_MPS) {
                    pendingStartHits++
                    if (pendingStartHits >= TRIP_START_MIN_HITS) {
                        currentState = State.Driving(
                            startAt = nowMs,
                            startLat = lat,
                            startLng = lng,
                            lastFixAt = nowMs,
                            lastLat = lat,
                            lastLng = lng,
                            distanceMeters = 0.0,
                            maxSpeedMps = speedMps,
                            lastActiveAt = nowMs,
                        )
                        pendingStartHits = 0
                        Timber.d("TripDetector: start (speed=${(speedMps * 3.6).toInt()} km/h)")
                    }
                } else {
                    pendingStartHits = 0
                }
            }
            is State.Driving -> {
                val delta = haversineMeters(s.lastLat, s.lastLng, lat, lng)
                // Sanity guard — teleporti (>500m između fix-a manje od 30s = GPS jitter).
                // Ne trošimo distance na to.
                val elapsedSinceLast = (nowMs - s.lastFixAt).coerceAtLeast(1L)
                val impliedMps = delta / (elapsedSinceLast / 1000.0)
                if (impliedMps < 55.0) {
                    s.distanceMeters += delta
                }
                if (speedMps > s.maxSpeedMps) s.maxSpeedMps = speedMps
                s.lastFixAt = nowMs
                s.lastLat = lat
                s.lastLng = lng
                if (speedMps >= TRIP_KEEPALIVE_SPEED_MPS) {
                    s.lastActiveAt = nowMs
                }
                // Prekidaj vožnju ako je quiet period predugačak.
                if (nowMs - s.lastActiveAt > TRIP_END_QUIET_MS) {
                    finalize(uid, s, nowMs)
                    currentState = State.Idle
                }
            }
        }
    }

    /** Poziva se iz FGS-a onDestroy — sačuvaj bilo koji trip u toku pre nego što proces umre. */
    fun flush(uid: String, nowMs: Long = System.currentTimeMillis()) {
        val s = currentState as? State.Driving ?: return
        finalize(uid, s, nowMs)
        currentState = State.Idle
        pendingStartHits = 0
    }

    private fun finalize(uid: String, s: State.Driving, endAt: Long) {
        val durationSec = (endAt - s.startAt).coerceAtLeast(0L) / 1000L
        val distanceKm = s.distanceMeters / 1000.0
        // Filter noise trips — kratke ili prekratke se ne upisuju (npr. jedan zaboravljen
        // fix sa lažnom brzinom). MIN_TRIP_DISTANCE_KM cutoff pokriva slučaj "auto se
        // pomerio iz parking-a a onda stalo" bez prave vožnje.
        if (distanceKm < MIN_TRIP_DISTANCE_KM || durationSec < MIN_TRIP_DURATION_SEC) {
            Timber.d(
                "TripDetector: discard short trip (dist=%.2f km, dur=%ds)",
                distanceKm, durationSec,
            )
            return
        }
        val avgKmh = if (durationSec > 0) distanceKm / (durationSec / 3600.0) else 0.0
        val trip = TripModel(
            startAt = Date(s.startAt),
            endAt = Date(endAt),
            durationSec = durationSec,
            distanceKm = distanceKm,
            maxSpeedKmh = s.maxSpeedMps * 3.6,
            avgSpeedKmh = avgKmh,
            startLat = s.startLat,
            startLng = s.startLng,
            endLat = s.lastLat,
            endLng = s.lastLng,
        )
        scope.launch {
            runCatching { tripRepository.saveTrip(uid, trip) }
                .onFailure { Timber.w(it, "saveTrip failed") }
        }
        Timber.d(
            "TripDetector: finalize (dist=%.2f km, max=%.0f km/h, dur=%dmin)",
            distanceKm, s.maxSpeedMps * 3.6, durationSec / 60,
        )
    }

    companion object {
        /** ~25 km/h — donji prag za start-of-drive detekciju. Sporija kretanja (hodanje, bicikla, trotinet) se ignorišu. */
        const val TRIP_START_SPEED_MPS = 7.0f
        /** ~5 km/h — dok si iznad ovog, "vožnja" je aktivna. Ispod, brojimo quiet period. */
        const val TRIP_KEEPALIVE_SPEED_MPS = 1.4f
        /** Uzastopni fix-i iznad START threshold-a pre nego što potvrdimo start. Filter noise. */
        const val TRIP_START_MIN_HITS = 2
        /** Nema aktivnosti (speed < 5 km/h) duže od ovog → finalize trip. 3 min pokriva semafore i saobraćajne zastoje. */
        const val TRIP_END_QUIET_MS = 3 * 60_000L
        /** Kraći trip-ovi se odbacuju kao noise. */
        const val MIN_TRIP_DISTANCE_KM = 0.3
        const val MIN_TRIP_DURATION_SEC = 60L
    }
}
