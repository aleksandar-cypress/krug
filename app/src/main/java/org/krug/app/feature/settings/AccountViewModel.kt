package org.krug.app.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.krug.app.core.auth.AuthRepository
import org.krug.app.core.location.LocationTrackingService
import org.krug.app.core.user.UserRepository
import timber.log.Timber

data class AccountUiState(
    val displayName: String = "",
    val nameInput: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val signingOut: Boolean = false,
    val signedOut: Boolean = false,
    val saving: Boolean = false,
    val justSaved: Boolean = false,
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _state: MutableStateFlow<AccountUiState>
    val state: StateFlow<AccountUiState>

    init {
        val u = authRepository.currentUser
        val initialName = u?.displayName.orEmpty()
        _state = MutableStateFlow(
            AccountUiState(
                displayName = initialName,
                nameInput = initialName,
                email = u?.email.orEmpty(),
                photoUrl = u?.photoUrl?.toString(),
            ),
        )
        state = _state.asStateFlow()

        // Live updates iz Firestore-a (npr. ako je name set-ovan ranije).
        val uid = u?.uid
        if (uid != null) {
            userRepository.observeUser(uid)
                .onEach { profile ->
                    val name = profile?.displayName.orEmpty()
                    _state.update {
                        // Only update nameInput if user hasn't typed something different.
                        val newInput = if (it.nameInput == it.displayName) name else it.nameInput
                        it.copy(displayName = name, nameInput = newInput)
                    }
                }
                .launchIn(viewModelScope)
        }
    }

    fun setNameInput(value: String) {
        _state.update { it.copy(nameInput = value.take(40), justSaved = false) }
    }

    fun saveName() {
        val uid = authRepository.currentUser?.uid ?: return
        val trimmed = _state.value.nameInput.trim()
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, justSaved = false) }
        viewModelScope.launch {
            runCatching { userRepository.updateDisplayName(uid, trimmed) }
                .onFailure { Timber.w(it, "Failed to update display name") }
            _state.update {
                it.copy(saving = false, justSaved = true, displayName = trimmed, nameInput = trimmed)
            }
        }
    }

    fun signOut(context: Context) {
        if (_state.value.signingOut) return
        _state.update { it.copy(signingOut = true) }
        viewModelScope.launch {
            LocationTrackingService.stop(context)
            authRepository.signOut(context)
            _state.update { it.copy(signingOut = false, signedOut = true) }
        }
    }
}
