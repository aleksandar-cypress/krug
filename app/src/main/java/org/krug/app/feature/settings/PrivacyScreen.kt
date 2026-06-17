package org.krug.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.krug.app.R
import org.krug.app.core.auth.AuthRepository
import org.krug.app.core.settings.SettingsRepository
import org.krug.app.core.settings.UserSettings

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PrivacyViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<UserSettings> = authRepository.observeAuthState()
        .flatMapLatest { user ->
            if (user == null) flowOf(UserSettings()) else settingsRepository.observe(user.uid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    fun setShareGlobal(value: Boolean) {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch { settingsRepository.updateShareGlobal(uid, value) }
    }
}

@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
    viewModel: PrivacyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsSubScaffold(
        title = stringResource(R.string.privacy_title),
        onBack = onBack,
    ) { _ ->
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.privacy_share_label),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(Modifier.size(8.dp))
                Switch(
                    checked = state.shareLocationGlobal,
                    onCheckedChange = viewModel::setShareGlobal,
                )
            }
            Text(
                text = stringResource(
                    if (state.shareLocationGlobal) R.string.privacy_share_on
                    else R.string.privacy_share_off,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
