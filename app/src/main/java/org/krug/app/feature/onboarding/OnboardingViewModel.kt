package org.krug.app.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.krug.app.core.auth.AuthRepository
import org.krug.app.core.prefs.LocalPrefs
import org.krug.app.core.user.UserRepository
import timber.log.Timber

data class OnboardingUiState(
    val completing: Boolean = false,
    val completed: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val localPrefs: LocalPrefs,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun complete() {
        val uid = authRepository.currentUser?.uid ?: return
        if (_state.value.completing) return
        _state.value = _state.value.copy(completing = true)
        localPrefs.onboardingCompleted = true
        viewModelScope.launch {
            runCatching { userRepository.markOnboardingCompleted(uid) }
                .onFailure { Timber.w(it, "Failed to mark onboardingCompleted") }
            _state.value = OnboardingUiState(completing = false, completed = true)
        }
    }
}
