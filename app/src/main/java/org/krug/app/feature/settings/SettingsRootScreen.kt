package org.krug.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.app.Activity
import org.krug.app.BuildConfig
import org.krug.app.R
import org.krug.app.core.permissions.PermissionUtils
import org.krug.app.ui.brand.pressScaleClickable
import org.krug.app.ui.theme.LogoBlue
import org.krug.app.ui.theme.LogoOrange
import org.krug.app.ui.theme.LogoPink
import org.krug.app.ui.theme.LogoTeal

/**
 * Settings root organizovan u 4 brand-color kategorije sa subtitles:
 * - Profil (LogoBlue)
 * - Privatnost i bezbednost (LogoPink)
 * - Performanse (LogoTeal)
 * - Aplikacija + debug Dijagnostika (LogoOrange)
 *
 * Svaki red ima 40dp brand-color badge sa belom ikonom, naslov + subtitle. Section
 * header iznad svake grupe (small caps, secondary color). Vizuelno scanovljivo i
 * brand-consistent umesto flat list of rows-a.
 */
private data class SettingsItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accentColor: Color,
    val onClick: () -> Unit,
)

private data class SettingsSection(
    val header: String,
    val items: List<SettingsItem>,
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
        val sections = buildList {
            add(
                SettingsSection(
                    header = stringResource(R.string.settings_section_profile),
                    items = listOf(
                        SettingsItem(
                            title = stringResource(R.string.settings_account),
                            subtitle = stringResource(R.string.settings_account_subtitle),
                            icon = Icons.Outlined.AccountCircle,
                            accentColor = LogoBlue,
                            onClick = onAccount,
                        ),
                    ),
                ),
            )
            add(
                SettingsSection(
                    header = stringResource(R.string.settings_section_privacy),
                    items = listOf(
                        SettingsItem(
                            title = stringResource(R.string.settings_privacy),
                            subtitle = stringResource(R.string.settings_privacy_subtitle),
                            icon = Icons.Outlined.Lock,
                            accentColor = LogoPink,
                            onClick = onPrivacy,
                        ),
                    ),
                ),
            )
            add(
                SettingsSection(
                    header = stringResource(R.string.settings_section_performance),
                    items = listOf(
                        SettingsItem(
                            title = stringResource(R.string.settings_battery),
                            subtitle = stringResource(R.string.settings_battery_subtitle),
                            icon = Icons.Outlined.BatteryFull,
                            accentColor = LogoTeal,
                            onClick = onBattery,
                        ),
                    ),
                ),
            )
            add(
                SettingsSection(
                    header = stringResource(R.string.settings_section_app),
                    items = buildList {
                        add(
                            SettingsItem(
                                title = stringResource(R.string.settings_about),
                                subtitle = stringResource(R.string.settings_about_subtitle),
                                icon = Icons.Outlined.Info,
                                accentColor = LogoOrange,
                                onClick = onAbout,
                            ),
                        )
                        if (BuildConfig.DEBUG) {
                            add(
                                SettingsItem(
                                    title = stringResource(R.string.settings_diagnostics_debug),
                                    subtitle = stringResource(R.string.settings_diagnostics_debug_subtitle),
                                    icon = Icons.Outlined.BugReport,
                                    accentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    onClick = onDiagnostics,
                                ),
                            )
                        }
                    },
                ),
            )
        }
        LazyColumn(
            modifier = mod.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Reliability warning banner — visible samo kad battery-opt nije exempt.
            // Ovo je najviše mesto u Settings-u pa je nemoguće da ga user propusti kad
            // gleda zašto lokacija ne radi.
            item { ReliabilityWarningCard() }
            items(sections.size) { idx ->
                SettingsSectionBlock(sections[idx])
            }
        }
    }
}

@Composable
private fun ReliabilityWarningCard() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var ignoring by remember { mutableStateOf(PermissionUtils.isIgnoringBatteryOptimizations(context)) }
    // Rescan on ON_RESUME — kad se user vrati iz sistemskih podešavanja, banner odmah nestane.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                ignoring = PermissionUtils.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    if (ignoring) return
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = LogoOrange.copy(alpha = 0.12f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = LogoOrange,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.reliability_warning_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    text = stringResource(R.string.reliability_warning_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(8.dp))
                TextButton(
                    onClick = {
                        (context as? Activity)?.let { PermissionUtils.openBatteryOptimizationRequest(it) }
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.reliability_warning_cta),
                        color = LogoOrange,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionBlock(section: SettingsSection) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = section.header.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
        )
        section.items.forEach { item ->
            SettingsRow(item)
        }
    }
}

@Composable
private fun SettingsRow(item: SettingsItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .pressScaleClickable(pressedScale = 0.98f, onClick = item.onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Brand-color icon badge — 40dp circle u accent boji sa belom ikonom.
            // Daje svakom redu kategorijski identitet bez čitanja teksta.
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(item.accentColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.size(2.dp))
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
