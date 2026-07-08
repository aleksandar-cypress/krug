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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.krug.app.core.circle.CircleRepository
import org.krug.app.core.directions.GeocodingRepository
import org.krug.app.core.location.LocationHistoryRepository
import org.krug.app.core.location.LocationModel
import org.krug.app.core.location.LocationRepository
import org.krug.app.core.places.PlaceModel
import org.krug.app.core.places.PlaceRepository
import org.krug.app.core.places.PlaceSuggestion
import org.krug.app.core.places.detectPlaceSuggestions
import org.krug.app.core.user.UserRepository
import timber.log.Timber

data class PlacesUiState(
    val places: List<PlaceModel> = emptyList(),
    val currentLat: Double? = null,
    val currentLng: Double? = null,
    val saving: Boolean = false,
    val loaded: Boolean = false,
    val refreshing: Boolean = false,
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
    private val historyRepository: LocationHistoryRepository,
    private val auth: FirebaseAuth,
) : ViewModel() {

    /**
     * Search state — distinguisati "u toku", "greska", "prazno", "ima rezultata". Bez ovog:
     * greska mreze bi izgledala kao "no results" i user bi mislio da adresa ne postoji.
     */
    sealed class SearchState {
        object Idle : SearchState()
        object Loading : SearchState()
        object Error : SearchState()
        object Empty : SearchState()
        data class Success(val suggestions: List<GeocodingRepository.Suggestion>) : SearchState()
    }

    private val _searchState = MutableStateFlow<SearchState>(SearchState.Idle)
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    private var searchJob: kotlinx.coroutines.Job? = null
    private var lastQuery: String = ""

    fun searchAddress(query: String) {
        searchJob?.cancel()
        lastQuery = query
        if (query.trim().length < 3) {
            _searchState.value = SearchState.Idle
            return
        }
        searchJob = viewModelScope.launch {
            // Debounce 300ms — user tipka, ne bombardujmo Mapbox API-jem.
            kotlinx.coroutines.delay(300)
            _searchState.value = SearchState.Loading
            val proxLat = _state.value.currentLat
            val proxLng = _state.value.currentLng
            _searchState.value = when (val r = geocodingRepository.search(query, proxLat, proxLng)) {
                is GeocodingRepository.SearchResult.Success -> SearchState.Success(r.suggestions)
                GeocodingRepository.SearchResult.Empty -> SearchState.Empty
                GeocodingRepository.SearchResult.Error -> SearchState.Error
            }
        }
    }

    /** Ponovi zadnju search — koristi se "Try again" akcijom u UI-ju kad state = Error. */
    fun retrySearch() {
        if (lastQuery.isNotBlank()) searchAddress(lastQuery)
    }

    // Place suggestions — top hotspot iz zadnjih 7d self history-ja. Kompajlira se
    // lazy prvi put kad se PlacesScreen otvori (loadSuggestions() poziv). Sa
    // dismiss-om, kod ostaje u memoriji ali UI ne pokazuje ovaj session.
    private val _suggestions = MutableStateFlow<List<PlaceSuggestion>>(emptyList())
    val suggestions: StateFlow<List<PlaceSuggestion>> = _suggestions.asStateFlow()
    private var suggestionsLoaded = false
    private val dismissedSuggestions = mutableSetOf<String>() // key = "lat,lng" rounded

    fun loadSuggestions() {
        if (suggestionsLoaded) return
        suggestionsLoaded = true
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            val sinceMs = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
            val history = historyRepository.queryHistorySince(uid, sinceMs)
            val existing = _state.value.places
            val detected = detectPlaceSuggestions(history, existing, maxSuggestions = 2)
                .filterNot { s -> keyForSuggestion(s) in dismissedSuggestions }
            _suggestions.value = detected
        }
    }

    fun dismissSuggestion(s: PlaceSuggestion) {
        dismissedSuggestions.add(keyForSuggestion(s))
        _suggestions.value = _suggestions.value.filterNot { it === s }
    }

    private fun keyForSuggestion(s: PlaceSuggestion): String =
        "%.4f,%.4f".format(s.lat, s.lng)

    fun clearSearchResults() {
        searchJob?.cancel()
        _searchState.value = SearchState.Idle
        lastQuery = ""
    }

    private val circleId: String = requireNotNull(savedStateHandle["circleId"]) {
        "PlacesViewModel requires circleId"
    }

    private val _state = MutableStateFlow(PlacesUiState())
    val state: StateFlow<PlacesUiState> = _state.asStateFlow()

    /**
     * Pull-to-refresh gesture handler. Firestore snapshot listener već isporučuje updates
     * real-time (bez potrebe za pull-to-refresh) — ali user mental model je "povuci pa se
     * osveži". Pružamo mu vizuelno UX feedback (spinner ~700ms) i force-trigger recompose
     * kroz `places` StateFlow re-emit. Ne pravimo novi Firestore fetch — nema smisla,
     * imamo real-time listener.
     */
    fun refresh() {
        if (_state.value.refreshing) return
        _state.value = _state.value.copy(refreshing = true)
        viewModelScope.launch {
            kotlinx.coroutines.delay(700)
            _state.value = _state.value.copy(refreshing = false)
        }
    }

    val places: StateFlow<List<PlaceModel>> = placeRepository.observePlaces(circleId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentEvents: StateFlow<List<org.krug.app.core.places.PlaceEventModel>> =
        placeRepository.observeRecentEvents(circleId, limit = 20)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Mapa uid → displayName za sve članove tekućeg kruga. Koristi se u UI-ju za
     * prikazivanje ko je kreirao place (`place.createdBy` je userId). Emit-uje se
     * čim se lista članova ili user record neki od njih promeni.
     */
    val memberNamesByUid: StateFlow<Map<String, String>> =
        circleRepository.observeMembersUids(circleId).flatMapLatest { uids ->
            if (uids.isEmpty()) flowOf(emptyMap())
            else combine(
                uids.map { uid ->
                    userRepository.observeUser(uid)
                        .map { user -> uid to (user?.displayName.orEmpty()) }
                },
            ) { arr -> arr.toList().toMap() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

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
