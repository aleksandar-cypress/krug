package org.krug.app.feature.onboarding.pages

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.krug.app.R

@Composable
fun AllSetPage(
    completing: Boolean,
    onDone: () -> Unit,
) {
    OnboardingPageScaffold(
        icon = Icons.Outlined.CheckCircle,
        title = stringResource(R.string.onb_done_title),
        body = stringResource(R.string.onb_done_body),
        primaryButtonText = stringResource(R.string.onb_done_cta),
        onPrimary = onDone,
        primaryLoading = completing,
    )
}
