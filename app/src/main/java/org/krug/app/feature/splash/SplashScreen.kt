package org.krug.app.feature.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SplashScreen(
    onSignedOut: () -> Unit,
    onOnboardingPending: (skipIntro: Boolean) -> Unit,
    onReady: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel(),
) {
    val decision by viewModel.decision.collectAsStateWithLifecycle()

    LaunchedEffect(decision) {
        when (val d = decision) {
            SplashDecision.Loading -> Unit
            SplashDecision.SignedOut -> onSignedOut()
            is SplashDecision.OnboardingPending -> onOnboardingPending(d.skipIntro)
            SplashDecision.Ready -> onReady()
        }
    }

    // Sistemski splash (Android 12+ `installSplashScreen` + SplashGate) ostaje vidljiv
    // dok SplashViewModel ne odluči. Compose splash je samo prazna pozadina koja
    // sprečava flash dok navigacija ne pređe na sledeći route. Bez drugog logo-a
    // nema "double splash" jumpa.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
    )
}
