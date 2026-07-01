package org.krug.app.core.util

import android.content.Context
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.krug.app.R

/**
 * Vazdušna (great-circle) udaljenost između dve geo tačke, u metrima.
 * Earth radius approximation 6371 km — dovoljno precizno za prikaz korisniku
 * (greška < 0.5% za rastojanja u Srbiji).
 */
fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6371000.0
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val dphi = Math.toRadians(lat2 - lat1)
    val dlambda = Math.toRadians(lng2 - lng1)
    val a = sin(dphi / 2).let { it * it } +
        cos(phi1) * cos(phi2) *
        sin(dlambda / 2).let { it * it }
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

/**
 * Pure bucket za distance prikaz. Testabilan bez Context-a (vidi GeoTest).
 */
sealed class DistanceBucket {
    object Nearby : DistanceBucket()
    data class Meters(val value: Int) : DistanceBucket()
    data class KmDecimal(val km: Double) : DistanceBucket()
    data class KmInt(val km: Int) : DistanceBucket()
}

fun bucketDistance(meters: Double): DistanceBucket = when {
    meters < 50 -> DistanceBucket.Nearby
    meters < 1000 -> DistanceBucket.Meters(meters.toInt())
    meters < 10_000 -> DistanceBucket.KmDecimal(meters / 1000.0)
    else -> DistanceBucket.KmInt((meters / 1000.0).toInt())
}

/**
 * Formatira metre u korisniku-čitljiv string:
 * - < 50m: "blizu" / "near" (lokalizovano)
 * - < 1km: "123 m"
 * - < 10km: "1.5 km"
 * - else: "23 km"
 */
fun formatDistance(context: Context, meters: Double): String = when (val b = bucketDistance(meters)) {
    DistanceBucket.Nearby -> context.getString(R.string.distance_nearby)
    is DistanceBucket.Meters -> "${b.value} m"
    // Locale.getDefault() → SR koristi zarez ("1,5 km"), EN tačku ("1.5 km").
    is DistanceBucket.KmDecimal -> String.format(Locale.getDefault(), "%.1f km", b.km)
    is DistanceBucket.KmInt -> "${b.km} km"
}
