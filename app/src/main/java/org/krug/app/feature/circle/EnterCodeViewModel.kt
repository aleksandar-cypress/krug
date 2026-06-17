package org.krug.app.feature.circle

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

    fun setCode(value: String) {
        val filtered = value.filter { it.isDigit() }.take(CODE_LENGTH)
        _state.update { it.copy(code = filtered, errorRes = null) }
    }

    fun submit() {
        val code = _state.value.code
        if (code.length != CODE_LENGTH) return
        val uid = authRepository.currentUser?.uid ?: return
        if (_state.value.joining) return
        _state.update { it.copy(joining = true) }
        viewModelScope.launch {
            when (val result = inviteRepository.acceptInvite(code, uid)) {
                is JoinResult.Success -> _state.update {
                    it.copy(joining = false, joinedCircleId = result.circleId)
                }
                JoinResult.Failure.Invalid -> errorOut(org.krug.app.R.string.enter_code_error_invalid)
                JoinResult.Failure.Expired -> errorOut(org.krug.app.R.string.enter_code_error_invalid)
                JoinResult.Failure.AlreadyMember -> errorOut(org.krug.app.R.string.enter_code_error_already_member)
                JoinResult.Failure.Network -> errorOut(org.krug.app.R.string.enter_code_error_generic)
            }
        }
    }

    private fun errorOut(resId: Int) {
        _state.update { it.copy(joining = false, errorRes = resId) }
    }

    companion object {
        const val CODE_LENGTH = 6
    }
}
