package org.krug.app.feature.onboarding.pages

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PinDrop
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.krug.app.R

@Composable
fun WelcomePage(onContinue: () -> Unit) {
    OnboardingPageScaffold(
        icon = Icons.Outlined.PinDrop,
        title = stringResource(R.string.onb_welcome_title),
        body = stringResource(R.string.onb_welcome_body),
        primaryButtonText = stringResource(R.string.action_continue),
        onPrimary = onContinue,
    )
}

@Composable
fun HowItWorksPage(onContinue: () -> Unit, onBack: () -> Unit) {
    OnboardingPageScaffold(
        icon = Icons.Outlined.Groups,
        title = stringResource(R.string.onb_how_title),
        body = stringResource(R.string.onb_how_body),
        primaryButtonText = stringResource(R.string.action_continue),
        onPrimary = onContinue,
        secondaryButtonText = stringResource(R.string.action_back),
        onSecondary = onBack,
    )
}

@Composable
fun PrivacyPage(onContinue: () -> Unit, onBack: () -> Unit) {
    OnboardingPageScaffold(
        icon = Icons.Outlined.Lock,
        title = stringResource(R.string.onb_privacy_title),
        body = stringResource(R.string.onb_privacy_body),
        primaryButtonText = stringResource(R.string.action_continue),
        onPrimary = onContinue,
        secondaryButtonText = stringResource(R.string.action_back),
        onSecondary = onBack,
    )
}
