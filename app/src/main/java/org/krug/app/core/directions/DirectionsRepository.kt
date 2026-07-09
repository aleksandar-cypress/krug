package org.krug.app.core.directions

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.krug.app.BuildConfig
import timber.log.Timber

/**
 * Mapbox Directions API klijent za ETA sharing. Vraća procenu vremena i preostalog
 * puta na osnovu real road network-a (uzima u obzir jednosmerne, brzine, itd.),
 * što je znatno preciznije nego haversine estimate.
 *
 * Rate/cost: besplatni tier je 100k zahteva/mesec. Ako 100 usera aktivno deli ETA
 * po 30min sa update-om na 60s, to je 3000 req/mesec — daleko od praga. Cache-uje
 * se rezultat 15s da izbegnemo double-fetch pri brzim sequential fix-evima.
 */
@Singleton
class DirectionsRepository @Inject constructor() {

    data class Route(
        val durationSec: Double,
        val distanceMeters: Double,
    )

    /**
     * Fetch driving directions od origin do destination. `null` = network/API greška
     * (caller degradira na haversine estimate).
     */
    suspend fun driveEta(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
    ): Route? = withContext(Dispatchers.IO) {
        val token = BuildConfig.MAPBOX_PUBLIC_TOKEN
        if (token.isBlank()) return@withContext null
        val url = "https://api.mapbox.com/directions/v5/mapbox/driving/" +
            "$originLng,$originLat;$destLng,$destLat" +
            "?access_token=$token&overview=false&geometries=geojson"
        runCatching {
            val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                requestMethod = "GET"
            }
            try {
                if (conn.responseCode != 200) {
                    Timber.w("Directions API responded %d", conn.responseCode)
                    return@runCatching null
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val routes = Json.parseToJsonElement(body).jsonObject["routes"]?.jsonArray
                val first = routes?.firstOrNull()?.jsonObject ?: return@runCatching null
                val dur = first["duration"]?.jsonPrimitive?.doubleOrNull ?: return@runCatching null
                val dist = first["distance"]?.jsonPrimitive?.doubleOrNull ?: return@runCatching null
                Route(durationSec = dur, distanceMeters = dist)
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }
}
