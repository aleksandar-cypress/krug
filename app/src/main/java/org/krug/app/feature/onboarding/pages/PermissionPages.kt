package org.krug.app.feature.onboarding.pages

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import org.krug.app.R
import org.krug.app.core.permissions.PermissionUtils

/**
 * Combined foreground + background location prompt — state machine na istom ekranu.
 * Faza 1 (foreground not granted): standardni system dialog za ACCESS_FINE/COARSE_LOCATION.
 * Faza 2 (foreground granted, background not): otvara app settings da user prebaci na "Uvek dozvoli".
 * Kad oboje granted → onGranted(). Na Android < 10 nema posebnog background-a, foreground = sve.
 */
@Composable
fun LocationPermissionPage(onGranted: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var foregroundGranted by remember { mutableStateOf(PermissionUtils.hasForegroundLocation(context)) }
    var backgroundGranted by remember { mutableStateOf(PermissionUtils.hasBackgroundLocation(context)) }
    // Background tap counter — posle prvog tap-a, ako još nije granted, dozvoli "Preskoči"
    // jer Android 11+ background grant traži navigaciju u Settings što mnogi user-i ne znaju.
    var bgAttempted by remember { mutableStateOf(false) }

    val foregroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        foregroundGranted = PermissionUtils.hasForegroundLocation(context)
        backgroundGranted = PermissionUtils.hasBackgroundLocation(context)
    }
    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        backgroundGranted = PermissionUtils.hasBackgroundLocation(context)
        bgAttempted = true
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                foregroundGranted = PermissionUtils.hasForegroundLocation(context)
                backgroundGranted = PermissionUtils.hasBackgroundLocation(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        while (!foregroundGranted || !backgroundGranted) {
            delay(500)
            if (!foregroundGranted) foregroundGranted = PermissionUtils.hasForegroundLocation(context)
            if (!backgroundGranted) backgroundGranted = PermissionUtils.hasBackgroundLocation(context)
        }
    }

    LaunchedEffect(foregroundGranted, backgroundGranted) {
        if (foregroundGranted && backgroundGranted) onGranted()
    }

    if (!foregroundGranted) {
        // Faza 1 — sistem dialog za foreground location.
        OnboardingPageScaffold(
            icon = Icons.Outlined.LocationOn,
            title = stringResource(R.string.onb_loc_title),
            body = stringResource(R.string.onb_loc_body),
            primaryButtonText = stringResource(R.string.onb_loc_grant),
            onPrimary = {
                if (PermissionUtils.hasForegroundLocation(context)) {
                    foregroundGranted = true
                } else {
                    foregroundLauncher.launch(PermissionUtils.foregroundLocationPermissions.toTypedArray())
                }
            },
            secondaryButtonText = stringResource(R.string.onb_loc_open_settings),
            onSecondary = { (context as? Activity)?.let { PermissionUtils.openAppSettings(it) } },
        )
    } else {
        // Faza 2 — "Uvek dozvoli". Koristi permission launcher (A11+ automatski redirektuje
        // u app Settings); polling + ON_RESUME pokupe rezultat. Preskoči se otkrije posle
        // prvog tap-a tako da user ne ostane zaglavljen ako ne ume da promeni u Settings-u.
        OnboardingPageScaffold(
            icon = Icons.Outlined.MyLocation,
            title = stringResource(R.string.onb_bg_title),
            body = stringResource(R.string.onb_bg_body),
            primaryButtonText = stringResource(R.string.onb_bg_open_settings),
            onPrimary = {
                if (PermissionUtils.hasBackgroundLocation(context)) {
                    backgroundGranted = true
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    backgroundLauncher.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                } else {
                    // < A10 background nije posebna permisija — foreground = sve.
                    backgroundGranted = true
                }
            },
            secondaryButtonText = if (bgAttempted) stringResource(R.string.action_skip) else null,
            onSecondary = if (bgAttempted) ({
                // User je probao i ne uspeva — preskočimo. Lokacija će raditi u foreground-u,
                // background tracking limitiran. Može da uključi kasnije kroz Settings.
                backgroundGranted = true
            }) else null,
        )
    }
}

@Composable
fun NotificationsPermissionPage(onContinueOrSkip: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as? Activity
    var granted by remember { mutableStateOf(PermissionUtils.hasNotifications(context)) }
    // Posle prvog tap-a, ako sistem više ne prikazuje dijalog (double-deny ili
    // "Don't ask again"), prebacujemo primary CTA na link ka sistemskim podešavanjima.
    var attempted by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        granted = PermissionUtils.hasNotifications(context)
        attempted = true
        if (granted) onContinueOrSkip()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = PermissionUtils.hasNotifications(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        while (!granted) {
            delay(500)
            if (PermissionUtils.hasNotifications(context)) granted = true
        }
    }

    LaunchedEffect(granted) {
        if (granted) onContinueOrSkip()
    }

    val showSettingsCta = attempted && !granted && activity != null &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        !activity.shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)

    OnboardingPageScaffold(
        icon = Icons.Outlined.Notifications,
        title = stringResource(R.string.onb_notif_title),
        body = stringResource(R.string.onb_notif_body),
        primaryButtonText = if (showSettingsCta) stringResource(R.string.onb_notif_open_settings)
        else stringResource(R.string.onb_notif_grant),
        onPrimary = {
            if (PermissionUtils.hasNotifications(context)) {
                onContinueOrSkip()
            } else if (showSettingsCta) {
                activity?.let { PermissionUtils.openAppSettings(it) }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // Pre Tiramisu-a notifikacije su default-no enabled, samo nastavi.
                onContinueOrSkip()
            }
        },
    )
}

@Composable
fun BatteryOptimizationPage(onContinueOrSkip: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var ignoring by remember { mutableStateOf(PermissionUtils.isIgnoringBatteryOptimizations(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                ignoring = PermissionUtils.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(ignoring) {
        if (ignoring) onContinueOrSkip()
    }

    OnboardingPageScaffold(
        icon = Icons.Outlined.BatteryFull,
        title = stringResource(R.string.onb_bat_title),
        body = stringResource(R.string.onb_bat_body),
        primaryButtonText = stringResource(R.string.onb_bat_open),
        onPrimary = {
            // Već exempt — sistem dialog ne pokazuje ništa, LaunchedEffect(ignoring)
            // ne re-fire-uje jer se vrednost ne menja. Advanc-uj ručno.
            if (PermissionUtils.isIgnoringBatteryOptimizations(context)) {
                onContinueOrSkip()
            } else {
                (context as? Activity)?.let { PermissionUtils.openBatteryOptimizationRequest(it) }
            }
        },
        secondaryButtonText = stringResource(R.string.action_skip),
        onSecondary = onContinueOrSkip,
    )
}
