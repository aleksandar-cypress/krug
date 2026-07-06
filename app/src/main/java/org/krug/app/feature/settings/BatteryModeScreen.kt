package org.krug.app.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import org.krug.app.ui.brand.KrugRadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import org.krug.app.core.settings.BatteryMode
import org.krug.app.core.settings.SettingsRepository
import org.krug.app.core.settings.UserSettings

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BatteryModeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<UserSettings> = authRepository.observeAuthState()
        .flatMapLatest { user ->
            if (user == null) flowOf(UserSettings()) else settingsRepository.observe(user.uid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    fun setMode(mode: BatteryMode) {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch { settingsRepository.updateBatteryMode(uid, mode) }
    }
}

@Composable
fun BatteryModeScreen(
    onBack: () -> Unit,
    viewModel: BatteryModeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsSubScaffold(
        title = stringResource(R.string.battery_title),
        onBack = onBack,
    ) { _ ->
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ModeCard(
                title = stringResource(R.string.battery_balanced),
                description = stringResource(R.string.battery_balanced_desc),
                selected = state.batteryMode == BatteryMode.BALANCED,
                onClick = { viewModel.setMode(BatteryMode.BALANCED) },
            )
            ModeCard(
                title = stringResource(R.string.battery_saver),
                description = stringResource(R.string.battery_saver_desc),
                selected = state.batteryMode == BatteryMode.SAVER,
                onClick = { viewModel.setMode(BatteryMode.SAVER) },
            )
            ModeCard(
                title = stringResource(R.string.battery_max),
                description = stringResource(R.string.battery_max_desc),
                selected = state.batteryMode == BatteryMode.MAX,
                onClick = { viewModel.setMode(BatteryMode.MAX) },
            )
        }
    }
}

@Composable
private fun ModeCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            KrugRadioButton(selected = selected, onClick = onClick)
            Spacer(Modifier.size(4.dp))
            Column(modifier = Modifier.weight(1f).padding(top = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
