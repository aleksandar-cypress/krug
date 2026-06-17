package org.krug.app.core.sos

data class SosModel(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val triggeredAt: Long = 0L,
    val message: String? = null,
)
