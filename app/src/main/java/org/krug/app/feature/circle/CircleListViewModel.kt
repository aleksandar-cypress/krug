package org.krug.app.feature.circle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.krug.app.core.auth.AuthRepository
import org.krug.app.core.circle.CircleModel
import org.krug.app.core.circle.CircleRepository

data class CircleListUiState(
    val loading: Boolean = true,
    val circles: List<CircleModel> = emptyList(),
)

@HiltViewModel
class CircleListViewModel @Inject constructor(
    authRepository: AuthRepository,
    circleRepository: CircleRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CircleListUiState())
    val state: StateFlow<CircleListUiState> = _state.asStateFlow()

    init {
        authRepository.observeAuthState()
            .flatMapLatest { user ->
                if (user == null) flowOf(emptyList())
                else circleRepository.observeMyCircles(user.uid)
            }
            .map { circles -> CircleListUiState(loading = false, circles = circles) }
            .onEach { _state.value = it }
            .launchIn(viewModelScope)
    }
}
