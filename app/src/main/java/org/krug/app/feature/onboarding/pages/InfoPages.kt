package org.krug.app.feature.onboarding.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.krug.app.R
import org.krug.app.ui.brand.KrugLogo

/**
 * Spojeni Welcome + HowItWorks + Privacy u jedan ekran sa 3 sekcije.
 * Bivše 3 strane → 1 — manje "Dalje, Dalje, Dalje" klikova pre permission ekrana.
 */
@Composable
fun IntroPage(onContinue: () -> Unit) {
    // Staggered reveal — sve sekcije se pojavljuju jedna po jedna posle prvog frame-a.
    // Bez ovog, ekran "ulazi" kao monolit i welcome content nema vizuelni breathing room.
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.size(40.dp))

        // Brand hero — scale-in iz 80% da logo "raste" na ekran, nije samo pop.
        AnimatedVisibility(
            visible = visible,
            enter = scaleIn(initialScale = 0.8f, animationSpec = tween(500)) +
                fadeIn(animationSpec = tween(500)),
        ) {
            KrugLogo(
                modifier = Modifier.size(140.dp),
            )
        }

        Spacer(Modifier.size(28.dp))

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { it / 3 },
                animationSpec = tween(500, delayMillis = 200),
            ) + fadeIn(animationSpec = tween(500, delayMillis = 200)),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.onb_welcome_title),
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.onb_welcome_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.size(28.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Feature row-ovi — staggered po 150ms da ne ulaze istovremeno (less chaotic).
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(
                    initialOffsetY = { it / 4 },
                    animationSpec = tween(500, delayMillis = 400),
                ) + fadeIn(animationSpec = tween(500, delayMillis = 400)),
            ) {
                IntroFeatureRow(
                    icon = Icons.Outlined.Groups,
                    title = stringResource(R.string.onb_how_title),
                    body = stringResource(R.string.onb_how_body),
                )
            }
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(
                    initialOffsetY = { it / 4 },
                    animationSpec = tween(500, delayMillis = 550),
                ) + fadeIn(animationSpec = tween(500, delayMillis = 550)),
            ) {
                IntroFeatureRow(
                    icon = Icons.Outlined.Lock,
                    title = stringResource(R.string.onb_privacy_title),
                    body = stringResource(R.string.onb_privacy_body),
                )
            }
        }

        Spacer(Modifier.size(16.dp))

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(400, delayMillis = 750)) +
                scaleIn(initialScale = 0.92f, animationSpec = tween(400, delayMillis = 750)),
        ) {
            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    text = stringResource(R.string.action_continue),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}

@Composable
private fun IntroFeatureRow(icon: ImageVector, title: String, body: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        Spacer(Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f).clip(RoundedCornerShape(0.dp))) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
