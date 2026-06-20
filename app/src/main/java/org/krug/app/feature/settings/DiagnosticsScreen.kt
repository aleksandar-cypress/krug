package org.krug.app.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import org.krug.app.core.location.LocationTrackingService
import org.krug.app.core.permissions.PermissionUtils
import org.krug.app.core.util.DeviceNames

/**
 * Debug-only ekran sa state-om FGS-a, permission-a, identity-a — alat za
 * dijagnostiku "lokacija ne dolazi" izveštaja od beta testera. Skupljamo
 * sve što obično tražimo kroz logcat na jedan ekran, sa Copy dugmetom
 * da user može da pošalje izveštaj.
 */
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // Recompose trigger za live re-read (manual refresh dugme + auto na enter).
    var refreshTick by remember { mutableIntStateOf(0) }
    val snapshot = remember(refreshTick) { collectSnapshot(context) }

    SettingsSubScaffold(title = "Dijagnostika", onBack = onBack) { _ ->
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                snapshot.sections.forEach { section ->
                    item { SectionHeader(section.title) }
                    section.rows.forEach { (label, value) ->
                        item { DiagRow(label = label, value = value) }
                    }
                    item { Spacer(Modifier.size(8.dp)) }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { refreshTick++ },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Osveži")
                }
                Button(
                    onClick = { copyToClipboard(context, snapshot.toClipboardText()) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Kopiraj sve")
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun DiagRow(label: String, value: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp)),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private data class DiagSection(val title: String, val rows: List<Pair<String, String>>)

private data class DiagSnapshot(val sections: List<DiagSection>) {
    fun toClipboardText(): String = buildString {
        appendLine("=== Krug dijagnostika ===")
        sections.forEach { section ->
            appendLine()
            appendLine("[${section.title}]")
            section.rows.forEach { (label, value) -> appendLine("$label: $value") }
        }
    }
}

private fun collectSnapshot(context: Context): DiagSnapshot {
    val now = System.currentTimeMillis()
    val lastPublish = LocationTrackingService.lastPublishAtMs
    val publishAgo = if (lastPublish == 0L) "nikad" else "${(now - lastPublish) / 1000}s"
    val user = FirebaseAuth.getInstance().currentUser
    val rawDevice = "${Build.MANUFACTURER} ${Build.MODEL}"
    val friendlyDevice = DeviceNames.friendly(rawDevice)

    return DiagSnapshot(
        sections = listOf(
            DiagSection(
                title = "FGS (Location Tracking Service)",
                rows = listOf(
                    "isRunning" to LocationTrackingService.isRunning.get().toString(),
                    "lastPublishAt" to (if (lastPublish == 0L) "—" else lastPublish.toString()),
                    "publishAgo" to publishAgo,
                ),
            ),
            DiagSection(
                title = "Permissions",
                rows = listOf(
                    "foregroundLocation" to PermissionUtils.hasForegroundLocation(context).toString(),
                    "backgroundLocation" to PermissionUtils.hasBackgroundLocation(context).toString(),
                    "notifications" to PermissionUtils.hasNotifications(context).toString(),
                    "batteryExempt" to PermissionUtils.isIgnoringBatteryOptimizations(context).toString(),
                ),
            ),
            DiagSection(
                title = "Identity",
                rows = listOf(
                    "uid" to (user?.uid?.take(12)?.plus("…") ?: "—"),
                    "providerId" to (user?.providerId ?: "—"),
                    "isAnonymous" to (user?.isAnonymous?.toString() ?: "—"),
                    "email" to (user?.email ?: "—"),
                ),
            ),
            DiagSection(
                title = "Uređaj",
                rows = listOf(
                    "rawModel" to rawDevice,
                    "friendlyName" to friendlyDevice,
                    "androidVersion" to "API ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})",
                    "manufacturer" to Build.MANUFACTURER,
                ),
            ),
        ),
    )
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Krug dijagnostika", text))
}
