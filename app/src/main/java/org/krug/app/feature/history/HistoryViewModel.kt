package org.krug.app.feature.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import org.krug.app.core.circle.CircleRepository
import org.krug.app.core.location.LocationHistoryPoint
import org.krug.app.core.location.LocationHistoryRepository
import org.krug.app.core.places.PlaceModel
import org.krug.app.core.places.PlaceRepository
import org.krug.app.core.prefs.LocalPrefs
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach

data class HistoryDayRange(val fromMs: Long, val toMs: Long)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val historyRepository: LocationHistoryRepository,
    private val placeRepository: PlaceRepository,
    private val circleRepository: CircleRepository,
    private val localPrefs: LocalPrefs,
    private val auth: FirebaseAuth,
) : ViewModel() {

    val uid: String = requireNotNull(savedStateHandle["uid"])
    val displayName: String = savedStateHandle["displayName"] ?: ""

    private val _selectedDay = MutableStateFlow(startOfDay(System.currentTimeMillis()))
    val selectedDay: StateFlow<HistoryDayRange> = _selectedDay.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    val points: StateFlow<List<LocationHistoryPoint>> = selectedDay.flatMapLatest { range ->
        _loaded.value = false
        historyRepository.observeHistory(uid, range.fromMs, range.toMs)
            .onEach { _loaded.value = true }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Places iz trenutno aktivnog kruga — render kao statični pinovi iznad trag-a.
     * Konzistentno sa MapViewModel.effectiveActiveCircleId — ako raw activeCircleId nije
     * postavljen (fresh user), pada na prvi krug. Bez ovog, history-jev overlay je bio
     * empty za usere koji nisu explicit setActiveCircle pozvali, iako sami vide members
     * i sopstveni trag na mapi.
     */
    private val selfUid: String? = auth.currentUser?.uid
    private val effectiveActiveCircleId: Flow<String?> = if (selfUid == null) {
        flowOf(null)
    } else {
        circleRepository.observeMyCircles(selfUid).combine(localPrefs.activeCircleIdFlow) { circles, stored ->
            (circles.firstOrNull { it.id == stored } ?: circles.firstOrNull())?.id
        }
    }
    val activePlaces: StateFlow<List<PlaceModel>> = effectiveActiveCircleId
        .flatMapLatest { activeId ->
            if (activeId.isNullOrBlank()) flowOf(emptyList())
            else placeRepository.observePlaces(activeId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** dayOffset: 0 = danas, -1 = juče, -2 = pretpr., itd. Max 30 dana unazad. */
    fun setDayOffset(offset: Int) {
        val clamped = offset.coerceIn(-29, 0)
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, clamped)
        }
        _selectedDay.value = startOfDay(cal.timeInMillis)
    }

    private fun startOfDay(ms: Long): HistoryDayRange {
        val cal = Calendar.getInstance().apply {
            timeInMillis = ms
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val from = cal.timeInMillis
        val to = from + 24 * 60 * 60_000L - 1
        return HistoryDayRange(from, to)
    }
}
