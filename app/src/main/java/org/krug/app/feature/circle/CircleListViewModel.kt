package org.krug.app.feature.circle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.krug.app.core.auth.AuthRepository
import org.krug.app.core.circle.CircleModel
import org.krug.app.core.circle.CircleRepository

data class CircleListUiState(
    val loading: Boolean = true,
    val circles: List<CircleModel> = emptyList(),
    /** Razdvaja "user nema krugove" od "Firestore down" — UI prikazuje retry banner. */
    val error: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CircleListViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val circleRepository: CircleRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CircleListUiState())
    val state: StateFlow<CircleListUiState> = _state.asStateFlow()

    init {
        authRepository.observeAuthState()
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
                _state.value = CircleListUiState(
                    loading = false,
                    circles = circles,
                    error = error != null && circles.isEmpty(),
                )
            }
            .launchIn(viewModelScope)
    }
}
