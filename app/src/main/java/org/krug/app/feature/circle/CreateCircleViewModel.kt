package org.krug.app.feature.circle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.krug.app.core.auth.AuthRepository
import org.krug.app.core.circle.CirclePresets
import org.krug.app.core.circle.CircleRepository
import org.krug.app.core.circle.InviteRepository
import timber.log.Timber

data class CreateCircleUiState(
    val name: String = "",
    val selectedColor: String = CirclePresets.colors.first(),
    val selectedIcon: String = CirclePresets.icons.first(),
    val creating: Boolean = false,
    val nameError: Boolean = false,
    val genericError: String? = null,
    val createdCircleId: String? = null,
    val inviteCode: String? = null,
)

@HiltViewModel
class CreateCircleViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val circleRepository: CircleRepository,
    private val inviteRepository: InviteRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateCircleUiState())
    val state: StateFlow<CreateCircleUiState> = _state.asStateFlow()

    fun setName(value: String) = _state.update { it.copy(name = value, nameError = false) }
    fun setColor(value: String) = _state.update { it.copy(selectedColor = value) }
    fun setIcon(value: String) = _state.update { it.copy(selectedIcon = value) }
    fun clearError() = _state.update { it.copy(genericError = null) }

    fun submit() {
        val trimmed = _state.value.name.trim()
        if (trimmed.isEmpty()) {
            _state.update { it.copy(nameError = true) }
            return
        }
        val uid = authRepository.currentUser?.uid ?: return
        if (_state.value.creating) return
        _state.update { it.copy(creating = true) }
        viewModelScope.launch {
            runCatching {
                val circleId = circleRepository.createCircle(
                    ownerUid = uid,
                    name = trimmed,
                    colorHex = _state.value.selectedColor,
                    iconKey = _state.value.selectedIcon,
                )
                val code = inviteRepository.createInvite(circleId, uid)
                circleId to code
            }
                .onSuccess { (circleId, code) ->
                    _state.update {
                        it.copy(creating = false, createdCircleId = circleId, inviteCode = code)
                    }
                }
                .onFailure { e ->
                    Timber.e(e, "createCircle failed")
                    _state.update { it.copy(creating = false, genericError = "generic") }
                }
        }
    }
}
