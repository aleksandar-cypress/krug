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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.krug.app.core.location.LocationRepository
import org.krug.app.core.places.PlaceModel
import org.krug.app.core.places.PlaceRepository
import timber.log.Timber

data class PlacesUiState(
    val places: List<PlaceModel> = emptyList(),
    val currentLat: Double? = null,
    val currentLng: Double? = null,
    val saving: Boolean = false,
    val error: String? = null,
    val editingPlace: PlaceModel? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlacesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val placeRepository: PlaceRepository,
    private val locationRepository: LocationRepository,
    private val auth: FirebaseAuth,
) : ViewModel() {

    private val circleId: String = requireNotNull(savedStateHandle["circleId"]) {
        "PlacesViewModel requires circleId"
    }

    private val _state = MutableStateFlow(PlacesUiState())
    val state: StateFlow<PlacesUiState> = _state.asStateFlow()

    val places: StateFlow<List<PlaceModel>> = placeRepository.observePlaces(circleId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            places.collect { list ->
                _state.value = _state.value.copy(places = list)
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

    fun startEdit(place: PlaceModel?) {
        _state.value = _state.value.copy(editingPlace = place, error = null)
    }

    fun createPlace(name: String, lat: Double, lng: Double, radius: Int, onSuccess: () -> Unit) {
        val uid = auth.currentUser?.uid ?: return
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
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, error = null)
            runCatching {
                placeRepository.createPlace(circleId, uid, trimmed, lat, lng, radius)
            }.onSuccess {
                _state.value = _state.value.copy(saving = false, editingPlace = null)
                onSuccess()
            }.onFailure { e ->
                Timber.w(e, "createPlace failed")
                _state.value = _state.value.copy(saving = false, error = e.message)
            }
        }
    }

    fun updatePlace(placeId: String, name: String, radius: Int, onSuccess: () -> Unit) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            _state.value = _state.value.copy(error = "Ime ne može biti prazno")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, error = null)
            runCatching {
                placeRepository.updatePlace(circleId, placeId, trimmed, radius)
            }.onSuccess {
                _state.value = _state.value.copy(saving = false, editingPlace = null)
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
