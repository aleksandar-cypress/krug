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
 * Mapbox Geocoding API — search adresa/POI po tekstualnom query-ju.
 * Vraća listu predloga sa full display name-om + lat/lng.
 *
 * Besplatni tier: 100k requestova/mesec — dovoljno za tester bazu i moderate produkciju.
 */
@Singleton
class GeocodingRepository @Inject constructor() {

    data class Suggestion(
        val displayName: String,
        val placeName: String,
        val lat: Double,
        val lng: Double,
    )

    /**
     * Search po slobodnom tekstu (adresa, POI, mesto). Limit 5 rezultata.
     * `proximity` parametar podiže lokalne rezultate (npr Beograd) iznad global-a.
     */
    suspend fun search(
        query: String,
        proximityLat: Double? = null,
        proximityLng: Double? = null,
    ): List<Suggestion> = withContext(Dispatchers.IO) {
        if (query.trim().length < 3) return@withContext emptyList()
        val token = BuildConfig.MAPBOX_PUBLIC_TOKEN
        if (token.isBlank()) return@withContext emptyList()
        val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
        val url = buildString {
            append("https://api.mapbox.com/geocoding/v5/mapbox.places/$encoded.json")
            append("?access_token=$token&limit=5&language=sr,en")
            if (proximityLat != null && proximityLng != null) {
                append("&proximity=$proximityLng,$proximityLat")
            }
        }
        runCatching {
            val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                requestMethod = "GET"
            }
            try {
                if (conn.responseCode != 200) {
                    Timber.w("Geocoding responded %d for %s", conn.responseCode, query)
                    return@runCatching emptyList()
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val features = Json.parseToJsonElement(body).jsonObject["features"]?.jsonArray
                    ?: return@runCatching emptyList()
                features.mapNotNull { f ->
                    val obj = f.jsonObject
                    val coords = obj["center"]?.jsonArray ?: return@mapNotNull null
                    val lng = coords.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                    val lat = coords.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                    val text = obj["text"]?.jsonPrimitive?.content.orEmpty()
                    val placeName = obj["place_name"]?.jsonPrimitive?.content.orEmpty()
                    Suggestion(
                        displayName = text.ifBlank { placeName },
                        placeName = placeName,
                        lat = lat,
                        lng = lng,
                    )
                }
            } finally {
                conn.disconnect()
            }
        }.onFailure { Timber.w(it, "Geocoding search failed for %s", query) }.getOrDefault(emptyList())
    }
}
