package org.krug.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Calendar
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
            // Ako user gasi share, očisti temp timer (nema smisla držati istek koji bi se
            // realizovao dok je već off).
            if (!value && state.value.settings.shareUntilMs != null) {
                settingsRepository.updateShareUntil(uid, null)
            }
            // Sync u RTDB tako da peers odmah vide "Privatni mod" (bez čekanja 15min
            // staleness threshold-a). Kad uključi nazad, paused=false + FGS će ubrzo
            // publish-ovati svežu lokaciju.
            locationRepository.setPaused(uid, paused = !value)
        }
    }

    /**
     * Postavlja trajanje temporary sharing-a. `durationMs = null` → clear temp (uvek).
     * Non-null → set shareUntilMs = now + durationMs, uključi share (u slučaju da je bio off).
     */
    fun setShareDuration(durationMs: Long?) {
        val uid = authRepository.currentUser?.uid ?: return
        if (state.value.isChildAnywhere) return
        viewModelScope.launch {
            if (durationMs == null) {
                settingsRepository.updateShareUntil(uid, null)
            } else {
                val until = System.currentTimeMillis() + durationMs
                settingsRepository.updateShareGlobal(uid, true)
                settingsRepository.updateShareUntil(uid, until)
                // Peer sees paused=false odmah, kao pri manual toggle.
                locationRepository.setPaused(uid, paused = false)
            }
        }
    }

    fun setPlaceNotifs(enabled: Boolean) {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            settingsRepository.updatePlaceNotifs(uid, enabled)
        }
    }

    fun setSilentHours(value: String?) {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            settingsRepository.updateSilentHours(uid, value)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
    onOpenMyHistory: () -> Unit = {},
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
            // Duration picker se pokazuje samo kad je share ON i nije child-locked. Bez
            // ovog, izbor 1h/4h u pause modu bi bio ambigvitetan (uključuje deljenje?
            // ne uključuje?). Sada čist model: hoćeš deliš — biraš koliko.
            if (state.settings.shareLocationGlobal && !state.isChildAnywhere) {
                Spacer(Modifier.size(8.dp))
                ShareDurationPicker(
                    activeUntilMs = state.settings.shareUntilMs,
                    onSelect = viewModel::setShareDuration,
                )
            }

            Spacer(Modifier.size(24.dp))
            androidx.compose.material3.HorizontalDivider()
            Spacer(Modifier.size(16.dp))

            // Place notifikacije toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.privacy_place_notifs_label),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = stringResource(R.string.privacy_place_notifs_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.size(8.dp))
                Switch(
                    checked = state.settings.placeNotifsEnabled,
                    onCheckedChange = viewModel::setPlaceNotifs,
                )
            }

            // Silent hours toggle + preset chips
            Spacer(Modifier.size(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.privacy_silent_hours_label),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = stringResource(R.string.privacy_silent_hours_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.size(8.dp))
                Switch(
                    checked = state.settings.silentHours != null,
                    onCheckedChange = { enabled ->
                        viewModel.setSilentHours(if (enabled) "23:00-07:00" else null)
                    },
                )
            }
            if (state.settings.silentHours != null) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val presets = listOf(
                        "22:00-07:00" to stringResource(R.string.privacy_silent_hours_preset_night),
                        "23:00-07:00" to stringResource(R.string.privacy_silent_hours_preset_late),
                        "00:00-06:00" to stringResource(R.string.privacy_silent_hours_preset_midnight),
                    )
                    presets.forEach { (value, label) ->
                        FilterChip(
                            selected = state.settings.silentHours == value,
                            onClick = { viewModel.setSilentHours(value) },
                            label = { Text(label) },
                        )
                    }
                }
            }

            Spacer(Modifier.size(16.dp))
            androidx.compose.material3.OutlinedButton(
                onClick = onOpenMyHistory,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.privacy_my_history_cta))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShareDurationPicker(
    activeUntilMs: Long?,
    onSelect: (Long?) -> Unit,
) {
    Text(
        text = stringResource(R.string.privacy_share_duration_label),
        style = MaterialTheme.typography.titleMedium,
    )
    Spacer(Modifier.size(6.dp))
    Text(
        text = stringResource(R.string.privacy_share_duration_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.size(10.dp))
    // FlowRow: chip-ovi se lome u drugi red kad prekorače širinu ekrana. "Do kraja dana"
    // je najduži label, u Row+weight se sekao/prelomio. FlowRow ostavi svakom chip-u
    // svoju punu širinu i wrap-uje ostatak dole. Bez ovog, na sr-Latn (duži tekstovi)
    // UI se raspada.
    val remaining = activeUntilMs?.let { it - System.currentTimeMillis() }
    val is1h = remaining != null && remaining in 1..(2 * 60 * 60_000L)
    val is4h = remaining != null && remaining in (2 * 60 * 60_000L + 1)..(6 * 60 * 60_000L)
    val isEod = remaining != null && remaining > (6 * 60 * 60_000L)
    val alwaysSelected = activeUntilMs == null
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = alwaysSelected,
            onClick = { if (!alwaysSelected) onSelect(null) },
            label = { Text(stringResource(R.string.privacy_share_duration_always)) },
        )
        FilterChip(
            selected = is1h,
            onClick = { onSelect(60 * 60_000L) },
            label = { Text(stringResource(R.string.privacy_share_duration_1h)) },
        )
        FilterChip(
            selected = is4h,
            onClick = { onSelect(4 * 60 * 60_000L) },
            label = { Text(stringResource(R.string.privacy_share_duration_4h)) },
        )
        FilterChip(
            selected = isEod,
            onClick = { onSelect(millisUntilEndOfDay()) },
            label = { Text(stringResource(R.string.privacy_share_duration_eod)) },
        )
    }
    // Countdown labela kad je timer aktivan — user vidi koliko još ostaje. Tick svakih
    // 30s (dovoljno precizno da nije prevrsen); LaunchedEffect ponovo pokreće.
    if (activeUntilMs != null) {
        var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }
        LaunchedEffect(activeUntilMs) {
            while (true) {
                kotlinx.coroutines.delay(30_000L)
                nowTick = System.currentTimeMillis()
            }
        }
        val remainingMs = (activeUntilMs - nowTick).coerceAtLeast(0L)
        val human = formatRemainingHuman(remainingMs)
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.privacy_share_active_until, human),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Vraća broj ms do kraja dana (23:59:59.999) u user timezone-u. Koristi se za
 * "Do kraja dana" chip — auto-off u ponoć lokalnog vremena.
 */
private fun millisUntilEndOfDay(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 23)
    cal.set(Calendar.MINUTE, 59)
    cal.set(Calendar.SECOND, 59)
    cal.set(Calendar.MILLISECOND, 999)
    return (cal.timeInMillis - System.currentTimeMillis()).coerceAtLeast(60_000L)
}

/** "1h 23m", "23m", "45s" — kratka human-readable countdown labela. */
private fun formatRemainingHuman(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m"
        else -> "${totalSec}s"
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
                    text = stringResource(R.string.privacy_child_lock_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.size(2.dp))
                Text(
                    text = stringResource(R.string.privacy_child_lock_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}
