package org.krug.app.core.location

// RTDB POJO for /locations/{uid}. updatedAt is a server-set ms-since-epoch.
data class LocationModel(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val accuracy: Float = 0f,
    val batteryPct: Int = -1,
    val isCharging: Boolean = false,
    val updatedAt: Long = 0L,
)
