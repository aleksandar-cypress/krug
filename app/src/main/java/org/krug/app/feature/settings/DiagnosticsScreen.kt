package org.krug.app.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.location.DetectedActivity
import com.google.firebase.auth.FirebaseAuth
import org.krug.app.BuildConfig
import org.krug.app.R
import org.krug.app.core.location.LocationTrackingService
import org.krug.app.core.logging.LogRingBuffer
import org.krug.app.core.permissions.PermissionUtils
import org.krug.app.core.util.DeviceNames
import timber.log.Timber

private fun activityName(type: Int): String = when (type) {
    DetectedActivity.IN_VEHICLE -> "VEHICLE"
    DetectedActivity.ON_BICYCLE -> "BICYCLE"
    DetectedActivity.ON_FOOT -> "ON_FOOT"
    DetectedActivity.WALKING -> "WALKING"
    DetectedActivity.RUNNING -> "RUNNING"
    DetectedActivity.STILL -> "STILL"
    DetectedActivity.TILTING -> "TILTING"
    DetectedActivity.UNKNOWN -> "UNKNOWN"
    else -> "OTHER($type)"
}

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

    SettingsSubScaffold(title = stringResource(R.string.diagnostics_title), onBack = onBack) { _ ->
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Primarna akcija za internal testere — jednim tap-om otvara mailer sa
                // pre-fill body-jem koji sadrži dijagnostiku (perms, FGS status, device,
                // version). Tester samo dopiše šta se desilo i pošalje. Bez ovog dugmeta
                // izveštaji stižu usmeno („nešto je puklo") pa Aleksandar mora naknadno
                // da pita za ove iste vrednosti — mailto pattern eliminiše cikluse.
                Button(
                    onClick = {
                        sendFeedbackEmail(context, snapshot)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.diagnostics_send_report))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { refreshTick++ },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.action_refresh))
                    }
                    Button(
                        onClick = {
                            copyToClipboard(context, snapshot.toClipboardText())
                            // Vizuelni confirm — bez toast-a tester klikne pa ne zna
                            // da li se ništa desilo (clipboard je nevidljiv). Kratak
                            // toast je dovoljan; snackbar bi bio previše za jedno-tap
                            // radnju.
                            Toast.makeText(
                                context,
                                R.string.diagnostics_copied_toast,
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.action_copy_all))
                    }
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
        // Copy-all podrazumeva puno stanje — uključi ceo bafer (do 500 linija) tako da
        // tester koji je odabrao Copy paste-ovanjem u chat/email dobija dubinski kontekst.
        // Za mailto: intent koristimo cap na 150 linija zbog URI limit-a (vidi sendFeedbackEmail).
        val logs = LogRingBuffer.dump()
        if (logs.isNotEmpty()) {
            appendLine()
            appendLine("[Log poslednjih ${logs.size} linija]")
            logs.forEach { appendLine(it) }
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
                title = "App",
                rows = listOf(
                    "versionName" to BuildConfig.VERSION_NAME,
                    "versionCode" to BuildConfig.VERSION_CODE.toString(),
                    "buildType" to BuildConfig.BUILD_TYPE,
                ),
            ),
            DiagSection(
                title = "FGS (Location Tracking Service)",
                rows = listOf(
                    "isRunning" to LocationTrackingService.isRunning.get().toString(),
                    "lastPublishAt" to (if (lastPublish == 0L) "-" else lastPublish.toString()),
                    "publishAgo" to publishAgo,
                    "detectedActivity" to activityName(LocationTrackingService.detectedActivity),
                ),
            ),
            DiagSection(
                title = context.getString(R.string.diagnostics_section_permissions),
                rows = listOf(
                    "foregroundLocation" to PermissionUtils.hasForegroundLocation(context).toString(),
                    "backgroundLocation" to PermissionUtils.hasBackgroundLocation(context).toString(),
                    "notifications" to PermissionUtils.hasNotifications(context).toString(),
                    "activityRecognition" to PermissionUtils.hasActivityRecognition(context).toString(),
                    "batteryExempt" to PermissionUtils.isIgnoringBatteryOptimizations(context).toString(),
                ),
            ),
            DiagSection(
                title = context.getString(R.string.diagnostics_section_identity),
                rows = listOf(
                    "uid" to (user?.uid?.take(12)?.plus("…") ?: "-"),
                    "providerId" to (user?.providerId ?: "-"),
                    "isAnonymous" to (user?.isAnonymous?.toString() ?: "-"),
                    "email" to (user?.email ?: "-"),
                ),
            ),
            DiagSection(
                title = context.getString(R.string.diagnostics_section_device),
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

/**
 * Otvara sistemski mailer sa pre-fill body-jem = full dijagnostika. Testeri dodaju
 * kratak opis šta se desilo i pošalju. `ACTION_SENDTO` sa mailto: URI-jem forsira
 * samo email app-ove da rezultuju u chooser-u (bez SMS/generic share targeta).
 *
 * Za email placeholder ostavljamo prazno „napiši šta se desilo…" markiran red na
 * vrhu — user zna gde da tapne i piše. Bez tog nudge-a, tester samo šalje golu
 * dijagnostiku bez konteksta.
 */
private fun sendFeedbackEmail(context: Context, snapshot: DiagSnapshot) {
    val versionName = BuildConfig.VERSION_NAME
    val versionCode = BuildConfig.VERSION_CODE
    val device = DeviceNames.friendly("${Build.MANUFACTURER} ${Build.MODEL}")
    val subject = "Krug problem — $device — $versionName ($versionCode)"
    // Log dump: poslednjih 150 linija iz [LogRingBuffer]. Cap 150 (a ne full 500)
    // jer mailto: URI ima praktičan limit ~64KB pre Gmail truncate-a. 150 linija *
    // ~150 char = ~22KB — dovoljno da vidimo šta se desilo par minuta pre bug-a,
    // sigurno unutar limita. Ako treba dublji dump, tester može da tapne „Kopiraj
    // sve" pa da paste-uje ceo bafer ručno u email.
    val logs = LogRingBuffer.dump(lastN = 150)
    val body = buildString {
        appendLine("[Napiši šta se desilo — koraci, vreme, koje ekran, koji član kruga:]")
        appendLine()
        appendLine()
        appendLine("--- Dijagnostika ---")
        append(snapshot.toClipboardText())
        appendLine()
        appendLine()
        appendLine("--- Log (poslednjih ${logs.size} linija) ---")
        logs.forEach { appendLine(it) }
    }
    val uri = Uri.parse(
        "mailto:aleksandarr@gmail.com?subject=" +
            Uri.encode(subject) +
            "&body=" + Uri.encode(body),
    )
    val intent = Intent(Intent.ACTION_SENDTO, uri)
    runCatching { context.startActivity(intent) }
        .onFailure {
            Timber.w(it, "DiagnosticsScreen: no email app resolved intent")
            Toast.makeText(context, R.string.diagnostics_send_no_email_app, Toast.LENGTH_LONG).show()
        }
}
