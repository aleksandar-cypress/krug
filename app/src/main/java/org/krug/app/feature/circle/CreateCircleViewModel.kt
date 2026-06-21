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
import org.krug.app.core.util.capitalizeFirstLetter
import timber.log.Timber

data class CreateCircleUiState(
    val name: String = "",
    val selectedColor: String = CirclePresets.colors.first(),
    val selectedIcon: String = CirclePresets.icons.first(),
    val creating: Boolean = false,
    val nameError: Boolean = false,
    /** True ako user već ima krug sa istim imenom — UI prikazuje "Već imaš krug sa tim imenom". */
    val duplicateError: Boolean = false,
    val genericError: String? = null,
    val createdCircleId: String? = null,
)

@HiltViewModel
class CreateCircleViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val circleRepository: CircleRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateCircleUiState())
    val state: StateFlow<CreateCircleUiState> = _state.asStateFlow()

    fun setName(value: String) = _state.update {
        // Bilo kakva izmena imena resetuje nameError i duplicateError — user dobija
        // čisto polje dok kuca.
        it.copy(name = value.take(NAME_MAX_LENGTH), nameError = false, duplicateError = false)
    }
    fun setColor(value: String) = _state.update { it.copy(selectedColor = value) }
    fun setIcon(value: String) = _state.update { it.copy(selectedIcon = value) }
    fun clearError() = _state.update { it.copy(genericError = null) }

    fun submit() {
        val trimmed = _state.value.name.trim().capitalizeFirstLetter()
        if (trimmed.isEmpty()) {
            _state.update { it.copy(nameError = true) }
            return
        }
        val uid = authRepository.currentUser?.uid ?: return
        if (_state.value.creating) return
        _state.update { it.copy(creating = true, duplicateError = false) }
        viewModelScope.launch {
            // Spreči duplikat — user već poseduje krug sa istim imenom.
            val isDuplicate = runCatching {
                circleRepository.hasOwnedCircleNamed(uid, trimmed)
            }.getOrDefault(false)
            if (isDuplicate) {
                _state.update { it.copy(creating = false, duplicateError = true) }
                return@launch
            }
            runCatching {
                circleRepository.createCircle(
                    ownerUid = uid,
                    name = trimmed.take(NAME_MAX_LENGTH),
                    colorHex = _state.value.selectedColor,
                    iconKey = _state.value.selectedIcon,
                )
            }
                .onSuccess { circleId ->
                    _state.update { it.copy(creating = false, createdCircleId = circleId) }
                }
                .onFailure { e ->
                    Timber.e(e, "createCircle failed")
                    _state.update { it.copy(creating = false, genericError = "generic") }
                }
        }
    }

    companion object {
        const val NAME_MAX_LENGTH = 20
    }
}
