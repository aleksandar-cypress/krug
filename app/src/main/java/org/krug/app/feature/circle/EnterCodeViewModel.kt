package org.krug.app.feature.circle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.krug.app.core.auth.AuthRepository
import org.krug.app.core.circle.CircleRepository
import org.krug.app.core.circle.InviteRepository
import org.krug.app.core.circle.JoinResult
import org.krug.app.core.prefs.LocalPrefs

data class EnterCodeUiState(
    val code: String = "",
    val joining: Boolean = false,
    val errorRes: Int? = null,
    val joinedCircleId: String? = null,
    /**
     * Sekunde preostale do isteka cooldown-a; 0 = može da se submit-uje. UI koristi za
     * prikaz "Sačekaj X s" countdown-a + disable submit dugmeta. ViewModel tick-uje
     * vrednost na 1Hz dok ima cooldown-a.
     */
    val cooldownRemainingSec: Int = 0,
)

@HiltViewModel
class EnterCodeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val inviteRepository: InviteRepository,
    private val circleRepository: CircleRepository,
    private val localPrefs: LocalPrefs,
) : ViewModel() {

    private val _state = MutableStateFlow(EnterCodeUiState())
    val state: StateFlow<EnterCodeUiState> = _state.asStateFlow()

    /**
     * Double-tap protection — StateFlow read+update između if-check-a i update-a nije
     * atomic. AtomicBoolean.compareAndSet eliminiše race kad user brzo dvaput tapne
     * "Pridruži se" pa se dva acceptInvite coroutine-a paralelno startuju.
     */
    private val submitInFlight = AtomicBoolean(false)

    /**
     * Exponential backoff za brute-force pokušaje invite kod-a. Base36 alphanumeric,
     * 6 znakova = 36^6 ≈ 2.17B mogućnosti. Bez cooldown-a, napadač sa svojim auth-om
     * može da pokuša ~100 kod/s i pogodi 50% space-a za ~250 dana (upgrade sa ~3h
     * kod pure-digit 6-char šeme). Sa cooldown-om 1s/2s/5s/15s posle 1./2./3./4.+
     * fail-a, brute-force postaje potpuno neisplativ. Reset na uspeh.
     */
    @Volatile private var consecutiveFailures: Int = 0
    @Volatile private var cooldownUntilMs: Long = 0L

    fun setCode(value: String) {
        val filtered = value.uppercase()
            .filter { it in InviteRepository.CODE_ALPHABET }
            .take(CODE_LENGTH)
        _state.update { it.copy(code = filtered, errorRes = null) }
    }

    fun submit() {
        val code = _state.value.code
        if (code.length != CODE_LENGTH) return
        val uid = authRepository.currentUser?.uid ?: return
        val now = System.currentTimeMillis()
        if (now < cooldownUntilMs) {
            _state.update { it.copy(errorRes = org.krug.app.R.string.enter_code_error_cooldown) }
            return
        }
        if (!submitInFlight.compareAndSet(false, true)) return
        _state.update { it.copy(joining = true) }
        viewModelScope.launch {
            try {
                when (val result = inviteRepository.acceptInvite(code, uid)) {
                    is JoinResult.Success -> {
                        consecutiveFailures = 0
                        cooldownUntilMs = 0L
                        // Aktiviraj novi krug odmah — Map će ga fokusirati po povratku.
                        localPrefs.setActiveCircleId(result.circleId)
                        // Sačekaj da Firestore snapshot listener vidi novi krug pre navigacije.
                        // Bez ovog, Map briefly renderuje empty-state CTA ("Imam pozivnicu")
                        // dok server snapshot ne stigne (race između tx commit i listener emit).
                        // Timeout 3s — ako Firestore kasni, navigiramo svejedno; live listener
                        // će svejedno popuniti listu kad stigne.
                        withTimeoutOrNull(3_000L) {
                            circleRepository.observeMyCircles(uid)
                                .first { circles -> circles.any { it.id == result.circleId } }
                        }
                        _state.update {
                            it.copy(joining = false, joinedCircleId = result.circleId)
                        }
                    }
                    JoinResult.Failure.Invalid -> failedAttempt(org.krug.app.R.string.enter_code_error_invalid)
                    JoinResult.Failure.Expired -> failedAttempt(org.krug.app.R.string.enter_code_error_invalid)
                    JoinResult.Failure.AlreadyMember -> {
                        // AlreadyMember nije brute-force, ne uvećavaj failureCount.
                        _state.update {
                            it.copy(joining = false, errorRes = org.krug.app.R.string.enter_code_error_already_member)
                        }
                    }
                    JoinResult.Failure.Network -> {
                        // Network fail takođe nije brute-force, ali ne pri-bilježuje cooldown.
                        _state.update {
                            it.copy(joining = false, errorRes = org.krug.app.R.string.enter_code_error_generic)
                        }
                    }
                }
            } finally {
                submitInFlight.set(false)
            }
        }
    }

    private fun failedAttempt(resId: Int) {
        consecutiveFailures += 1
        cooldownUntilMs = System.currentTimeMillis() + backoffMsFor(consecutiveFailures)
        _state.update { it.copy(joining = false, errorRes = resId) }
        startCooldownTick()
    }

    /**
     * 1Hz tick coroutine — ažurira cooldownRemainingSec u UiState dok god nije 0. UI vidi
     * countdown ("Sačekaj 4 s", "Sačekaj 3 s", ...) i disables submit dugme automatski
     * kroz cooldownRemainingSec > 0 check.
     */
    private fun startCooldownTick() {
        viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                val remainingMs = cooldownUntilMs - now
                if (remainingMs <= 0L) {
                    _state.update { it.copy(cooldownRemainingSec = 0) }
                    break
                }
                val sec = ((remainingMs + 999L) / 1000L).toInt()
                _state.update { it.copy(cooldownRemainingSec = sec) }
                delay(500L)
            }
        }
    }

    private fun backoffMsFor(failures: Int): Long = when (failures) {
        1 -> 1_000L
        2 -> 2_000L
        3 -> 5_000L
        else -> 15_000L
    }

    companion object {
        const val CODE_LENGTH = 6
    }
}
