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
import org.krug.app.core.circle.CircleRepository
import org.krug.app.core.location.LocationRepository
import org.krug.app.core.location.LocationTrackingService
import org.krug.app.core.prefs.LocalPrefs
import org.krug.app.core.sos.SosRepository
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
    val deleting: Boolean = false,
    /** Set kad Firebase Auth.delete() traži recent re-login — UI prikazuje dialog sa CTA. */
    val deleteNeedsReauth: Boolean = false,
    /** Set dok reauth Google chooser radi (spinner umesto CTA). */
    val reauthInProgress: Boolean = false,
    /** Set ako reauth padne (cancel / mismatch / network) — UI prikazuje poruku, dozvoljava retry. */
    val reauthError: Boolean = false,
    /** Roditeljska kontrola — bilo koji krug me je markirao kao dete → sakrij "Obriši nalog". */
    val isChildAnywhere: Boolean = false,
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val circleRepository: CircleRepository,
    private val locationRepository: LocationRepository,
    private val sosRepository: SosRepository,
    private val localPrefs: LocalPrefs,
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
            // Child status — bilo koji krug me je markirao kao dete → sakrij delete.
            circleRepository.observeUserIsChildAnywhere(uid)
                .onEach { isChild ->
                    _state.update { it.copy(isChildAnywhere = isChild) }
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
            val user = authRepository.currentUser
            val uid = user?.uid
            val isAnonymous = user?.isAnonymous == true
            LocationTrackingService.stop(context)
            // Anonimni user-i dobijaju novi uid pri sledećem sign-in-u; stari uid postaje
            // orfan vlasnik krugova. Da ne ostavimo "vhg ima Samsung A37 5G kao Vlasnik
            // koji više ne postoji" stanje, čistimo sve njegove podatke pre sign-out-a.
            // Google sign-in user-i zadržavaju stabilan uid, ne treba im ovo.
            if (isAnonymous && uid != null) {
                Timber.d("Anonymous signOut: cleaning up owned data for uid=$uid")
                runCatching { locationRepository.deleteForUser(uid) }
                    .onFailure { Timber.w(it, "anonymous signOut: delete RTDB location failed") }
                runCatching { sosRepository.clear(uid) }
                    .onFailure { Timber.w(it, "anonymous signOut: clear SOS failed") }
                runCatching { circleRepository.cleanupForDeletedUser(uid) }
                    .onFailure { Timber.w(it, "anonymous signOut: circle cleanup failed") }
                runCatching { userRepository.deleteUser(uid) }
                    .onFailure { Timber.w(it, "anonymous signOut: user doc delete failed") }
            }
            authRepository.signOut(context)
            _state.update { it.copy(signingOut = false, signedOut = true) }
        }
    }

    /**
     * GDPR brisanje naloga — fan-out cleanup pa Firebase Auth delete.
     * Redosled bitno: prvo data (dok je auth.uid još važeći), pa auth.delete().
     */
    fun deleteAccount(context: Context) {
        val uid = authRepository.currentUser?.uid ?: return
        if (_state.value.deleting) return
        // Defensive: dete ne sme da obriše nalog. UI je već sakrio dugme, ovo je extra layer.
        if (_state.value.isChildAnywhere) return
        _state.update { it.copy(deleting = true, deleteNeedsReauth = false) }
        viewModelScope.launch {
            Timber.i("Account delete started uid=%s", uid)
            // 0. Mark pending delete u prefs — ako Auth.delete fail-uje a app crashne pre
            //    nego što user pokrene reauth, SplashViewModel pri sledećem startu vidi
            //    ovaj flag i retry-uje cleanup ili force-uje signOut (sprečava ghost-account).
            localPrefs.pendingDeleteUid = uid
            // 1. Zaustavi FGS odmah — više ne sme da publish-uje.
            LocationTrackingService.stop(context)
            // 2. RTDB cleanup (location + SOS).
            runCatching { locationRepository.deleteForUser(uid) }
                .onFailure { Timber.w(it, "delete RTDB location failed") }
            runCatching { sosRepository.clear(uid) }
                .onFailure { Timber.w(it, "delete RTDB SOS failed") }
            // 3. Firestore: krugovi (vlasnik → obriši ceo, član → ukloni se) + user doc.
            runCatching { circleRepository.cleanupForDeletedUser(uid) }
                .onFailure { Timber.w(it, "circle cleanup failed") }
            runCatching { userRepository.deleteUser(uid) }
                .onFailure { Timber.w(it, "user doc delete failed") }
            // 4. Firebase Auth delete. Ako traži recent re-login, signal-uj UI-u.
            val authDeleted = authRepository.deleteAccount()
            if (!authDeleted) {
                // Pending flag ostaje set — Splash će na sledećem startu retry-ovati ili
                // force-ovati signOut da user ne ostane u inconsistent state-u.
                Timber.i("Account delete requires reauth uid=%s", uid)
                _state.update { it.copy(deleting = false, deleteNeedsReauth = true) }
                return@launch
            }
            // Clean delete uspeo — clear pending flag.
            localPrefs.pendingDeleteUid = null
            // 5. Sign-out cleanup (clear credentials).
            runCatching { authRepository.signOut(context) }
            Timber.i("Account delete completed uid=%s", uid)
            _state.update { it.copy(deleting = false, signedOut = true) }
        }
    }

    fun dismissDeleteReauth() {
        _state.update { it.copy(deleteNeedsReauth = false, reauthError = false) }
    }

    /**
     * One-step reauth + retry delete. Lanšuje Google chooser, traži fresh ID token,
     * pa pozove `user.reauthenticate(...)` i ponovo `authRepository.deleteAccount()`.
     * Ako bilo šta padne, vraća dialog u "needsReauth" state sa error flag-om da user može
     * ponovo da pokuša. Cleanup data (Firestore + RTDB) je već urađen u `deleteAccount` ranije
     * pa ovde samo finishujemo auth.delete + signOut.
     */
    fun reauthAndDelete(context: Context) {
        val uid = authRepository.currentUser?.uid ?: return
        if (_state.value.reauthInProgress) return
        _state.update { it.copy(reauthInProgress = true, reauthError = false) }
        viewModelScope.launch {
            val reauthFailure = authRepository.reauthenticateWithGoogle(context)
            if (reauthFailure != null) {
                Timber.w("Reauth failed reason=%s uid=%s", reauthFailure, uid)
                _state.update {
                    it.copy(reauthInProgress = false, reauthError = true, deleteNeedsReauth = true)
                }
                return@launch
            }
            val authDeleted = authRepository.deleteAccount()
            if (!authDeleted) {
                Timber.w("Reauth succeeded but delete still failed uid=%s", uid)
                _state.update {
                    it.copy(reauthInProgress = false, reauthError = true, deleteNeedsReauth = true)
                }
                return@launch
            }
            localPrefs.pendingDeleteUid = null
            runCatching { authRepository.signOut(context) }
            Timber.i("Account delete completed via reauth uid=%s", uid)
            _state.update {
                it.copy(
                    reauthInProgress = false,
                    reauthError = false,
                    deleteNeedsReauth = false,
                    deleting = false,
                    signedOut = true,
                )
            }
        }
    }
}
