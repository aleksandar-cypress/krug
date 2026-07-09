package org.krug.app.core.speeding

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Event upisan u Firestore kad user prekorači svoj podešen speeding threshold za
 * neprekidan period (SpeedingDetector.MIN_DURATION_MS). Piše se u sve krugove
 * kojima user pripada; drugi članovi listen-uju i pokazuju lokalnu notif.
 *
 * TTL: 24h (isti kao placeEvents — Firestore TTL policy briše stare event-e).
 */
data class SpeedingEventModel(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val maxSpeedKmh: Int = 0,
    val thresholdKmh: Int = 0,
    val durationSec: Int = 0,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    @ServerTimestamp val timestamp: Date? = null,
)
