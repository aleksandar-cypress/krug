package org.krug.app.core.map

import com.mapbox.maps.Style
import org.krug.app.R

/**
 * Stil mape koji user bira u Settings-u. Persist se u LocalPrefs (`mapStyleKeyFlow`)
 * kao string enum ime (npr. "STANDARD") pa je stabilan preko upgrade-a i R8 obfuskacije.
 *
 * `styleUri` je Mapbox constant string koji se prosleđuje u `mapboxMap.loadStyle(...)`.
 * Konzumenti (MapScreen, HistoryScreen, AddPlaceScreen) observe-uju flow i re-loaduju
 * mapu kad user promeni pref.
 *
 * Trenutno je svima dostupno; premium gate za nefault-ne stilove dolazi u 1.2.0
 * (bez modifikacije ovog enum-a, gate se dodaje na entry point u MapStyleScreen-u).
 */
enum class MapStyleOption(
    val styleUri: String,
    val labelRes: Int,
    val subtitleRes: Int,
) {
    STANDARD(
        styleUri = Style.STANDARD,
        labelRes = R.string.map_style_standard,
        subtitleRes = R.string.map_style_standard_desc,
    ),
    DARK(
        styleUri = Style.DARK,
        labelRes = R.string.map_style_dark,
        subtitleRes = R.string.map_style_dark_desc,
    ),
    SATELLITE(
        styleUri = Style.SATELLITE_STREETS,
        labelRes = R.string.map_style_satellite,
        subtitleRes = R.string.map_style_satellite_desc,
    ),
    OUTDOORS(
        styleUri = Style.OUTDOORS,
        labelRes = R.string.map_style_outdoors,
        subtitleRes = R.string.map_style_outdoors_desc,
    );

    companion object {
        val DEFAULT: MapStyleOption = STANDARD

        fun fromKey(key: String?): MapStyleOption =
            entries.firstOrNull { it.name == key } ?: DEFAULT
    }
}
