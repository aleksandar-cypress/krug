package org.krug.app.feature.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.krug.app.core.auth.AuthRepository
import org.krug.app.core.prefs.LocalPrefs
import timber.log.Timber

sealed interface SplashDecision {
    data object Loading : SplashDecision
    data object SignedOut : SplashDecision
    data object OnboardingPending : SplashDecision
    data object Ready : SplashDecision
}

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val firestore: FirebaseFirestore,
    private val localPrefs: LocalPrefs,
) : ViewModel() {

    private val _decision = MutableStateFlow<SplashDecision>(SplashDecision.Loading)
    val decision: StateFlow<SplashDecision> = _decision.asStateFlow()

    init {
        viewModelScope.launch { decide() }
    }

    private companion object {
        const val SPLASH_TIMEOUT_MS = 5_000L
    }

    private suspend fun decide() {
        val user = authRepository.currentUser
        if (user == null) {
            _decision.value = SplashDecision.SignedOut
            return
        }
        // Local flag short-circuits the Firestore round-trip so onboarding is not re-shown
        // after sign-out/sign-in on the same device (esp. relevant for anonymous test users
        // whose UID changes each session).
        if (localPrefs.onboardingCompleted) {
            _decision.value = SplashDecision.Ready
            return
        }
        val onboardingDone = withTimeoutOrNull(SPLASH_TIMEOUT_MS) {
            try {
                firestore.collection("users").document(user.uid).get().await()
                    .getBoolean("onboardingCompleted") == true
            } catch (e: Exception) {
                Timber.w(e, "Failed to read onboarding flag; assume pending")
                false
            }
        } ?: run {
            Timber.w("Firestore unreachable; assuming onboarding pending")
            false
        }
        if (onboardingDone) localPrefs.onboardingCompleted = true
        _decision.value = if (onboardingDone) SplashDecision.Ready else SplashDecision.OnboardingPending
    }
}
