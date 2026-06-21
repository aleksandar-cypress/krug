package org.krug.app.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Calendar
import org.krug.app.BuildConfig
import org.krug.app.R
import org.krug.app.ui.theme.LogoBlue
import timber.log.Timber

private const val PRIVACY_URL = "https://aleksandar-cypress.github.io/krug/privacy.html"
private const val TERMS_URL = "https://aleksandar-cypress.github.io/krug/terms.html"

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    fun openUrl(url: String) {
        openExternalUrl(context, url)
    }
    // Verzija u release-u nema "-debug" suffix; u debug build-u ostavljamo radi jasnosti.
    val versionDisplay = BuildConfig.VERSION_NAME
    val year = Calendar.getInstance().get(Calendar.YEAR)

    SettingsSubScaffold(
        title = stringResource(R.string.about_title),
        onBack = onBack,
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.size(8.dp))
            Image(
                painter = painterResource(R.drawable.krug_logo),
                contentDescription = null,
                modifier = Modifier.size(230.dp),
            )
            Spacer(Modifier.size(16.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                color = LogoBlue,
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = stringResource(R.string.about_version, versionDisplay),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.size(32.dp))

            AboutLinkRow(
                icon = Icons.Outlined.Lock,
                label = stringResource(R.string.about_privacy_policy),
                onClick = { openUrl(PRIVACY_URL) },
            )
            Spacer(Modifier.size(10.dp))
            AboutLinkRow(
                icon = Icons.Outlined.Description,
                label = stringResource(R.string.about_terms),
                onClick = { openUrl(TERMS_URL) },
            )

            Spacer(Modifier.size(40.dp))
            Text(
                text = "© $year Krug · Sva prava zadržana",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Defensive eksterni URL launcher:
 * 1) Pokušaj Chrome Custom Tabs (in-app browser, back se vraća u Krug bez gašenja app-a).
 * 2) Fallback na klasičan ACTION_VIEW intent (eksterni browser).
 * 3) Fallback na Toast — nema instaliran nijedan handler.
 *
 * Bez ovoga, raw ACTION_VIEW na Android 14+ ume da baci ActivityNotFoundException ako
 * korisnik nema default browser (custom ROM, headless device, freshly wiped state).
 */
private fun openExternalUrl(context: Context, url: String) {
    val uri = runCatching { Uri.parse(url) }.getOrNull()
    if (uri == null) {
        Timber.w("openExternalUrl: failed to parse $url")
        Toast.makeText(context, "Link je neispravan", Toast.LENGTH_SHORT).show()
        return
    }
    // Custom Tabs path — najbolji UX, ali zahteva da postoji bar jedan browser sa CCT podrškom.
    runCatching {
        val tab = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        tab.launchUrl(context, uri)
    }.onSuccess { return }
        .onFailure { Timber.d(it, "Custom Tabs launch failed; trying ACTION_VIEW") }

    // Fallback 1: klasičan ACTION_VIEW.
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, uri)
        context.startActivity(intent)
    }.onSuccess { return }
        .onFailure { e ->
            if (e !is ActivityNotFoundException) Timber.w(e, "ACTION_VIEW failed for $url")
        }

    // Fallback 2: nema browsera uopšte.
    Toast.makeText(context, "Nije moguće otvoriti link", Toast.LENGTH_SHORT).show()
}

@Composable
private fun AboutLinkRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.size(14.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
