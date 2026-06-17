package org.krug.app.core.location

// RTDB POJO for /locations/{uid}. updatedAt is a server-set ms-since-epoch.
// `charging` (ne `isCharging`) — Kotlin `is` prefix konfundovala Firebase ClassMapper,
// generišući "No setter/field for isCharging" warning na svakom read-u.
data class LocationModel(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val accuracy: Float = 0f,
    val batteryPct: Int = -1,
    val charging: Boolean = false,
    val updatedAt: Long = 0L,
)
