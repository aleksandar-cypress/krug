package org.krug.app.feature.circle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.krug.app.core.auth.AuthRepository
import org.krug.app.core.circle.InviteRepository
import org.krug.app.core.circle.JoinResult

data class EnterCodeUiState(
    val code: String = "",
    val joining: Boolean = false,
    val errorRes: Int? = null,
    val joinedCircleId: String? = null,
)

@HiltViewModel
class EnterCodeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val inviteRepository: InviteRepository,
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
     * Exponential backoff za brute-force pokušaje invite kod-a. 6 cifara = 10^6 mogućnosti;
     * bez cooldown-a, napadač sa svojim auth-om može da pokuša ~10 kod/s i pogodi za par
     * sati. Sa cooldown-om 1s/2s/5s/15s posle 1./2./3./4.+ fail-a, brute-force postaje
     * neisplativ. Reset na uspeh.
     */
    @Volatile private var consecutiveFailures: Int = 0
    @Volatile private var cooldownUntilMs: Long = 0L

    fun setCode(value: String) {
        val filtered = value.filter { it.isDigit() }.take(CODE_LENGTH)
        _state.update { it.copy(code = filtered, errorRes = null) }
    }

    fun submit() {
        val code = _state.value.code
        if (code.length != CODE_LENGTH) return
        val uid = authRepository.currentUser?.uid ?: return
        val now = System.currentTimeMillis()
        if (now < cooldownUntilMs) {
            val waitSec = ((cooldownUntilMs - now) / 1000L).coerceAtLeast(1)
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
