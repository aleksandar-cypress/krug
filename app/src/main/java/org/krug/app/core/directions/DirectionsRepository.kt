package org.krug.app.core.directions

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.krug.app.BuildConfig
import timber.log.Timber

/**
 * Klijent ka Mapbox Directions API-u za putnu (driving) distance među dvema lokacijama.
 * Razlikuje se od haversine (vazdušne linije) — pokazuje koliko realno treba da se prevali
 * autom (zaobilazi reke/planine, prati realne puteve). Razlika ume da bude 30-50% u Srbiji.
 *
 * Caching: LRU sa TTL-om i bucket-ovanim koordinatama (~100m granularnost) — sprečava
 * spamovanje Mapbox API-ja na svaki re-render MemberDetailSheet-a. Mapbox besplatna kvota
 * je 100k requestova/mesec, a cache rezolucija od 100m je dovoljna jer putna distance ne
 * menja smisleno za 50m korisničkog drifta.
 */
@Singleton
class DirectionsRepository @Inject constructor() {

    private data class CachedDistance(val meters: Double, val fetchedAt: Long)

    private val mutex = Mutex()
    // LinkedHashMap u access-order modu = LRU. Eviction kad pređe MAX_CACHE_ENTRIES.
    private val cache = object : LinkedHashMap<String, CachedDistance>(
        MAX_CACHE_ENTRIES, 0.75f, true,
    ) {
        override fun removeEldestEntry(eldest: Map.Entry<String, CachedDistance>): Boolean =
            size > MAX_CACHE_ENTRIES
    }

    /**
     * Vraća putnu udaljenost u metrima, ili null ako nije moguće (network fail, prazan
     * token, no route). Caller bi trebalo da prikaže haversine fallback u tom slučaju.
     */
    suspend fun drivingDistanceMeters(
        fromLat: Double, fromLng: Double,
        toLat: Double, toLng: Double,
    ): Double? {
        val key = cacheKey(fromLat, fromLng, toLat, toLng)
        val now = System.currentTimeMillis()
        mutex.withLock {
            cache[key]?.let { cached ->
                if (now - cached.fetchedAt < CACHE_TTL_MS) return cached.meters
            }
        }
        val fetched = fetchFromApi(fromLat, fromLng, toLat, toLng) ?: return null
        mutex.withLock { cache[key] = CachedDistance(fetched, now) }
        return fetched
    }

    private suspend fun fetchFromApi(
        fromLat: Double, fromLng: Double,
        toLat: Double, toLng: Double,
    ): Double? = withContext(Dispatchers.IO) {
        val token = BuildConfig.MAPBOX_PUBLIC_TOKEN
        if (token.isBlank()) {
            Timber.w("DirectionsRepository: empty Mapbox token, skipping fetch")
            return@withContext null
        }
        // Mapbox traži lng,lat redosled (ne lat,lng kao Google). overview=false znamenuje
        // da ne tražimo polyline geometriju — samo nam treba ukupna metraža.
        val url = buildString {
            append("https://api.mapbox.com/directions/v5/mapbox/driving/")
            append("$fromLng,$fromLat;$toLng,$toLat")
            append("?access_token=$token&overview=false&geometries=geojson")
        }
        runCatching {
            val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = REQUEST_TIMEOUT_MS
                readTimeout = REQUEST_TIMEOUT_MS
                requestMethod = "GET"
            }
            try {
                if (conn.responseCode != 200) {
                    Timber.w("Directions API responded ${conn.responseCode} for $fromLat,$fromLng -> $toLat,$toLng")
                    return@runCatching null
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = Json.parseToJsonElement(body).jsonObject
                val routes = obj["routes"]?.jsonArray ?: return@runCatching null
                if (routes.isEmpty()) return@runCatching null
                routes[0].jsonObject["distance"]?.jsonPrimitive?.doubleOrNull
            } finally {
                conn.disconnect()
            }
        }.onFailure { Timber.w(it, "Directions API fetch failed") }.getOrNull()
    }

    private fun cacheKey(
        fromLat: Double, fromLng: Double,
        toLat: Double, toLng: Double,
    ): String {
        // Bucket na ~100m (0.001 stepena ≈ 111m na ekvatoru) — sprečava da svaki GPS drift
        // od 10m oborije cache i pošalje novi request.
        fun bucket(d: Double): Long = (d * 1000.0).toLong()
        return "${bucket(fromLat)},${bucket(fromLng)}->${bucket(toLat)},${bucket(toLng)}"
    }

    private companion object {
        const val MAX_CACHE_ENTRIES = 64
        const val CACHE_TTL_MS = 5L * 60_000L // 5 min — putna distance ne varira brzo
        const val REQUEST_TIMEOUT_MS = 8000
    }
}
