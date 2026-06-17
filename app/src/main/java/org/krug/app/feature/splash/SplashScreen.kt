package org.krug.app.feature.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.krug.app.R

@Composable
fun SplashScreen(
    onSignedOut: () -> Unit,
    onOnboardingPending: () -> Unit,
    onReady: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel(),
) {
    val decision by viewModel.decision.collectAsStateWithLifecycle()

    LaunchedEffect(decision) {
        when (decision) {
            SplashDecision.Loading -> Unit
            SplashDecision.SignedOut -> onSignedOut()
            SplashDecision.OnboardingPending -> onOnboardingPending()
            SplashDecision.Ready -> onReady()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.krug_logo),
                contentDescription = null,
                modifier = Modifier.size(160.dp),
            )
            Text(
                text = "Krug",
                style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}
