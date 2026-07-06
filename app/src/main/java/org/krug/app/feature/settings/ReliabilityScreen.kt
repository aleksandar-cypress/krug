package org.krug.app.feature.settings

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.krug.app.R
import org.krug.app.core.location.LocationTrackingService
import org.krug.app.core.permissions.PermissionUtils

/**
 * User-facing "zašto sam offline?" ekran. Za razliku od `DiagnosticsScreen` (debug-only,
 * monospace log-format za bug reportove), ovaj ekran je friendly checklist sa green/red
 * status ikonama + Fix dugmadima. Svrha: kad Slobodan pita "zašto me ne vidiš?", user
 * može da ga uputi na ovaj ekran i on samostalno vidi šta fali + kako da popravi.
 */
private data class ReliabilityCheck(
    val titleRes: Int,
    val bodyRes: Int,
    val isOk: Boolean,
    val onFix: ((Activity) -> Unit)?,
)

@Composable
fun ReliabilityScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val checks = remember(resumeTick) { collectAllChecks(context) }
    val issueCount = checks.count { !it.isOk }

    SettingsSubScaffold(
        title = stringResource(R.string.reliability_screen_title),
        onBack = onBack,
    ) { mod ->
        LazyColumn(
            modifier = mod.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { HeaderCard(issueCount = issueCount) }
            items(checks.size) { idx ->
                CheckRow(checks[idx])
            }
            item {
                Spacer(Modifier.size(8.dp))
                LiveStatusCard()
            }
        }
    }
}

@Composable
private fun HeaderCard(issueCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = if (issueCount == 0) StatusOkGreen.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = if (issueCount == 0) Icons.Filled.CheckCircle else Icons.Outlined.Cancel,
                contentDescription = null,
                tint = if (issueCount == 0) StatusOkGreen else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (issueCount == 0) stringResource(R.string.reliability_screen_all_ok_title)
                    else pluralStringResource(R.plurals.reliability_screen_has_issues_title, issueCount, issueCount),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    text = if (issueCount == 0) stringResource(R.string.reliability_screen_all_ok_body)
                    else stringResource(R.string.reliability_screen_has_issues_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CheckRow(check: ReliabilityCheck) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (check.isOk) Icons.Filled.CheckCircle else Icons.Outlined.Cancel,
                contentDescription = null,
                tint = if (check.isOk) StatusOkGreen else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(check.titleRes),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!check.isOk) {
                    Spacer(Modifier.size(2.dp))
                    Text(
                        text = stringResource(check.bodyRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!check.isOk && check.onFix != null) {
                Spacer(Modifier.size(8.dp))
                TextButton(
                    onClick = { (context as? Activity)?.let { check.onFix.invoke(it) } },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.reliability_issue_fix),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveStatusCard() {
    // Live status uzima podatke direktno iz LocationTrackingService static state-a. Nije
    // reactive (osvežava se samo pri ulasku/lifecycle-u kroz resumeTick u parent-u).
    // OK je jer je ekran već posmatrački; user ne očekuje real-time counter, već snapshot.
    val serviceRunning = LocationTrackingService.isRunning.get()
    val lastPublish = LocationTrackingService.lastPublishAtMs
    val now = System.currentTimeMillis()
    val ageText = if (lastPublish == 0L) stringResource(R.string.reliability_status_never)
    else formatAgo(now - lastPublish)
    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LiveStatusRow(
                label = stringResource(R.string.reliability_status_service),
                value = if (serviceRunning) stringResource(R.string.reliability_status_service_on)
                else stringResource(R.string.reliability_status_service_off),
                ok = serviceRunning,
            )
            LiveStatusRow(
                label = stringResource(R.string.reliability_status_last_publish),
                value = ageText,
                ok = lastPublish != 0L && (now - lastPublish) < 10L * 60_000L,
            )
        }
    }
}

@Composable
private fun LiveStatusRow(label: String, value: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
        )
    }
}

private fun collectAllChecks(context: Context): List<ReliabilityCheck> = listOf(
    ReliabilityCheck(
        titleRes = R.string.reliability_issue_bglocation,
        bodyRes = R.string.reliability_issue_bglocation_body,
        isOk = PermissionUtils.hasBackgroundLocation(context),
        onFix = { PermissionUtils.openAppSettings(it) },
    ),
    ReliabilityCheck(
        titleRes = R.string.reliability_issue_battery,
        bodyRes = R.string.reliability_issue_battery_body,
        isOk = PermissionUtils.isIgnoringBatteryOptimizations(context),
        onFix = { PermissionUtils.openBatteryOptimizationRequest(it) },
    ),
    ReliabilityCheck(
        titleRes = R.string.reliability_issue_notifications,
        bodyRes = R.string.reliability_issue_notifications_body,
        isOk = PermissionUtils.hasNotifications(context),
        onFix = { PermissionUtils.openAppSettings(it) },
    ),
    ReliabilityCheck(
        titleRes = R.string.reliability_issue_activity,
        bodyRes = R.string.reliability_issue_activity_body,
        isOk = PermissionUtils.hasActivityRecognition(context),
        onFix = { PermissionUtils.openAppSettings(it) },
    ),
)

private fun formatAgo(ms: Long): String {
    val secs = ms / 1000L
    val mins = secs / 60L
    val hours = mins / 60L
    val days = hours / 24L
    return when {
        secs < 60 -> "${secs}s"
        mins < 60 -> "${mins}min"
        hours < 24 -> "${hours}h"
        else -> "${days}d"
    }
}

private val StatusOkGreen = Color(0xFF10B981)
