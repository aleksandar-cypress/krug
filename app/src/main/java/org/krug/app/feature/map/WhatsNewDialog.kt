package org.krug.app.feature.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.krug.app.R

/**
 * Prikazuje se jednom po version-code bump-u. Cilj: user razume koje su nove
 * mogućnosti bez proaktivnog istraživanja UI-ja.
 */
@Composable
fun WhatsNewDialog(
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.whats_new_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    stringResource(R.string.whats_new_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Feature(
                    icon = Icons.Outlined.CheckCircle,
                    title = stringResource(R.string.whats_new_checkin_title),
                    body = stringResource(R.string.whats_new_checkin_body),
                )
                Feature(
                    icon = Icons.Outlined.Navigation,
                    title = stringResource(R.string.whats_new_eta_title),
                    body = stringResource(R.string.whats_new_eta_body),
                )
                Feature(
                    icon = Icons.Outlined.Speed,
                    title = stringResource(R.string.whats_new_speeding_title),
                    body = stringResource(R.string.whats_new_speeding_body),
                )
                Feature(
                    icon = Icons.Outlined.Warning,
                    title = stringResource(R.string.whats_new_crash_title),
                    body = stringResource(R.string.whats_new_crash_body),
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    stringResource(R.string.whats_new_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.whats_new_close))
            }
        },
    )
}

@Composable
private fun Feature(icon: ImageVector, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.size(12.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
