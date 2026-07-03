package org.krug.app.core.directions

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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

    data class DrivingRoute(val distanceMeters: Double, val durationSeconds: Double)

    private data class CachedRoute(val route: DrivingRoute, val fetchedAt: Long)
    private data class CachedDistance(val meters: Double, val fetchedAt: Long)

    private val mutex = Mutex()
    // LinkedHashMap u access-order modu = LRU. Eviction kad pređe MAX_CACHE_ENTRIES.
    private val cache = object : LinkedHashMap<String, CachedDistance>(
        MAX_CACHE_ENTRIES, 0.75f, true,
    ) {
        override fun removeEldestEntry(eldest: Map.Entry<String, CachedDistance>): Boolean =
            size > MAX_CACHE_ENTRIES
    }
    // In-flight requests — drugi pozivalac na isti key se "pridruži" postojećem
    // Deferred-u umesto da pošalje paralelan HTTP request. Bez ovog, ako dva uglavnom-
    // istovremena MemberDetailSheet-a otvore se sa istim parovima koordinata, oboje bi
    // hit-ovala cache miss i poslala duplikat zahtev ka Mapbox API-ju.
    private val inFlight = mutableMapOf<String, CompletableDeferred<Double?>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        // Atomska check-then-{cache hit || join in-flight || start new fetch} sekvenca.
        // Jedan mutex blok obuhvata sve odluke da spreči trku između paralelnih poziva.
        val deferred: CompletableDeferred<Double?> = mutex.withLock {
            cache[key]?.let { cached ->
                if (now - cached.fetchedAt < CACHE_TTL_MS) {
                    return cached.meters
                }
            }
            inFlight[key]?.let { existing -> return@withLock existing }
            val fresh = CompletableDeferred<Double?>()
            inFlight[key] = fresh
            // Pokrećemo fetch u scope-u da pozivalac može da bude cancellated bez da
            // ubije in-flight request (drugi pozivalac može da ga koristi).
            scope.async {
                val result = runCatching {
                    fetchFromApi(fromLat, fromLng, toLat, toLng)
                }.getOrNull()
                mutex.withLock {
                    inFlight.remove(key)
                    if (result != null) cache[key] = CachedDistance(result, System.currentTimeMillis())
                }
                fresh.complete(result)
            }
            fresh
        }
        return deferred.await()
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

    // ETA (route sa distance + duration).
    private val routeCache = object : LinkedHashMap<String, CachedRoute>(
        MAX_CACHE_ENTRIES, 0.75f, true,
    ) {
        override fun removeEldestEntry(eldest: Map.Entry<String, CachedRoute>): Boolean =
            size > MAX_CACHE_ENTRIES
    }
    private val inFlightRoute = mutableMapOf<String, CompletableDeferred<DrivingRoute?>>()

    /**
     * Vraća putnu udaljenost + duration u sekundama. Duration je traffic-agnostic
     * (Mapbox besplatni tier nema live traffic). Za bolju ETA — Mapbox `driving-traffic`
     * profil, ali on ima drugačiju kvotu.
     */
    suspend fun drivingRoute(
        fromLat: Double, fromLng: Double,
        toLat: Double, toLng: Double,
    ): DrivingRoute? {
        val key = cacheKey(fromLat, fromLng, toLat, toLng)
        val now = System.currentTimeMillis()
        val deferred: CompletableDeferred<DrivingRoute?> = mutex.withLock {
            routeCache[key]?.let { cached ->
                if (now - cached.fetchedAt < CACHE_TTL_MS) return cached.route
            }
            inFlightRoute[key]?.let { return@withLock it }
            val fresh = CompletableDeferred<DrivingRoute?>()
            inFlightRoute[key] = fresh
            scope.async {
                val result = runCatching {
                    fetchRouteFromApi(fromLat, fromLng, toLat, toLng)
                }.getOrNull()
                mutex.withLock {
                    inFlightRoute.remove(key)
                    if (result != null) routeCache[key] = CachedRoute(result, System.currentTimeMillis())
                }
                fresh.complete(result)
            }
            fresh
        }
        return deferred.await()
    }

    private suspend fun fetchRouteFromApi(
        fromLat: Double, fromLng: Double,
        toLat: Double, toLng: Double,
    ): DrivingRoute? = withContext(Dispatchers.IO) {
        val token = BuildConfig.MAPBOX_PUBLIC_TOKEN
        if (token.isBlank()) return@withContext null
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
                if (conn.responseCode != 200) return@runCatching null
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = Json.parseToJsonElement(body).jsonObject
                val routes = obj["routes"]?.jsonArray ?: return@runCatching null
                if (routes.isEmpty()) return@runCatching null
                val first = routes[0].jsonObject
                val dist = first["distance"]?.jsonPrimitive?.doubleOrNull ?: return@runCatching null
                val dur = first["duration"]?.jsonPrimitive?.doubleOrNull ?: return@runCatching null
                DrivingRoute(dist, dur)
            } finally {
                conn.disconnect()
            }
        }.onFailure { Timber.w(it, "Directions route fetch failed") }.getOrNull()
    }

    private companion object {
        const val MAX_CACHE_ENTRIES = 64
        const val CACHE_TTL_MS = 5L * 60_000L // 5 min — putna distance ne varira brzo
        const val REQUEST_TIMEOUT_MS = 8000
    }
}
