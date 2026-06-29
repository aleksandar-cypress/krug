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
import org.krug.app.core.circle.CircleRepository
import org.krug.app.core.location.LocationRepository
import org.krug.app.core.permissions.PermissionUtils
import org.krug.app.core.prefs.LocalPrefs
import org.krug.app.core.sos.SosRepository
import org.krug.app.core.splash.SplashGate
import org.krug.app.core.user.UserRepository
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
    private val circleRepository: CircleRepository,
    private val locationRepository: LocationRepository,
    private val sosRepository: SosRepository,
    private val userRepository: UserRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _decision = MutableStateFlow<SplashDecision>(SplashDecision.Loading)
    val decision: StateFlow<SplashDecision> = _decision.asStateFlow()

    init {
        viewModelScope.launch {
            // try/finally — defensive: ako decide() throw-uje neuhvaćen exception (npr.
            // korumpiran SharedPrefs ili neka neočekivana NPE), SplashGate.ready bi ostao
            // false zauvek → user zaglavljen na beloj splash strani. Sa finally, gate se
            // uvek oslobađa, a fallback decision (SignedOut) vodi user-a u Auth flow.
            try {
                decide()
            } catch (e: Throwable) {
                Timber.e(e, "Splash decide failed — fallback to SignedOut")
                _decision.value = SplashDecision.SignedOut
            } finally {
                SplashGate.ready.set(true)
            }
        }
    }

    private companion object {
        const val SPLASH_TIMEOUT_MS = 5_000L
        /** Hard cap pending-delete recovery — sprečava infinite splash ako Firestore/RTDB visi. */
        const val PENDING_DELETE_TIMEOUT_MS = 10_000L
    }

    private suspend fun decide() {
        val user = authRepository.currentUser
        if (user == null) {
            // Cleanup pending-delete entry ako je bilo signed-out (može biti orfan iz starog
            // app verzije kad smo upisali flag bez parnjak signOut-a).
            if (localPrefs.pendingDeleteUid != null) localPrefs.pendingDeleteUid = null
            _decision.value = SplashDecision.SignedOut
            return
        }

        // GDPR ghost-account recovery — ako je trenutni uid u pending-delete prefs-u,
        // znači da je prošli delete-account ostao na pola: data obrisana, auth-delete
        // failed sa "needs reauth", user nije rešio reauth. Sada retry-ujemo cleanup
        // (idempotent — obrisani docs ostaju obrisani) pa probamo Auth.delete ponovo.
        // Ako i dalje fail, force-ujemo signOut da user ne nastavi sa ghost stanjem
        // (data je svakako obrisana — Settings će sve videti prazno).
        val pendingDelete = localPrefs.pendingDeleteUid
        if (pendingDelete != null && pendingDelete == user.uid) {
            Timber.w("Splash: completing pending delete for uid=%s", user.uid)
            // Hard timeout oko celog cleanup-a — bez ovog, Firestore/RTDB down može da
            // visi Splash zauvek (cleanup poziva 4 await() pre Auth.delete-a). 10s daje
            // dovoljno za normalan network, a sprečava infinite splash ako server pada.
            // Idempotent operacije — naredni boot će se ponoviti ako se ovaj prekine.
            val recovered = withTimeoutOrNull(PENDING_DELETE_TIMEOUT_MS) {
                runCatching { locationRepository.deleteForUser(user.uid) }
                    .onFailure { Timber.w(it, "pending-delete: RTDB location") }
                runCatching { sosRepository.clear(user.uid) }
                    .onFailure { Timber.w(it, "pending-delete: RTDB SOS") }
                runCatching { circleRepository.cleanupForDeletedUser(user.uid) }
                    .onFailure { Timber.w(it, "pending-delete: circles") }
                runCatching { userRepository.deleteUser(user.uid) }
                    .onFailure { Timber.w(it, "pending-delete: user doc") }
                authRepository.deleteAccount()
            }
            when (recovered) {
                true -> {
                    localPrefs.pendingDeleteUid = null
                    localPrefs.clearForAccountReset()
                    _decision.value = SplashDecision.SignedOut
                    return
                }
                false -> {
                    // Auth.delete i dalje fail (reauth required). Sign-out user-a — sledeći
                    // sign-in će dobiti čist state (upsertOnSignIn pravi nov users doc), a
                    // ghost u Firebase Auth-u će biti orfan dok user opet ne pokrene delete.
                    Timber.w("Splash: Auth.delete still fails, forcing signOut")
                    runCatching { authRepository.signOut(context) }
                    localPrefs.pendingDeleteUid = null
                    localPrefs.clearForAccountReset()
                    _decision.value = SplashDecision.SignedOut
                    return
                }
                null -> {
                    // Cleanup timeout-ovao. NE clear-uj pending flag — sledeći start će
                    // probati opet. Ali NE drži user-a na Splash-u — pusti ga dalje, makar
                    // sa orphan auth-om; UI će raditi sa praznim podacima koje smo već
                    // obrisali (krugovi prazni, users doc nema).
                    Timber.w("Splash: pending-delete cleanup timed out, proceeding with current session")
                }
            }
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
