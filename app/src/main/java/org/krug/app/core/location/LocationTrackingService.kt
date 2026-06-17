package org.krug.app.core.location

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.krug.app.MainActivity
import org.krug.app.R
import org.krug.app.core.settings.BatteryMode
import org.krug.app.core.settings.SettingsRepository
import org.krug.app.core.settings.UserSettings
import timber.log.Timber

@AndroidEntryPoint
class LocationTrackingService : Service() {

    @Inject lateinit var firebaseAuth: FirebaseAuth
    @Inject lateinit var locationRepository: LocationRepository
    @Inject lateinit var settingsRepository: SettingsRepository

    private lateinit var fused: FusedLocationProviderClient
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var currentSettings: UserSettings = UserSettings()
    @Volatile private var currentProfile: LocationProfile? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            val (battery, charging) = readBattery()
            // Re-evaluate profile so a battery drop below threshold downshifts on next tick.
            reconfigureIfNeeded(battery, charging)
            if (!currentSettings.shareLocationGlobal) {
                Timber.d("Location sharing paused; skipping publish")
                return
            }
            val uid = firebaseAuth.currentUser?.uid ?: return
            scope.launch {
                runCatching {
                    locationRepository.publish(
                        uid = uid,
                        lat = loc.latitude,
                        lng = loc.longitude,
                        accuracy = loc.accuracy,
                        batteryPct = battery,
                        isCharging = charging,
                    )
                }.onFailure { Timber.w(it, "publish location failed") }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        fused = LocationServices.getFusedLocationProviderClient(this)
        observeSettings()
        observeRefreshRequests()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val (battery, charging) = readBattery()
        val initial = computeProfile(currentSettings, battery, charging)
        applyProfile(initial)
        requestOneShotFix()
        return START_STICKY
    }

    // FGS interval može da bude do 10min (LOW) / 2min (HIGH) između callback-ova.
    // Kad service tek startuje (npr. user otvara app posle dugog perioda), RTDB ima staru lokaciju
    // — povuci sveži fix odmah da se mapa ne pokazuje juče-pre-juče.
    @SuppressLint("MissingPermission")
    private fun requestOneShotFix() {
        if (!currentSettings.shareLocationGlobal) return
        val uid = firebaseAuth.currentUser?.uid ?: return
        val cts = CancellationTokenSource()
        try {
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { loc ->
                    loc ?: return@addOnSuccessListener
                    val (battery, charging) = readBattery()
                    scope.launch {
                        runCatching {
                            locationRepository.publish(
                                uid = uid,
                                lat = loc.latitude,
                                lng = loc.longitude,
                                accuracy = loc.accuracy,
                                batteryPct = battery,
                                isCharging = charging,
                            )
                        }.onFailure { Timber.w(it, "one-shot publish failed") }
                    }
                }
                .addOnFailureListener { Timber.w(it, "getCurrentLocation failed") }
        } catch (e: SecurityException) {
            Timber.w(e, "getCurrentLocation missing permission")
        }
    }

    private fun observeSettings() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        scope.launch {
            settingsRepository.observe(uid).collectLatest { settings ->
                currentSettings = settings
                val (battery, charging) = readBattery()
                reconfigureIfNeeded(battery, charging)
            }
        }
    }

    /** Drugi član krug-a je tražio osvežavanje — povuci sveži fix i očisti ping-ove. */
    private fun observeRefreshRequests() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        scope.launch {
            locationRepository.observeRefreshRequests(uid).collectLatest { requesters ->
                if (requesters.isEmpty()) return@collectLatest
                Timber.d("Refresh request from ${requesters.size} member(s) — pulling fresh fix")
                requestOneShotFix()
                runCatching { locationRepository.clearRefreshRequests(uid) }
                    .onFailure { Timber.w(it, "Failed to clear refresh requests") }
            }
        }
    }

    private fun reconfigureIfNeeded(batteryPct: Int, charging: Boolean) {
        val desired = computeProfile(currentSettings, batteryPct, charging)
        if (desired != currentProfile) {
            Timber.d("Switching location profile: $currentProfile -> $desired (batt=$batteryPct, charging=$charging, mode=${currentSettings.batteryMode})")
            applyProfile(desired)
        }
    }

    private fun applyProfile(profile: LocationProfile) {
        try {
            fused.removeLocationUpdates(locationCallback)
        } catch (_: Exception) { /* no-op */ }
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, profile.intervalMs)
            .setMinUpdateDistanceMeters(profile.displacementM)
            .setMinUpdateIntervalMillis(profile.fastestMs)
            .setWaitForAccurateLocation(false)
            .build()
        try {
            fused.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            currentProfile = profile
        } catch (e: SecurityException) {
            Timber.w(e, "Missing location permission; stopping self")
            stopSelf()
        }
    }

    private fun computeProfile(
        settings: UserSettings,
        batteryPct: Int,
        charging: Boolean,
    ): LocationProfile {
        if (charging) return LocationProfile.HIGH
        return when (settings.batteryMode) {
            BatteryMode.CONSTANT -> LocationProfile.HIGH
            BatteryMode.ADAPTIVE, BatteryMode.HYBRID ->
                if (batteryPct >= settings.hybridThresholdPct) LocationProfile.HIGH else LocationProfile.LOW
        }
    }

    override fun onDestroy() {
        try {
            fused.removeLocationUpdates(locationCallback)
        } catch (_: Exception) { /* no-op */ }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun readBattery(): Pair<Int, Boolean> {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = bm.isCharging
        return level to charging
    }

    private fun buildNotification(): android.app.Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.loc_notif_title))
            .setContentText(getString(R.string.loc_notif_text))
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private enum class LocationProfile(
        val intervalMs: Long,
        val fastestMs: Long,
        val displacementM: Float,
    ) {
        HIGH(intervalMs = 120_000L, fastestMs = 60_000L, displacementM = 50f),
        LOW(intervalMs = 600_000L, fastestMs = 300_000L, displacementM = 200f),
    }

    companion object {
        const val CHANNEL_ID = "krug_location"
        const val NOTIFICATION_ID = 1001

        fun ensureChannel(context: Context) {
            val mgr = NotificationManagerCompat.from(context)
            if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannelCompat.Builder(
                CHANNEL_ID,
                NotificationManager.IMPORTANCE_LOW,
            )
                .setName(context.getString(R.string.loc_notif_channel))
                .setShowBadge(false)
                .build()
            mgr.createNotificationChannel(channel)
        }

        fun start(context: Context) {
            ensureChannel(context)
            val intent = Intent(context, LocationTrackingService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LocationTrackingService::class.java))
        }
    }
}
