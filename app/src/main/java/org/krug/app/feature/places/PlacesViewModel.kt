package org.krug.app.feature.places

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.krug.app.core.circle.CircleRepository
import org.krug.app.core.directions.GeocodingRepository
import org.krug.app.core.location.LocationModel
import org.krug.app.core.location.LocationRepository
import org.krug.app.core.places.PlaceModel
import org.krug.app.core.places.PlaceRepository
import org.krug.app.core.user.UserRepository
import timber.log.Timber

data class PlacesUiState(
    val places: List<PlaceModel> = emptyList(),
    val currentLat: Double? = null,
    val currentLng: Double? = null,
    val saving: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val sheetOpen: Boolean = false,
    val editingPlace: PlaceModel? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlacesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val placeRepository: PlaceRepository,
    private val locationRepository: LocationRepository,
    private val circleRepository: CircleRepository,
    private val userRepository: UserRepository,
    private val geocodingRepository: GeocodingRepository,
    private val auth: FirebaseAuth,
) : ViewModel() {

    private val _searchResults = MutableStateFlow<List<GeocodingRepository.Suggestion>>(emptyList())
    val searchResults: StateFlow<List<GeocodingRepository.Suggestion>> = _searchResults.asStateFlow()

    private var searchJob: kotlinx.coroutines.Job? = null

    fun searchAddress(query: String) {
        searchJob?.cancel()
        if (query.trim().length < 3) {
            _searchResults.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            // Debounce 300ms — user tipka, ne bombardujmo Mapbox API-jem.
            kotlinx.coroutines.delay(300)
            val proxLat = _state.value.currentLat
            val proxLng = _state.value.currentLng
            _searchResults.value = geocodingRepository.search(query, proxLat, proxLng)
        }
    }

    fun clearSearchResults() {
        searchJob?.cancel()
        _searchResults.value = emptyList()
    }

    private val circleId: String = requireNotNull(savedStateHandle["circleId"]) {
        "PlacesViewModel requires circleId"
    }

    private val _state = MutableStateFlow(PlacesUiState())
    val state: StateFlow<PlacesUiState> = _state.asStateFlow()

    val places: StateFlow<List<PlaceModel>> = placeRepository.observePlaces(circleId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentEvents: StateFlow<List<org.krug.app.core.places.PlaceEventModel>> =
        placeRepository.observeRecentEvents(circleId, limit = 20)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Trenutno u mestu — mapa placeId → lista imena članova koji su unutar radius-a.
     * Presence check: computeaj distancu member.location od center-a place-a, ako je
     * <= radius i location je "fresh" (unutar 15min), član je "unutra".
     */
    val presenceByPlace: StateFlow<Map<String, List<String>>> = run {
        circleRepository.observeMembersUids(circleId).flatMapLatest { uids ->
            if (uids.isEmpty()) flowOf(emptyMap())
            else combine(
                uids.map { uid ->
                    combine(
                        locationRepository.observe(uid),
                        userRepository.observeUser(uid),
                    ) { loc, user -> Triple(uid, loc, user?.displayName.orEmpty()) }
                },
            ) { array -> array.toList() }.combine(places) { locs, plcs ->
                val now = System.currentTimeMillis()
                val result = mutableMapOf<String, MutableList<String>>()
                plcs.forEach { p ->
                    val insideNames = mutableListOf<String>()
                    locs.forEach { (uid, loc, name) ->
                        if (loc == null || (now - loc.updatedAt) > 15 * 60_000L) return@forEach
                        val dist = FloatArray(1)
                        android.location.Location.distanceBetween(
                            loc.lat, loc.lng, p.lat, p.lng, dist,
                        )
                        if (dist[0] <= p.radius) {
                            insideNames.add(name.ifBlank { uid.take(6) })
                        }
                    }
                    if (insideNames.isNotEmpty()) result[p.id] = insideNames
                }
                result.toMap()
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    }

    init {
        viewModelScope.launch {
            places.collect { list ->
                // Prvi emit iz Firestore znači da smo bar jednom primili snapshot —
                // razdvaja "still loading" od "loaded, empty" za empty state UX.
                _state.value = _state.value.copy(places = list, loaded = true)
            }
        }
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            locationRepository.observe(uid).collect { loc ->
                if (loc != null) {
                    _state.value = _state.value.copy(
                        currentLat = loc.lat, currentLng = loc.lng,
                    )
                }
            }
        }
    }

    fun openAddSheet() {
        _state.value = _state.value.copy(sheetOpen = true, editingPlace = null, error = null)
    }

    fun openEditSheet(place: PlaceModel) {
        _state.value = _state.value.copy(sheetOpen = true, editingPlace = place, error = null)
    }

    fun closeSheet() {
        _state.value = _state.value.copy(sheetOpen = false, editingPlace = null, error = null)
    }

    fun createPlace(
        name: String,
        lat: Double,
        lng: Double,
        radius: Int,
        category: org.krug.app.core.places.PlaceCategory,
        onSuccess: () -> Unit,
    ) {
        val uid = auth.currentUser?.uid ?: return
        // Guard protiv brzog double-tap-a: prva launch pokrene coroutine ali `saving=true`
        // se postavlja unutar njega (asinhrono). Drugi tap koji stigne pre te izmene bi
        // opet launch-ovao paralelni pisač → duplicate Firestore doc za isti place. Return
        // odmah ako je već save u toku.
        if (_state.value.saving) return
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            _state.value = _state.value.copy(error = "Ime ne može biti prazno")
            return
        }
        if (_state.value.places.size >= PlaceModel.FREE_TIER_MAX_PER_CIRCLE) {
            _state.value = _state.value.copy(
                error = "Limit dostignut (${PlaceModel.FREE_TIER_MAX_PER_CIRCLE} mesta)",
            )
            return
        }
        _state.value = _state.value.copy(saving = true, error = null)
        viewModelScope.launch {
            runCatching {
                placeRepository.createPlace(circleId, uid, trimmed, lat, lng, radius, category)
            }.onSuccess {
                _state.value = _state.value.copy(
                    saving = false, sheetOpen = false, editingPlace = null,
                )
                onSuccess()
            }.onFailure { e ->
                Timber.w(e, "createPlace failed")
                _state.value = _state.value.copy(saving = false, error = e.message)
            }
        }
    }

    fun updatePlace(
        placeId: String,
        name: String,
        radius: Int,
        category: org.krug.app.core.places.PlaceCategory,
        onSuccess: () -> Unit,
    ) {
        // Isti double-tap guard kao createPlace — sprečava paralelne update-e.
        if (_state.value.saving) return
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            _state.value = _state.value.copy(error = "Ime ne može biti prazno")
            return
        }
        _state.value = _state.value.copy(saving = true, error = null)
        viewModelScope.launch {
            runCatching {
                placeRepository.updatePlace(circleId, placeId, trimmed, radius, category)
            }.onSuccess {
                _state.value = _state.value.copy(
                    saving = false, sheetOpen = false, editingPlace = null,
                )
                onSuccess()
            }.onFailure { e ->
                Timber.w(e, "updatePlace failed")
                _state.value = _state.value.copy(saving = false, error = e.message)
            }
        }
    }

    fun deletePlace(placeId: String) {
        viewModelScope.launch {
            runCatching { placeRepository.deletePlace(circleId, placeId) }
                .onFailure { Timber.w(it, "deletePlace failed") }
        }
    }
}
