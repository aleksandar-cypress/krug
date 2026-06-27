package org.krug.app.feature.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.krug.app.ui.brand.KrugLogo

@Composable
fun SplashScreen(
    onSignedOut: () -> Unit,
    onOnboardingPending: (skipIntro: Boolean) -> Unit,
    onReady: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel(),
) {
    val decision by viewModel.decision.collectAsStateWithLifecycle()

    // Min display window — animacija (~1.1s entrance) mora da odsvira makar približno
    // do kraja čak i ako VM odluči odmah. Bez ovog, Compose splash bi se navigirao u
    // sledeći ekran posle ~50ms i animacija ne bi bila vidljiva.
    var minTimeReached by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(MIN_DISPLAY_MS)
        minTimeReached = true
    }

    LaunchedEffect(decision, minTimeReached) {
        if (!minTimeReached) return@LaunchedEffect
        when (val d = decision) {
            SplashDecision.Loading -> Unit
            SplashDecision.SignedOut -> onSignedOut()
            is SplashDecision.OnboardingPending -> onOnboardingPending(d.skipIntro)
            SplashDecision.Ready -> onReady()
        }
    }

    // Canvas fiksiran na 192dp (poklapa sistemski splash veličinu da nema "jumpa"). Heads
    // unutar KrugLogo-a kreću na ~2.6x finalnog radijusa što je dobro VAN 192dp bounds-a.
    // Compose Canvas + Box ne klipuju crtanje izvan layout bounds-a, pa heads vizuelno
    // ulaze sa strana ekrana (Surface clipuje tek na ivici Surface-a = ekran).
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        KrugLogo(
            modifier = Modifier.size(LOGO_SIZE_DP.dp),
            animated = true,
        )
    }
}

// Spin (1.2s, starts at 0.3s) → ukupno ~1.5s; dajemo 1.6s.
private const val MIN_DISPLAY_MS = 1_600L
private const val LOGO_SIZE_DP = 208
