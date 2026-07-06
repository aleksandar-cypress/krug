package org.krug.app.core.places

import org.krug.app.core.location.LocationHistoryPoint
import org.krug.app.core.util.haversineMeters

/**
 * Predlog za novo mesto — detektovan na osnovu History-ja. Prikazuje se u
 * PlacesScreen banner-u sa "Kreiraj" akcijom koja otvara AddPlaceScreen sa
 * prefilled lat/lng.
 *
 * `pointCount` je broj history point-a u toj lokaciji za dati period —
 * heuristika za "koliko user provodi vremena tu". Score = pointCount /
 * suffered_days (broj različitih dana kad je user tu bio bar jednom).
 */
data class PlaceSuggestion(
    val lat: Double,
    val lng: Double,
    val pointCount: Int,
    val distinctDays: Int,
    /** Predloženi tip — UI resolve-uje u lokalizovan string preko resource-a. */
    val suggestedType: SuggestedNameType,
)

/** Tip predloženog mesta — resolved u UI-ju kroz string resource. */
enum class SuggestedNameType {
    HOME,
    WORK,
    GENERIC,
}

/**
 * Detektuje potencijalna mesta na osnovu History-ja. Algoritam:
 *
 * 1. Bucket-uj sve point-e u grid od 100m (round lat/lng na 0.001° = ~110m).
 * 2. Za svaki bucket, prebroji point-e i različite dane.
 * 3. Filtriraj: min 20 point-a preko min 3 različita dana (recurring visits, ne
 *    single-time posete).
 * 4. Filtriraj već pokrivene bucket-ove: ako je centar bucket-a unutar 200m od
 *    postojećeg place-a, skip (user je već znao za to mesto).
 * 5. Sortiraj po score-u desc, uzmi top-N.
 *
 * `suggestedName` heuristika: koristi većinski deo dana za noćni-vs-dnevni odabir.
 * - Ako > 60% point-a između 22h-06h → "Kuća" (verovatno).
 * - Ako > 60% point-a između 09h-17h radnim danima → "Posao" (verovatno).
 * - Inače "Mesto".
 *
 * @param history point-i iz zadnjih N dana (poziva se sa 7d prozorom za MVP)
 * @param existingPlaces trenutna Places lista — filter za već postojeća mesta
 * @param maxSuggestions max broj vraćenih predloga (default 2)
 */
fun detectPlaceSuggestions(
    history: List<LocationHistoryPoint>,
    existingPlaces: List<PlaceModel>,
    maxSuggestions: Int = 2,
): List<PlaceSuggestion> {
    if (history.size < 20) return emptyList() // Premalo podataka za bilo kakav pattern.
    // Grid bucket size: 0.001° lat ≈ 111m, 0.001° lng na 45° geo lat ≈ 78m.
    // 100m grid je dovoljan za "isto mesto" bez false-splitting-a između ulica.
    val gridStep = 0.001
    data class Bucket(
        val latKey: Int,
        val lngKey: Int,
        val points: MutableList<LocationHistoryPoint> = mutableListOf(),
    )
    val buckets = mutableMapOf<Pair<Int, Int>, Bucket>()
    history.forEach { p ->
        val latKey = (p.lat / gridStep).toInt()
        val lngKey = (p.lng / gridStep).toInt()
        val key = latKey to lngKey
        buckets.getOrPut(key) { Bucket(latKey, lngKey) }.points.add(p)
    }
    val candidates = buckets.values.mapNotNull { b ->
        if (b.points.size < 20) return@mapNotNull null
        val distinctDays = b.points.mapNotNull { it.timestamp?.time }
            .map { it / (24L * 60 * 60 * 1000) }
            .toSet()
            .size
        if (distinctDays < 3) return@mapNotNull null
        // Centar bucket-a — prosek lat/lng point-a (preciznije od grid center-a).
        val cLat = b.points.map { it.lat }.average()
        val cLng = b.points.map { it.lng }.average()
        // Filter: ako je već blizu postojećeg place-a, skip.
        val nearExisting = existingPlaces.any { p ->
            haversineMeters(cLat, cLng, p.lat, p.lng) < 200.0
        }
        if (nearExisting) return@mapNotNull null
        // Tip — heuristika iz vremena.
        val type = inferPlaceType(b.points)
        PlaceSuggestion(
            lat = cLat,
            lng = cLng,
            pointCount = b.points.size,
            distinctDays = distinctDays,
            suggestedType = type,
        )
    }
    // Sort desc po pointCount (višem count = češće posećeno).
    return candidates.sortedByDescending { it.pointCount }.take(maxSuggestions)
}

/**
 * Heuristika za tip na osnovu vremena dana kada je user tu.
 * - Većina point-a noću (22-06) → HOME
 * - Većina point-a radnim danima 09-17 → WORK
 * - Ostalo → GENERIC
 */
private fun inferPlaceType(points: List<LocationHistoryPoint>): SuggestedNameType {
    var nightCount = 0
    var workHoursCount = 0
    var total = 0
    val cal = java.util.Calendar.getInstance()
    points.forEach { p ->
        val ts = p.timestamp?.time ?: return@forEach
        cal.timeInMillis = ts
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK) // 1=Sun, 7=Sat
        total++
        if (hour >= 22 || hour < 6) nightCount++
        if (hour in 9..16 && dayOfWeek in java.util.Calendar.MONDAY..java.util.Calendar.FRIDAY) {
            workHoursCount++
        }
    }
    if (total == 0) return SuggestedNameType.GENERIC
    return when {
        nightCount * 100 / total >= 60 -> SuggestedNameType.HOME
        workHoursCount * 100 / total >= 60 -> SuggestedNameType.WORK
        else -> SuggestedNameType.GENERIC
    }
}
