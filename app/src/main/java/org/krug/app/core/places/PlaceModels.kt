package org.krug.app.core.places

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Geofence lokacija definisana per-circle. Svi članovi kruga vide iste places.
 * Enter/exit event triggeruje lokalnu notifikaciju kod ostalih članova
 * (klijent Firestore listener, nema Cloud Functions u v1.1).
 */
data class PlaceModel(
    val id: String = "",
    val name: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    /** Radius u metrima. GeofencingClient min = 10m, praktični min = 50m zbog GPS accuracy. */
    val radius: Int = DEFAULT_RADIUS_M,
    /** Kategorija (Home/School/Work/Gym/Shop/Other). Default OTHER za backward compat. */
    val category: String = PlaceCategory.OTHER.name,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Date? = null,
) {
    companion object {
        const val DEFAULT_RADIUS_M = 100
        const val MIN_RADIUS_M = 50
        const val MAX_RADIUS_M = 500

        /** Free tier: max 3 places per circle. Premium (v1.1+): unlimited. */
        const val FREE_TIER_MAX_PER_CIRCLE = 3
    }
}

/**
 * Kategorija mesta — utiče na marker ikonicu na mapi i default ime placeholder.
 * Enum vrednosti se čuvaju kao string u Firestore-u (data class field `category`).
 */
enum class PlaceCategory {
    HOME, SCHOOL, WORK, GYM, SHOP, OTHER;

    companion object {
        fun fromString(value: String?): PlaceCategory =
            values().firstOrNull { it.name == value } ?: OTHER
    }
}

/**
 * Event upisan u Firestore kad geofence enter/exit trigger-uje.
 * Ostali članovi kruga listen-uju subcollection i pokazuju lokalnu notif.
 * TTL: 24h — Firestore TTL policy briše stare event-e.
 */
data class PlaceEventModel(
    val id: String = "",
    val placeId: String = "",
    val placeName: String = "",
    val userId: String = "",
    val userName: String = "",
    val type: String = TYPE_ENTER,
    @ServerTimestamp val timestamp: Date? = null,
) {
    companion object {
        const val TYPE_ENTER = "ENTER"
        const val TYPE_EXIT = "EXIT"
    }
}
