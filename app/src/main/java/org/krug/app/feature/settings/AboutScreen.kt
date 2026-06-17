package org.krug.app.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.krug.app.BuildConfig
import org.krug.app.R

@Composable
fun AboutScreen(onBack: () -> Unit) {
    SettingsSubScaffold(
        title = stringResource(R.string.about_title),
        onBack = onBack,
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.krug_logo),
                contentDescription = null,
                modifier = Modifier.size(96.dp),
            )
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(
                text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(16.dp))

            TextButton(onClick = { /* TODO: open privacy policy URL */ }) {
                Text(stringResource(R.string.about_privacy_policy))
            }
            TextButton(onClick = { /* TODO: open terms URL */ }) {
                Text(stringResource(R.string.about_terms))
            }
            TextButton(onClick = { /* TODO: open Buy Me a Coffee URL */ }) {
                Text(stringResource(R.string.about_support))
            }
        }
    }
}
