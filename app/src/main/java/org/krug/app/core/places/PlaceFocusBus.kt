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

    /**
     * Deep-link iz notifikacije (Place event enter/exit) prosleđuje samo `placeId` jer
     * PlaceEventNotifier nema instant pristup lokaciji/nazivu/radius-u (Firestore snapshot
     * može biti stale). MapScreen collect-uje ovaj flow, čeka da `activePlaces` u
     * MapViewModel-u sadrži place sa tim id-om, pa resolve-uje u Focus i emituje kroz
     * regular `pending` flow. Tako flyTo logika ima jedan pattern za sve pozive.
     */
    private val _pendingId = MutableStateFlow<String?>(null)
    val pendingId: StateFlow<String?> = _pendingId.asStateFlow()

    fun requestById(placeId: String) {
        _pendingId.value = placeId
    }

    fun consumeId() {
        _pendingId.value = null
    }
}
