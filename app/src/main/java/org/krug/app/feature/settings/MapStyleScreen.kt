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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import org.krug.app.R
import org.krug.app.core.map.MapStyleOption
import org.krug.app.core.prefs.LocalPrefs
import org.krug.app.ui.brand.KrugRadioButton

@HiltViewModel
class MapStyleViewModel @Inject constructor(
    private val localPrefs: LocalPrefs,
) : ViewModel() {

    val selected: StateFlow<MapStyleOption> = localPrefs.mapStyleKeyFlow
        .map { MapStyleOption.fromKey(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapStyleOption.DEFAULT)

    fun select(option: MapStyleOption) {
        localPrefs.setMapStyleKey(option.name)
    }
}

@Composable
fun MapStyleScreen(
    onBack: () -> Unit,
    viewModel: MapStyleViewModel = hiltViewModel(),
) {
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    SettingsSubScaffold(
        title = stringResource(R.string.map_style_screen_title),
        onBack = onBack,
    ) { _ ->
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.map_style_screen_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MapStyleOption.entries.forEach { option ->
                StyleCard(
                    title = stringResource(option.labelRes),
                    description = stringResource(option.subtitleRes),
                    selected = selected == option,
                    onClick = { viewModel.select(option) },
                )
            }
        }
    }
}

@Composable
private fun StyleCard(
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
