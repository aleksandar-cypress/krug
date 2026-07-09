package org.krug.app.core.checkin

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Safe check-in event — "Stigao/la sam" akcija sa trenutne lokacije. Piše se u sve
 * krugove korisnika. Nije vezan za konkretan place (za razliku od geofence enter/exit);
 * `placeLabel` je opciona reverse-geocoded oznaka (npr. „Bulevar kralja Aleksandra 15")
 * koja daje kontekst u notifikaciji.
 *
 * TTL: 24h (Firestore TTL policy).
 */
data class CheckInEventModel(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    /** Reverse-geocoded label ili prazno ako geocoding faila/nije završen na vreme. */
    val placeLabel: String = "",
    @ServerTimestamp val timestamp: Date? = null,
)
