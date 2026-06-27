package org.krug.app.feature.onboarding.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.krug.app.ui.theme.LogoBlue50
import org.krug.app.ui.theme.LogoPink50

@Composable
internal fun OnboardingPageScaffold(
    icon: ImageVector,
    title: String,
    body: String,
    primaryButtonText: String,
    onPrimary: () -> Unit,
    primaryLoading: Boolean = false,
    primaryEnabled: Boolean = true,
    secondaryButtonText: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    // Staggered enter — hero scale+fade, text slide+fade, button scale+fade. Bez ovog,
    // svaki permission ekran "ulazi" kao monolit i ne signalizira fokus user-ovog dejstva.
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.size(72.dp))

        // Brand gradient hero container sa nežnim breath pulse-om (1.0 ↔ 1.04 / 3.2s).
        // Gradient od LogoBlue50 → LogoPink50 reflektuje brand primary + secondary u svetlim
        // tonovima — soft i welcoming za onboarding kontekst.
        val pulse = rememberInfiniteTransition(label = "onb-hero-pulse")
        val scale by pulse.animateFloat(
            initialValue = 1f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3_200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "onb-hero-scale",
        )
        AnimatedVisibility(
            visible = visible,
            enter = scaleIn(initialScale = 0.8f, animationSpec = tween(500)) +
                fadeIn(animationSpec = tween(500)),
        ) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .scale(scale)
                    .shadow(elevation = 14.dp, shape = CircleShape, clip = false)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(colors = listOf(LogoBlue50, LogoPink50)),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(Modifier.size(36.dp))

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { it / 3 },
                animationSpec = tween(500, delayMillis = 250),
            ) + fadeIn(animationSpec = tween(500, delayMillis = 250)),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(400, delayMillis = 500)) +
                scaleIn(initialScale = 0.92f, animationSpec = tween(400, delayMillis = 500)),
        ) {
            Column {
                Button(
                    onClick = onPrimary,
                    enabled = primaryEnabled && !primaryLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    if (primaryLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.size(12.dp))
                    }
                    Text(
                        text = primaryButtonText,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
                if (secondaryButtonText != null && onSecondary != null) {
                    Spacer(Modifier.size(4.dp))
                    TextButton(
                        onClick = onSecondary,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(secondaryButtonText)
                    }
                }
            }
        }
    }
}
