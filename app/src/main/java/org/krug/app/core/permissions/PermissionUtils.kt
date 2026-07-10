package org.krug.app.core.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object PermissionUtils {

    val foregroundLocationPermissions: List<String> = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    val needsBackgroundLocationPermission: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    val needsNotificationsPermission: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun hasForegroundLocation(context: Context): Boolean =
        foregroundLocationPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    fun hasBackgroundLocation(context: Context): Boolean {
        if (!needsBackgroundLocationPermission) return hasForegroundLocation(context)
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * True samo ako su notifikacije *stvarno* omogućene (i runtime permission granted
     * na 13+, i user nije disable-ovao channel-e). `NotificationManagerCompat.
     * areNotificationsEnabled()` je autoritativan check i za < 13 (Settings → App →
     * Notifs → OFF) i za 13+ (POST_NOTIFICATIONS nije grantovan) — ranije smo samo
     * proveravali permission grant, pa je user koji je isključio notif-e u system
     * settings-ima prolazio kao "OK" i banner se nije pojavljivao. Family Link
     * child accounts često imaju notif-e disable-ovane parent-control-om.
     */
    fun hasNotifications(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * Otvara direktno app-specific Notification Settings screen (ACTION_APP_NOTIFICATION_SETTINGS)
     * — user vidi listu channel-a i master toggle bez lutanja po Settings-u. Bolji UX
     * od generic openAppSettings za notif-only scenario. Fallback na app details ako
     * OEM ROM ne podržava intent (retko na modernim uređajima).
     */
    fun openNotificationSettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { activity.startActivity(intent) }
            .onFailure { openAppSettings(activity) }
    }

    /** Activity Recognition — A10+ traži runtime grant, pre-A10 nije bila potrebna. */
    fun hasActivityRecognition(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACTIVITY_RECOGNITION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun openAppSettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        activity.startActivity(intent)
    }

    @Suppress("BatteryLife") // Krug genuinely needs FGS reliability; declared in Play Console.
    fun openBatteryOptimizationRequest(activity: Activity) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${activity.packageName}")
        }
        runCatching { activity.startActivity(intent) }
            .onFailure {
                // Fallback: open system battery optimization list.
                activity.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
    }
}
