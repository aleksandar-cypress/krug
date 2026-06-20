package org.krug.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.krug.app.R
import org.krug.app.core.auth.AuthRepository
import org.krug.app.core.circle.CircleRepository
import org.krug.app.core.location.LocationRepository
import org.krug.app.core.settings.SettingsRepository
import org.krug.app.core.settings.UserSettings

data class PrivacyUiState(
    val settings: UserSettings = UserSettings(),
    val isChildAnywhere: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PrivacyViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val circleRepository: CircleRepository,
    private val locationRepository: LocationRepository,
) : ViewModel() {

    val state: StateFlow<PrivacyUiState> = authRepository.observeAuthState()
        .flatMapLatest { user ->
            if (user == null) flowOf(PrivacyUiState())
            else combine(
                settingsRepository.observe(user.uid),
                circleRepository.observeUserIsChildAnywhere(user.uid),
            ) { settings, isChild ->
                PrivacyUiState(settings = settings, isChildAnywhere = isChild)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrivacyUiState())

    fun setShareGlobal(value: Boolean) {
        val uid = authRepository.currentUser?.uid ?: return
        // Defensive: dete ne sme da menja sharing — UI bi tako bilo i tako zaključano.
        if (state.value.isChildAnywhere) return
        viewModelScope.launch {
            settingsRepository.updateShareGlobal(uid, value)
            // Sync u RTDB tako da peers odmah vide "Privatni mod" (bez čekanja 15min
            // staleness threshold-a). Kad uključi nazad, paused=false + FGS će ubrzo
            // publish-ovati svežu lokaciju.
            locationRepository.setPaused(uid, paused = !value)
        }
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
            if (state.isChildAnywhere) {
                ChildModeBanner()
                Spacer(Modifier.size(4.dp))
            }
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
                    checked = state.settings.shareLocationGlobal,
                    onCheckedChange = viewModel::setShareGlobal,
                    enabled = !state.isChildAnywhere,
                )
            }
            Text(
                text = stringResource(
                    if (state.settings.shareLocationGlobal) R.string.privacy_share_on
                    else R.string.privacy_share_off,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChildModeBanner() {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.ChildCare,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.size(10.dp))
            Column {
                Text(
                    text = "Roditeljska kontrola aktivna",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.size(2.dp))
                Text(
                    text = "Vlasnik kruga je označio tvoj nalog kao dete. Ne možeš pauzirati deljenje lokacije ni obrisati nalog",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}
