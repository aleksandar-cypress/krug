package org.krug.app.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.krug.app.R
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import org.krug.app.feature.auth.AuthScreen
import org.krug.app.feature.circle.CircleDetailScreen
import org.krug.app.feature.circle.CircleListScreen
import org.krug.app.feature.circle.CreateCircleScreen
import org.krug.app.feature.circle.EnterCodeScreen
import org.krug.app.feature.circle.ShowInviteScreen
import org.krug.app.feature.history.HistoryScreen
import org.krug.app.feature.map.MapScreen
import org.krug.app.feature.places.AddPlaceScreen
import org.krug.app.feature.places.PlacesScreen
import org.krug.app.feature.onboarding.OnboardingScreen
import org.krug.app.feature.settings.AboutScreen
import org.krug.app.feature.settings.AccountScreen
import org.krug.app.feature.settings.BatteryModeScreen
import org.krug.app.feature.settings.DiagnosticsScreen
import org.krug.app.feature.settings.PrivacyScreen
import org.krug.app.feature.settings.ReliabilityScreen
import org.krug.app.feature.settings.SettingsRootScreen
import org.krug.app.feature.splash.SplashScreen

@Composable
fun KrugNavHost() {
    val nav = rememberNavController()
    // Default screen transitions — horizontal slide za forward/back navigaciju.
    // Default Compose Nav je fade-only (200ms), što je tih ali ne signalizira hierarchy.
    // Slide-left za "uđi dublje", slide-right za "vrati se nazad" daje user-u intuitivni
    // model "stack-a screen-ova" (kao iOS i većina modernih Android app-ova).
    val slideDurationMs = 280
    NavHost(
        navController = nav,
        startDestination = Splash,
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(slideDurationMs),
            )
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(slideDurationMs),
            )
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(slideDurationMs),
            )
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(slideDurationMs),
            )
        },
    ) {
        composable<Splash> {
            SplashScreen(
                onSignedOut = {
                    nav.navigate(Auth) { popUpTo(Splash) { inclusive = true } }
                },
                onOnboardingPending = { skipIntro ->
                    nav.navigate(Onboarding(skipIntro = skipIntro)) {
                        popUpTo(Splash) { inclusive = true }
                    }
                },
                onReady = {
                    nav.navigate(Map) { popUpTo(Splash) { inclusive = true } }
                },
            )
        }
        composable<Auth> {
            AuthScreen(
                onSignedIn = {
                    // Route through Splash so it re-evaluates onboarding state
                    // (LocalPrefs flag or Firestore) for this freshly-signed-in user.
                    nav.navigate(Splash) { popUpTo(Auth) { inclusive = true } }
                },
            )
        }
        composable<Onboarding> { entry ->
            val args = entry.toRoute<Onboarding>()
            OnboardingScreen(
                skipIntro = args.skipIntro,
                onDone = {
                    nav.navigate(Map) {
                        popUpTo<Onboarding> { inclusive = true }
                    }
                },
            )
        }
        composable<Map> {
            MapScreen(
                onOpenCircles = { nav.navigate(CircleList) },
                onOpenSettings = { nav.navigate(Settings) },
                onOpenReliability = { nav.navigate(Reliability) },
                onOpenCircleDetail = { circleId -> nav.navigate(CircleDetail(circleId)) },
                onCreateCircle = { nav.navigate(CreateCircle) },
                onJoinByCode = { nav.navigate(EnterCode()) },
                onOpenPlacesForCircle = { circleId -> nav.navigate(Places(circleId = circleId)) },
                onOpenHistory = { uid, name -> nav.navigate(History(uid = uid, displayName = name)) },
            )
        }
        composable<Settings> {
            SettingsRootScreen(
                onBack = { nav.popBackStack() },
                onAccount = { nav.navigate(Account) },
                onPrivacy = { nav.navigate(Privacy) },
                onBattery = { nav.navigate(BatteryMode) },
                onReliability = { nav.navigate(Reliability) },
                onAbout = { nav.navigate(About) },
                onDiagnostics = { nav.navigate(Diagnostics) },
            )
        }
        composable<Reliability> {
            ReliabilityScreen(onBack = { nav.popBackStack() })
        }
        composable<Diagnostics> {
            DiagnosticsScreen(onBack = { nav.popBackStack() })
        }
        composable<Account> {
            AccountScreen(
                onBack = { nav.popBackStack() },
                onSignedOut = {
                    nav.navigate(Auth) { popUpTo(0) { inclusive = true } }
                },
            )
        }
        composable<Privacy> {
            PrivacyScreen(onBack = { nav.popBackStack() })
        }
        composable<BatteryMode> {
            BatteryModeScreen(onBack = { nav.popBackStack() })
        }
        composable<About> {
            AboutScreen(onBack = { nav.popBackStack() })
        }
        composable<CircleList> {
            CircleListScreen(
                onBack = { nav.popBackStack() },
                onCreate = { nav.navigate(CreateCircle) },
                onJoin = { nav.navigate(EnterCode()) },
                onCircleClick = { circleId -> nav.navigate(CircleDetail(circleId)) },
            )
        }
        composable<CreateCircle> {
            CreateCircleScreen(
                onBack = { nav.popBackStack() },
                onCreated = { circleId ->
                    // Pop CreateCircle off, ide direktno u CircleDetail gde vlasnik
                    // bira tip pozivnice (običan / dete) pre nego što kod bude generisan.
                    nav.popBackStack()
                    nav.navigate(CircleDetail(circleId))
                },
            )
        }
        composable<ShowInvite> { entry ->
            val args = entry.toRoute<ShowInvite>()
            ShowInviteScreen(
                circleName = args.circleName,
                inviteCode = args.code,
                onDone = { nav.popBackStack() },
            )
        }
        composable<EnterCode> { entry ->
            val args = entry.toRoute<EnterCode>()
            EnterCodeScreen(
                prefilledCode = args.prefilledCode,
                onBack = { nav.popBackStack() },
                onJoined = { _ -> nav.popBackStack(Map, inclusive = false) },
            )
        }
        composable<CircleDetail> {
            CircleDetailScreen(
                onBack = { nav.popBackStack() },
                onLeftOrDeleted = { nav.popBackStack(Map, inclusive = false) },
                onShowInvite = { circleId, circleName, code ->
                    nav.navigate(ShowInvite(circleId = circleId, circleName = circleName, code = code))
                },
                onOpenPlaces = { circleId -> nav.navigate(Places(circleId = circleId)) },
            )
        }
        composable<Places> { entry ->
            val args = entry.toRoute<Places>()
            PlacesScreen(
                onBack = { nav.popBackStack() },
                onAddPlace = { nav.navigate(AddPlace(circleId = args.circleId)) },
                onShowOnMap = { nav.popBackStack(Map, inclusive = false) },
            )
        }
        composable<AddPlace> {
            AddPlaceScreen(onBack = { nav.popBackStack() })
        }
        composable<History> {
            HistoryScreen(onBack = { nav.popBackStack() })
        }
    }
}
