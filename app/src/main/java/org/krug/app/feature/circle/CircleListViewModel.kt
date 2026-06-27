package org.krug.app.feature.circle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.krug.app.core.auth.AuthRepository
import org.krug.app.core.circle.CircleModel
import org.krug.app.core.circle.CircleRepository

data class CircleListUiState(
    val loading: Boolean = true,
    val circles: List<CircleModel> = emptyList(),
    /** Razdvaja "user nema krugove" od "Firestore down" — UI prikazuje retry banner. */
    val error: Boolean = false,
    /** Pull-to-refresh aktivan — UI prikazuje circular indicator dok ne mine. */
    val refreshing: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CircleListViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val circleRepository: CircleRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CircleListUiState())
    val state: StateFlow<CircleListUiState> = _state.asStateFlow()

    // Refresh trigger: kad user pullne, emituje novi timestamp koji flatMapLatest
    // koristi kao re-subscribe signal — Firestore observer se restartuje (relevantno
    // u retkim slučajevima kad SDK connection zaglavi posle backoff-a).
    private val refreshTrigger = MutableStateFlow(0L)

    init {
        combine(authRepository.observeAuthState(), refreshTrigger) { user, _ -> user }
            .flatMapLatest { user ->
                if (user == null) {
                    flowOf(emptyList<CircleModel>() to null)
                } else {
                    combine(
                        circleRepository.observeMyCircles(user.uid),
                        circleRepository.lastSnapshotError,
                    ) { circles, error -> circles to error }
                }
            }
            .onEach { (circles, error) ->
                _state.value = _state.value.copy(
                    loading = false,
                    circles = circles,
                    error = error != null && circles.isEmpty(),
                )
            }
            .launchIn(viewModelScope)
    }

    fun refresh() {
        if (_state.value.refreshing) return
        _state.value = _state.value.copy(refreshing = true)
        refreshTrigger.value = System.currentTimeMillis()
        viewModelScope.launch {
            // Minimum 600ms vidljive animacije — bez ovog, Firestore snapshot se vrati
            // za <50ms iz keša i spinner samo „blinkne". Korisnik ne vidi feedback.
            delay(600L)
            _state.value = _state.value.copy(refreshing = false)
        }
    }
}
