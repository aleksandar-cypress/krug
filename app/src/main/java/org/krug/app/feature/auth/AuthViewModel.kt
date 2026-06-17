package org.krug.app.feature.auth

import android.content.Context
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
import org.krug.app.core.auth.SignInResult

data class AuthUiState(
    val isSigningIn: Boolean = false,
    val errorMessage: String? = null,
    val signedIn: Boolean = false,
)

sealed interface AuthEvent {
    data class GoogleSignInClicked(val activityContext: Context) : AuthEvent
    data object EmailSignInClicked : AuthEvent
    data object AnonymousSignInClicked : AuthEvent
    data object ErrorShown : AuthEvent
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onEvent(event: AuthEvent) {
        when (event) {
            is AuthEvent.GoogleSignInClicked -> signIn { authRepository.signInWithGoogle(event.activityContext) }
            AuthEvent.AnonymousSignInClicked -> signIn { authRepository.signInAnonymously() }
            AuthEvent.EmailSignInClicked -> _state.update {
                it.copy(errorMessage = "Email sign-in dolazi uskoro.")
            }
            AuthEvent.ErrorShown -> _state.update { it.copy(errorMessage = null) }
        }
    }

    private fun signIn(block: suspend () -> SignInResult) {
        if (_state.value.isSigningIn) return
        _state.update { it.copy(isSigningIn = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = block()) {
                is SignInResult.Success -> _state.update {
                    it.copy(isSigningIn = false, signedIn = true)
                }
                is SignInResult.Failure -> _state.update {
                    it.copy(isSigningIn = false, errorMessage = result.reason.toMessage())
                }
            }
        }
    }

    private fun SignInResult.Reason.toMessage(): String = when (this) {
        SignInResult.Reason.Cancelled -> "Prijava otkazana."
        SignInResult.Reason.NoGoogleAccount -> "Nije pronađen Google nalog na uređaju."
        SignInResult.Reason.Network -> "Mrežna greška, pokušaj ponovo."
        SignInResult.Reason.ProviderDisabled -> "Anonimna prijava nije omogućena u Firebase Console-u."
        SignInResult.Reason.Unknown -> "Nešto je pošlo po zlu. Pokušaj ponovo."
    }
}
