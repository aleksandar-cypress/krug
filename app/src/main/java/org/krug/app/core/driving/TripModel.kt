package org.krug.app.core.driving

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Zabeležena vožnja jednog člana. Detektuje se u LocationTrackingService kroz TripDetector,
 * upisuje u `users/{uid}/trips/{tripId}` posle finalize-a (kad speed padne ispod threshold-a
 * duže od TRIP_END_QUIET_MS).
 *
 * Ovo je "sample" model — grubo agregisan (start/end + max/dist), bez per-point path-a.
 * Dovoljan za "Driving reports" premium feature (max brzina, ukupno km po danu, broj vožnji).
 * Ako kasnije treba path visualization, dodaje se posebna `path` subcollection.
 */
data class TripModel(
    val id: String = "",
    /** Start GPS point — koristi se kao anchor za mapu i za day-bucket agregat. */
    val startAt: Date? = null,
    val endAt: Date? = null,
    /** Trajanje u sekundama — denormalizovano da ne moramo da računamo na svakom read-u. */
    val durationSec: Long = 0L,
    val distanceKm: Double = 0.0,
    val maxSpeedKmh: Double = 0.0,
    /** Prosek preko celog trip-a (distance / duration, uključuje sve što se broji kao vožnja). */
    val avgSpeedKmh: Double = 0.0,
    val startLat: Double = 0.0,
    val startLng: Double = 0.0,
    val endLat: Double = 0.0,
    val endLng: Double = 0.0,
    @ServerTimestamp val createdAt: Date? = null,
)
