package org.krug.app.feature.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.krug.app.R
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
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onEvent(event: AuthEvent) {
        when (event) {
            is AuthEvent.GoogleSignInClicked -> signIn { authRepository.signInWithGoogle(event.activityContext) }
            AuthEvent.AnonymousSignInClicked -> signIn { authRepository.signInAnonymously() }
            AuthEvent.EmailSignInClicked -> _state.update {
                it.copy(errorMessage = appContext.getString(R.string.auth_email_coming_soon))
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

    private fun SignInResult.Reason.toMessage(): String = appContext.getString(
        when (this) {
            SignInResult.Reason.Cancelled -> R.string.auth_error_cancelled
            SignInResult.Reason.NoGoogleAccount -> R.string.auth_error_no_google_account
            SignInResult.Reason.Network -> R.string.auth_error_network
            SignInResult.Reason.ProviderDisabled -> R.string.auth_error_provider_disabled
            SignInResult.Reason.AccountDisabled -> R.string.auth_error_account_disabled
            SignInResult.Reason.InvalidCredential -> R.string.auth_error_invalid_credential
            SignInResult.Reason.Unknown -> R.string.auth_error_unknown
        },
    )
}
