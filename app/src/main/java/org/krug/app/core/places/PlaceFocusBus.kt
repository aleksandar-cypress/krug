package org.krug.app.core.places

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Deep-link bus: PlacesScreen postavlja pending fokus (lat/lng/radius/name)
 * pre nego što se pop-uje nazad na MapScreen. Map screen collect-uje i flyTo-uje
 * na taj place, prikazuje ime + eventualno naglašava radius krug.
 */
object PlaceFocusBus {
    data class Focus(
        val lat: Double,
        val lng: Double,
        val name: String,
        val radius: Int,
    )

    private val _pending = MutableStateFlow<Focus?>(null)
    val pending: StateFlow<Focus?> = _pending.asStateFlow()

    fun request(lat: Double, lng: Double, name: String, radius: Int) {
        _pending.value = Focus(lat, lng, name, radius)
    }

    fun consume() {
        _pending.value = null
    }
}
