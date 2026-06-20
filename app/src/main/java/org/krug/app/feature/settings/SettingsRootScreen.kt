package org.krug.app.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.krug.app.BuildConfig
import org.krug.app.R

private data class SettingsItem(
    val title: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
fun SettingsRootScreen(
    onBack: () -> Unit,
    onAccount: () -> Unit,
    onPrivacy: () -> Unit,
    onBattery: () -> Unit,
    onAbout: () -> Unit,
    onDiagnostics: () -> Unit = {},
) {
    SettingsSubScaffold(
        title = stringResource(R.string.settings_title),
        onBack = onBack,
    ) { mod ->
        val items = buildList {
            add(SettingsItem(stringResource(R.string.settings_account), Icons.Outlined.AccountCircle, onAccount))
            add(SettingsItem(stringResource(R.string.settings_privacy), Icons.Outlined.Lock, onPrivacy))
            add(SettingsItem(stringResource(R.string.settings_battery), Icons.Outlined.BatteryFull, onBattery))
            add(SettingsItem(stringResource(R.string.settings_about), Icons.Outlined.Info, onAbout))
            if (BuildConfig.DEBUG) {
                add(SettingsItem("Dijagnostika (debug)", Icons.Outlined.BugReport, onDiagnostics))
            }
        }
        LazyColumn(
            modifier = mod.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items) { SettingsRow(it) }
        }
    }
}

@Composable
private fun SettingsRow(item: SettingsItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = item.onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(16.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
