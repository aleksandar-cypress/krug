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

@Composable
fun LocationPermissionPage(onGranted: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(PermissionUtils.hasForegroundLocation(context)) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        // Uvek čitaj autoritativno stanje iz sistema — `result.values` može biti
        // prazno ili nedosledno na nekim OEM-ima.
        granted = PermissionUtils.hasForegroundLocation(context)
        if (granted) onGranted()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = PermissionUtils.hasForegroundLocation(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Poll fallback — neke MIUI verzije ne propagiraju ON_RESUME pouzdano kad user
    // grant-uje permission iz system settings-a.
    LaunchedEffect(Unit) {
        while (!granted) {
            delay(500)
            if (PermissionUtils.hasForegroundLocation(context)) granted = true
        }
    }

    LaunchedEffect(granted) {
        if (granted) onGranted()
    }

    OnboardingPageScaffold(
        icon = Icons.Outlined.LocationOn,
        title = stringResource(R.string.onb_loc_title),
        body = stringResource(R.string.onb_loc_body),
        primaryButtonText = stringResource(R.string.onb_loc_grant),
        onPrimary = {
            // Ako je permission već granted (npr. user ga dao u prethodnoj sesiji), launcher
            // ne pokazuje dijalog i `LaunchedEffect(granted)` ne re-fire-uje jer se vrednost
            // ne menja. Pozovi onGranted ručno.
            if (PermissionUtils.hasForegroundLocation(context)) {
                onGranted()
            } else {
                launcher.launch(PermissionUtils.foregroundLocationPermissions.toTypedArray())
            }
        },
        secondaryButtonText = stringResource(R.string.onb_loc_open_settings),
        onSecondary = { (context as? Activity)?.let { PermissionUtils.openAppSettings(it) } },
    )
}

@Composable
fun BackgroundLocationPage(onContinue: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(PermissionUtils.hasBackgroundLocation(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = PermissionUtils.hasBackgroundLocation(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        while (!granted) {
            delay(500)
            if (PermissionUtils.hasBackgroundLocation(context)) granted = true
        }
    }

    LaunchedEffect(granted) {
        if (granted) onContinue()
    }

    OnboardingPageScaffold(
        icon = Icons.Outlined.MyLocation,
        title = stringResource(R.string.onb_bg_title),
        body = stringResource(R.string.onb_bg_body),
        primaryButtonText = stringResource(R.string.onb_bg_open_settings),
        onPrimary = { (context as? Activity)?.let { PermissionUtils.openAppSettings(it) } },
        secondaryButtonText = stringResource(R.string.action_skip),
        onSecondary = onContinue,
    )
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
