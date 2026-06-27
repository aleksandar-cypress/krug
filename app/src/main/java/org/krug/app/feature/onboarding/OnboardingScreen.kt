package org.krug.app.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.krug.app.feature.onboarding.pages.IntroPage
import org.krug.app.feature.onboarding.pages.LocationPermissionPage
import org.krug.app.feature.onboarding.pages.NotificationsPermissionPage

@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    skipIntro: Boolean = false,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val pages = remember(skipIntro) { buildOnboardingPages(context, skipIntro = skipIntro) }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val uiState by viewModel.state.collectAsStateWithLifecycle()

    fun goNext() = scope.launch {
        if (pagerState.currentPage < pages.lastIndex) {
            pagerState.animateScrollToPage(pagerState.currentPage + 1)
        } else {
            // Poslednji ekran je završen → kompletiraj onboarding (auto-navigate u Map).
            viewModel.complete()
        }
    }

    fun goBack() = scope.launch {
        if (pagerState.currentPage > 0) {
            pagerState.animateScrollToPage(pagerState.currentPage - 1)
        }
    }

    BackHandler(enabled = pagerState.currentPage > 0) { goBack() }

    LaunchedEffect(uiState.completed) {
        if (uiState.completed) onDone()
    }

    // Edge: ako su sve permissions već granted (user ih dodelio iz sistemskih Settings-a,
    // ili posle uninstall+reinstall — Android čuva permission grants), `pages` je prazan
    // i nema šta da prikazujemo. Direktno kompletiraj onboarding.
    LaunchedEffect(pages) {
        if (pages.isEmpty() && !uiState.completed) {
            viewModel.complete()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier.fillMaxSize(),
        ) { pageIndex ->
            when (pages[pageIndex]) {
                OnboardingPage.INTRO -> IntroPage(onContinue = { goNext() })
                OnboardingPage.LOCATION -> LocationPermissionPage(onGranted = { goNext() })
                OnboardingPage.NOTIFICATIONS -> NotificationsPermissionPage(onContinueOrSkip = { goNext() })
            }
        }

        PagerDots(
            count = pages.size,
            current = pagerState.currentPage,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp),
        )
    }
}

@Composable
private fun PagerDots(
    count: Int,
    current: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(count) { index ->
            val active = index == current
            val targetWidth = if (active) 24.dp else 8.dp
            val width by animateDpAsState(
                targetValue = targetWidth,
                animationSpec = tween(durationMillis = 220),
                label = "pagerDotWidth",
            )
            Box(
                modifier = Modifier
                    .size(width = width, height = 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                    ),
            )
        }
    }
}
