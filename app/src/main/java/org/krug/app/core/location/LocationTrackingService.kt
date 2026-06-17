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
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.krug.app.MainActivity
import org.krug.app.R
import org.krug.app.core.circle.CircleRepository
import org.krug.app.core.permissions.PermissionUtils
import org.krug.app.core.settings.BatteryMode
import org.krug.app.core.settings.SettingsRepository
import org.krug.app.core.settings.UserSettings
import org.krug.app.core.sos.SosModel
import org.krug.app.core.sos.SosNotifier
import org.krug.app.core.sos.SosRepository
import org.krug.app.core.user.UserRepository
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
@AndroidEntryPoint
class LocationTrackingService : Service() {

    @Inject lateinit var firebaseAuth: FirebaseAuth
    @Inject lateinit var locationRepository: LocationRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var circleRepository: CircleRepository
    @Inject lateinit var userRepository: UserRepository
    @Inject lateinit var sosRepository: SosRepository
    @Inject lateinit var sosNotifier: SosNotifier

    /** Per-uid map: poslednji `triggeredAt` koji je već notifikovan korisnika. */
    private val knownSosTriggered = mutableMapOf<String, Long>()

    private lateinit var fused: FusedLocationProviderClient
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var currentSettings: UserSettings = UserSettings()
    @Volatile private var currentProfile: LocationProfile? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            val (battery, charging) = readBattery()
            reconfigureIfNeeded()
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
                    lastPublishAtMs = System.currentTimeMillis()
                }.onFailure { Timber.w(it, "publish location failed") }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Android 14+ ne dozvoljava startForeground sa LOCATION type-om bez
        // ACCESS_FINE/COARSE_LOCATION. Bilo koji entry (BootReceiver, Worker, MapScreen)
        // pošto smo bili odbijeni — stopSelf gracefully umesto da app crash-uje.
        if (!PermissionUtils.hasForegroundLocation(this)) {
            Timber.w("FGS started without location permission; stopping self")
            stopSelf()
            return
        }
        ensureChannel(this)
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } catch (e: SecurityException) {
            Timber.w(e, "startForeground threw SecurityException; stopping self")
            stopSelf()
            return
        }
        fused = LocationServices.getFusedLocationProviderClient(this)
        isRunning.set(true)
        observeSettings()
        observeRefreshRequests()
        observeCircleSos()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Ako je onCreate rano izašao (no permission), `fused` nije inicijalizovan.
        // Android i dalje deliveruje onStartCommand — moramo gracefully odustati.
        if (!isRunning.get()) {
            Timber.d("onStartCommand on uninitialized service; stopping")
            stopSelf()
            return START_NOT_STICKY
        }
        val initial = computeProfile(currentSettings)
        applyProfile(initial)
        // User-initiated refresh (dugme u MemberDetailSheet) preskače cooldown.
        // Ostali entry pointovi (Worker, Map start, Boot) idu kroz freshness check.
        val forceRefresh = intent?.getBooleanExtra(EXTRA_FORCE_REFRESH, false) == true
        val sincePublish = System.currentTimeMillis() - lastPublishAtMs
        if (forceRefresh || lastPublishAtMs == 0L || sincePublish > ONE_SHOT_COOLDOWN_MS) {
            requestOneShotFix()
        } else {
            Timber.d("Skip one-shot fix; last publish was ${sincePublish}ms ago")
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun requestOneShotFix() {
        Timber.d("requestOneShotFix invoked (shareGlobal=${currentSettings.shareLocationGlobal})")
        if (!currentSettings.shareLocationGlobal) return
        val uid = firebaseAuth.currentUser?.uid ?: run {
            Timber.w("requestOneShotFix: no firebase user")
            return
        }
        // Korak 1: instant publish keširane lokacije (Wi-Fi/cell/GPS cache, bez čekanja satelita).
        // Bez ovoga, u zatvorenom prostoru getCurrentLocation vrati null i nikad se ne publish-uje
        // dok ne istekne FGS interval (15 min na LOW profilu).
        try {
            fused.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    publishLocation(uid, loc, "last-location")
                } else {
                    Timber.d("lastLocation returned null")
                }
            }.addOnFailureListener { Timber.w(it, "getLastLocation failed") }
        } catch (e: SecurityException) {
            Timber.w(e, "getLastLocation missing permission")
        }
        // Korak 2: jednokratni location update — pouzdaniji od getCurrentLocation indoors.
        // BALANCED priority koristi i mrežu i GPS, pa skoro uvek vrati nešto.
        val req = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 0L)
            .setMaxUpdates(1)
            .setMaxUpdateAgeMillis(60_000L)
            .setWaitForAccurateLocation(false)
            .build()
        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { publishLocation(uid, it, "one-shot-updates") }
                fused.removeLocationUpdates(this)
            }
        }
        try {
            fused.requestLocationUpdates(req, cb, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Timber.w(e, "requestLocationUpdates(one-shot) missing permission")
        }
    }

    private fun publishLocation(uid: String, loc: android.location.Location, source: String) {
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
                lastPublishAtMs = System.currentTimeMillis()
                Timber.d("Published $source fix (lat=${loc.latitude}, lng=${loc.longitude}, acc=${loc.accuracy})")
            }.onFailure { Timber.w(it, "publish $source failed") }
        }
    }

    private fun observeSettings() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        scope.launch {
            settingsRepository.observe(uid).collectLatest { settings ->
                currentSettings = settings
                reconfigureIfNeeded()
            }
        }
    }

    /**
     * Observe-uje SOS state svih članova svih krugova kojima pripadam.
     * Notifikacija se okida samo ako sam član SOS-ovog kruga (sos.circleId).
     * Legacy SOS bez circleId-a (stari klijenti) prolazi kroz fallback.
     */
    private fun observeCircleSos() {
        val selfUid = firebaseAuth.currentUser?.uid ?: return
        scope.launch {
            circleRepository.observeMyCircles(selfUid)
                .flatMapLatest { circles ->
                    val myCircleIds = circles.map { it.id }.toSet()
                    val others = circles.flatMap { it.memberIds }.toSet() - selfUid
                    if (others.isEmpty()) flowOf(myCircleIds to emptyMap<String, SosModel?>())
                    else combine(
                        others.map { uid ->
                            sosRepository.observe(uid).map { sos -> uid to sos }
                        },
                    ) { pairs -> myCircleIds to pairs.toMap() }
                }
                .collectLatest { (myCircleIds, sosMap) ->
                    val currentUids = sosMap.keys
                    sosMap.forEach { (uid, sos) -> handleSosUpdate(uid, sos, myCircleIds) }
                    // Članovi više nisu u krug-u — sklon-i notifikacije.
                    (knownSosTriggered.keys.toSet() - currentUids).forEach { uid ->
                        sosNotifier.cancelSos(uid)
                        knownSosTriggered.remove(uid)
                    }
                }
        }
    }

    private suspend fun handleSosUpdate(uid: String, sos: SosModel?, myCircleIds: Set<String>) {
        val now = System.currentTimeMillis()
        val freshEnough = sos != null && sos.triggeredAt > 0L &&
            (now - sos.triggeredAt) < SOS_TTL_MS
        // Scope: sender mora biti u krugu koji ja pratim. Legacy SOS bez circleId-a
        // prolazi kao fallback (postoji za backward compat sa starim klijentima).
        val inScope = sos?.circleId == null || sos.circleId in myCircleIds
        val isActive = freshEnough && inScope
        val previousTs = knownSosTriggered[uid]
        when {
            isActive && previousTs != sos!!.triggeredAt -> {
                val name = fetchDisplayName(uid)
                sosNotifier.notifySos(uid, name)
                knownSosTriggered[uid] = sos.triggeredAt
                Timber.d("SOS notification fired for $uid ($name) circleId=${sos.circleId}")
            }
            !isActive && previousTs != null -> {
                sosNotifier.cancelSos(uid)
                knownSosTriggered.remove(uid)
                Timber.d("SOS cancelled for $uid")
            }
        }
    }

    private suspend fun fetchDisplayName(uid: String): String =
        withTimeoutOrNull(2_000L) {
            userRepository.observeUser(uid).filterNotNull().first().displayName.orEmpty()
        }.orEmpty()

    /** Drugi član krug-a je tražio osvežavanje — povuci sveži fix i očisti ping-ove. */
    private fun observeRefreshRequests() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        scope.launch {
            // `collect` (NE collectLatest!) — moramo da završimo clear pre nego što
            // procesujemo sledeću emisiju. collectLatest je cancellated clear coroutine
            // čim listener re-emit-uje (local cache update), entry ostaje, listener
            // re-fire, end of loop sa ~80ms petljom HIGH_ACCURACY GPS-a.
            locationRepository.observeRefreshRequests(uid).collect { requesters ->
                if (requesters.isEmpty()) return@collect
                Timber.d("Refresh request from ${requesters.size} member(s) — pulling fresh fix")
                requestOneShotFix()
                runCatching { locationRepository.clearRefreshRequests(uid, requesters) }
                    .onFailure { Timber.w(it, "Failed to clear refresh requests") }
            }
        }
    }

    private fun reconfigureIfNeeded() {
        val desired = computeProfile(currentSettings)
        if (desired != currentProfile) {
            Timber.d("Switching location profile: $currentProfile -> $desired (mode=${currentSettings.batteryMode})")
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

    // BALANCED je default i čvrsto LOW — heat dolazi od HIGH-frekventnog GPS poll-a.
    // SOS/refresh ping povlači sveži fix odvojeno (HIGH_ACCURACY one-shot).
    // MAX je opt-in za korisnike koji eksplicitno žele najtačnije tracking.
    private fun computeProfile(settings: UserSettings): LocationProfile =
        when (settings.batteryMode) {
            BatteryMode.MAX -> LocationProfile.HIGH
            BatteryMode.BALANCED, BatteryMode.SAVER -> LocationProfile.LOW
        }

    override fun onDestroy() {
        try {
            fused.removeLocationUpdates(locationCallback)
        } catch (_: Exception) { /* no-op */ }
        scope.cancel()
        isRunning.set(false)
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
        HIGH(intervalMs = 300_000L, fastestMs = 120_000L, displacementM = 100f),
        LOW(intervalMs = 900_000L, fastestMs = 600_000L, displacementM = 300f),
    }

    companion object {
        const val CHANNEL_ID = "krug_location"
        const val NOTIFICATION_ID = 1001
        const val ONE_SHOT_COOLDOWN_MS = 3 * 60_000L
        const val PUBLISH_FRESHNESS_MS = 12 * 60_000L
        const val SOS_TTL_MS = 30 * 60_000L
        private const val EXTRA_FORCE_REFRESH = "force_refresh"

        /** Live-process flag. Worker chita ovo da preskoči start ako je FGS već živ. */
        val isRunning = AtomicBoolean(false)

        /** Timestamp poslednjeg uspešnog publish-a. Worker chita za freshness check. */
        @Volatile var lastPublishAtMs: Long = 0L

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
            // Bilo koji entry point (BootReceiver, Worker, MapScreen) može da nas
            // pozove pre nego što je location permission dat (npr. odmah posle
            // reinstall-a). Bez ove provere startForegroundService → onCreate →
            // startForeground sa LOCATION type-om puca sa SecurityException na A14+.
            if (!PermissionUtils.hasForegroundLocation(context)) {
                Timber.d("LocationTrackingService.start skipped — no location permission")
                return
            }
            ensureChannel(context)
            val intent = Intent(context, LocationTrackingService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        /** User-initiated one-shot fix iz MemberDetailSheet (self). Preskače cooldown. */
        fun refreshSelf(context: Context) {
            if (!PermissionUtils.hasForegroundLocation(context)) {
                Timber.d("LocationTrackingService.refreshSelf skipped — no location permission")
                return
            }
            ensureChannel(context)
            val intent = Intent(context, LocationTrackingService::class.java)
                .putExtra(EXTRA_FORCE_REFRESH, true)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LocationTrackingService::class.java))
        }
    }
}
