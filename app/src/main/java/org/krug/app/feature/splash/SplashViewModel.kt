package org.krug.app.feature.splash

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.krug.app.core.auth.AuthRepository
import org.krug.app.core.permissions.PermissionUtils
import org.krug.app.core.prefs.LocalPrefs
import org.krug.app.core.splash.SplashGate
import timber.log.Timber

sealed interface SplashDecision {
    data object Loading : SplashDecision
    data object SignedOut : SplashDecision
    /**
     * `skipIntro` = true ako je user već završio onboarding ranije (Firestore flag) ali su
     * permission-i izgubljeni (reinstall, OEM revoke). Onboarding tada počinje direktno
     * od LOCATION ekrana, bez INTRO welcome-a.
     */
    data class OnboardingPending(val skipIntro: Boolean) : SplashDecision
    data object Ready : SplashDecision
}

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val firestore: FirebaseFirestore,
    private val localPrefs: LocalPrefs,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _decision = MutableStateFlow<SplashDecision>(SplashDecision.Loading)
    val decision: StateFlow<SplashDecision> = _decision.asStateFlow()

    init {
        viewModelScope.launch {
            decide()
            // Pusti sistemski splash da se dismiss-uje sad kad imamo decision.
            SplashGate.ready.set(true)
        }
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
        // OS permissions se brišu uninstall-om dok Firestore i lokalni flag i dalje pamte
        // da je onboarding završen. Bez ove provere user posle reinstall-a sleće na Map
        // bez location permission-a — FGS ne radi, ništa se ne publishuje, refresh dugme
        // tiho ne radi ništa. Samo foreground location je obavezan; notifikacije su
        // optional (onboarding ima "Preskoči" dugme) pa ih ne smemo tražiti ovde —
        // inače user koji je svesno odbio notifikacije završi u beskonačnoj onboarding
        // petlji.
        // Lazy: ako lokalni flag kaže "već prošao", verujemo (sprečava Firestore round-trip
        // kod svakog sign-in-a u sesiji). Firestore se proverava samo ako lokalni nije set.
        val onboardingDoneLocal = localPrefs.onboardingCompleted
        val onboardingDone = if (onboardingDoneLocal) true else withTimeoutOrNull(SPLASH_TIMEOUT_MS) {
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

        // OS permissions se brišu uninstall-om dok Firestore i lokalni flag i dalje pamte
        // da je onboarding završen. Ako nema permission-a → idemo u onboarding, ali ako je
        // user već prošao onboarding ranije (returning user posle reinstall-a), skip INTRO
        // welcome ekran — počinjemo odmah od LOCATION grant-a.
        // Samo foreground location je obavezan; notifikacije su optional (mogu kasnije).
        if (!PermissionUtils.hasForegroundLocation(context)) {
            Timber.d("Splash: foreground location missing — onboarding (skipIntro=$onboardingDone)")
            _decision.value = SplashDecision.OnboardingPending(skipIntro = onboardingDone)
            return
        }
        _decision.value = if (onboardingDone) SplashDecision.Ready
        else SplashDecision.OnboardingPending(skipIntro = false)
    }
}
