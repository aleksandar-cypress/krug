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
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import com.google.android.gms.location.DetectedActivity
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
import kotlinx.coroutines.CancellationException
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
import org.krug.app.core.prefs.LocalPrefs
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
    @Inject lateinit var localPrefs: LocalPrefs

    /**
     * Per-uid map: poslednji `triggeredAt` koji je već notifikovan korisnika.
     * Lazy-load iz LocalPrefs u onCreate — bez persistence-a, ako Android ubije proces
     * (OOM, ANR, BootReceiver restart), isti SOS bi pri sledećoj observe-emisiji
     * fired-ovao notifikaciju ponovo. TTL filter u loadSosNotified() drop-uje stare.
     */
    private lateinit var knownSosTriggered: MutableMap<String, Long>

    private lateinit var fused: FusedLocationProviderClient
    private var activityClient: ActivityRecognitionClient? = null
    private var activityPendingIntent: PendingIntent? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var currentSettings: UserSettings = UserSettings()
    @Volatile private var currentProfile: LocationProfile? = null

    /** Movement filter: poslednja PUBLISHED lokacija (ne svaki callback). */
    @Volatile private var lastPublishedLat: Double? = null
    @Volatile private var lastPublishedLng: Double? = null

    /** Trenutak start-a ove instance — onDestroy ga koristi za FGS lifetime telemetry. */
    @Volatile private var startedAtMs: Long = 0L

    /** Aktivni boost-expiry coroutine — cancel-uje se eksplicitno pre scope.cancel u onDestroy. */
    @Volatile private var boostExpiryJob: kotlinx.coroutines.Job? = null

    /**
     * BURST profil: SOS aktivan ili peer-ov refresh ping. Drži veoma frequent
     * fix interval kratko vreme (30min SOS, 5min refresh). Posle isteka, profil
     * se prirodno vraća na battery mode default.
     */
    @Volatile private var sosBoostUntilMs: Long = 0L
    @Volatile private var refreshBoostUntilMs: Long = 0L

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            // Accuracy filter — dropujemo unreliable fixes (indoor, tunnel, slab GPS sat).
            // Threshold 100m je liberalan: pravi GPS fix retko prelazi 50m čak u zgradama.
            if (loc.accuracy > MAX_ACCEPTABLE_ACCURACY_M) {
                Timber.d("Drop low-accuracy fix: ${loc.accuracy}m (threshold ${MAX_ACCEPTABLE_ACCURACY_M}m)")
                return
            }
            val (battery, charging) = readBattery()
            reconfigureIfNeeded()
            if (!currentSettings.shareLocationGlobal) {
                Timber.d("Location sharing paused; skipping publish")
                return
            }
            // Movement filter — preskoči publish ako se nismo mnogo pomerili, OSIM ako je
            // bilo predugo od poslednjeg publish-a (peers očekuju fresh updatedAt signal).
            if (!shouldPublish(loc)) {
                Timber.d("Skip publish: movement < ${SIGNIFICANT_MOVEMENT_M}m + recent publish")
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
                    lastPublishedLat = loc.latitude
                    lastPublishedLng = loc.longitude
                }.onFailure { ex ->
                    // FGS shutdown / scope cancel je normalan lifecycle event — ne loguj kao W
                    // (CrashlyticsTree bi to forward-ovao u dashboard kao false-positive).
                    if (ex is CancellationException) Timber.d("publish location cancelled (scope dying)")
                    else Timber.w(ex, "publish location failed")
                }
            }
        }
    }

    /**
     * Movement filter — publish samo ako je realna promena pozicije. Force publish ipak
     * svakih FORCE_PUBLISH_INTERVAL_MS da peers imaju svež updatedAt signal (potreban za
     * "Privatni mod" detection i refresh ping responsiveness).
     */
    private fun shouldPublish(loc: android.location.Location): Boolean {
        val lastLat = lastPublishedLat
        val lastLng = lastPublishedLng
        if (lastLat == null || lastLng == null) return true
        val sinceLastPublish = System.currentTimeMillis() - lastPublishAtMs
        if (sinceLastPublish >= FORCE_PUBLISH_INTERVAL_MS) return true
        val results = FloatArray(1)
        android.location.Location.distanceBetween(lastLat, lastLng, loc.latitude, loc.longitude, results)
        return results[0] >= SIGNIFICANT_MOVEMENT_M
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
            // Expected na Android 14+ kad nas zovne background entry (BootReceiver na
            // MY_PACKAGE_REPLACED, Worker iz Doze) — FGS sa type=location ne sme bez
            // user-visible context-a. Debug-only log da ne puni Crashlytics.
            Timber.d("startForeground SecurityException (background entry): %s", e.message)
            stopSelf()
            return
        }
        fused = LocationServices.getFusedLocationProviderClient(this)
        isRunning.set(true)
        startedAtMs = System.currentTimeMillis()
        knownSosTriggered = localPrefs.loadSosNotified(SOS_TTL_MS)
        Timber.i("FGS start (loaded %d SOS dedup entries)", knownSosTriggered.size)
        observeSettings()
        observeRefreshRequests()
        observeCircleSos()
        registerActivityRecognition()
    }

    /**
     * Activity Recognition setup — Google API koji koristi akcelerometar (low-power senzor)
     * da detektuje šta korisnik radi (vožnja, hodanje, sedi). FGS koristi to da prilagodi
     * GPS interval. Ako permission nije granted ili API nedostupan, gracefully skip.
     */
    private fun registerActivityRecognition() {
        if (!PermissionUtils.hasActivityRecognition(this)) {
            Timber.d("ActivityRecognition permission missing — skipping; falling back to static profile")
            return
        }
        val client = ActivityRecognition.getClient(this)
        val intent = Intent(this, ActivityRecognitionReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            this,
            ACTIVITY_RECOGNITION_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        // Detection interval 60s — dovoljno brzo da reaguje na promenu (npr. ulazi u auto),
        // a dovoljno retko da ne troši dodatnu bateriju.
        runCatching {
            @Suppress("MissingPermission")
            client.requestActivityUpdates(60_000L, pi)
            activityClient = client
            activityPendingIntent = pi
            Timber.d("ActivityRecognition registered (interval 60s)")
        }.onFailure { Timber.w(it, "ActivityRecognition register failed") }
    }

    private fun unregisterActivityRecognition() {
        val client = activityClient ?: return
        val pi = activityPendingIntent ?: return
        runCatching {
            client.removeActivityUpdates(pi)
            pi.cancel()
        }
        activityClient = null
        activityPendingIntent = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Ako je onCreate rano izašao (no permission), `fused` nije inicijalizovan.
        // Android i dalje deliveruje onStartCommand — moramo gracefully odustati.
        if (!isRunning.get()) {
            Timber.d("onStartCommand on uninitialized service; stopping")
            stopSelf()
            return START_NOT_STICKY
        }
        // SOS boost — MapViewModel.triggerSos prosleđuje EXTRA_SOS_BOOST=true. Postavlja
        // BURST profil na 30min za frequent peer updates tokom hitne situacije.
        if (intent?.getBooleanExtra(EXTRA_SOS_BOOST, false) == true) {
            sosBoostUntilMs = System.currentTimeMillis() + SOS_BOOST_DURATION_MS
            scheduleProfileReconfigOnBoostExpiry(SOS_BOOST_DURATION_MS)
            Timber.d("SOS boost activated until $sosBoostUntilMs")
        }
        // Activity Recognition poke — receiver kaže "promenila se aktivnost, reconfigure
        // profile odmah" da ne čekamo sledeći location callback (može biti 15min daleko).
        val activityChanged = intent?.getBooleanExtra(EXTRA_ACTIVITY_CHANGED, false) == true
        val initial = computeProfile(currentSettings)
        applyProfile(initial)
        if (activityChanged) {
            // Samo profile reconfigure (već urađeno gore), bez one-shot fix-a.
            Timber.d("Profile reconfigured for new activity: ${activityName(detectedActivity)} → $initial")
            return START_STICKY
        }
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

    /**
     * Boost expiry job — drži handle u `boostExpiryJob` da bismo mogli da ga cancel-ujemo
     * pre `scope.cancel()` u onDestroy. Bez ovog, `delay` može da nadživi service teardown
     * i `applyProfile` bi pucao na `fused` referenci koju je `super.onDestroy` već dao GC-u.
     * Novi boost zahtev otkazuje stari job (jedan u letu).
     */
    private fun scheduleProfileReconfigOnBoostExpiry(durationMs: Long) {
        boostExpiryJob?.cancel()
        boostExpiryJob = scope.launch {
            kotlinx.coroutines.delay(durationMs + 1_000L)
            applyProfile(computeProfile(currentSettings))
            Timber.d("Boost expired — profile back to ${currentProfile}")
        }
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
            }.onFailure { ex ->
                if (ex is CancellationException) Timber.d("publish $source cancelled (scope dying)")
                else Timber.w(ex, "publish $source failed")
            }
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
                    val staleUids = knownSosTriggered.keys.toSet() - currentUids
                    if (staleUids.isNotEmpty()) {
                        staleUids.forEach { uid ->
                            sosNotifier.cancelSos(uid)
                            knownSosTriggered.remove(uid)
                        }
                        localPrefs.saveSosNotified(knownSosTriggered)
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
                // Prvo iz payload-a (zero-latency, set u trenutku trigger-a). Legacy SOS-i
                // (pre v0.2) nemaju senderName — fallback na observeUser fetch.
                val name = sos.senderName?.takeIf { it.isNotBlank() } ?: fetchDisplayName(uid)
                val circleName = sos.circleName?.takeIf { it.isNotBlank() }
                sosNotifier.notifySos(uid, name, circleName)
                knownSosTriggered[uid] = sos.triggeredAt
                localPrefs.saveSosNotified(knownSosTriggered)
                Timber.d("SOS notification fired for $uid ($name) circleId=${sos.circleId}")
            }
            !isActive && previousTs != null -> {
                sosNotifier.cancelSos(uid)
                knownSosTriggered.remove(uid)
                localPrefs.saveSosNotified(knownSosTriggered)
                Timber.d("SOS cancelled for $uid")
            }
        }
    }

    private suspend fun fetchDisplayName(uid: String): String =
        withTimeoutOrNull(5_000L) {
            // 5s — Firestore cold start može da bude sporiji od starog 2s thresholda.
            // Bogatiji fallback chain — UserRepository.observeUser daje displayName /
            // email / deviceModel; biramo prvo neprazno (DeviceNames.friendly za device).
            userRepository.observeUser(uid).filterNotNull().first().let { u ->
                u.displayName.takeIf { it.isNotBlank() }
                    ?: u.email.substringBefore('@').takeIf { it.isNotBlank() }
                    ?: org.krug.app.core.util.DeviceNames.friendly(u.deviceModel)
                        .takeIf { it.isNotBlank() }
                    ?: ""
            }
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
                val now = System.currentTimeMillis()
                // Razdvoji fresh (< 5min) od stale ping-ova. Stari se odbacuju (drop)
                // bez triggering-a one-shot fix — sprečava reakciju na zaboravljene
                // ping-ove kad je FGS bio ubijen pa se vratio sat kasnije.
                val fresh = requesters.filter { (_, ts) -> now - ts < REFRESH_REQUEST_TTL_MS }
                val stale = requesters.keys - fresh.keys
                if (stale.isNotEmpty()) {
                    Timber.d("Discarding ${stale.size} stale refresh request(s) older than ${REFRESH_REQUEST_TTL_MS / 60_000}min")
                }
                if (fresh.isNotEmpty()) {
                    Timber.d("Refresh request from ${fresh.size} member(s) — pulling fresh fix + boost")
                    requestOneShotFix()
                    // Plus prebaci na BURST profil 5min — ako se kreće, peer ga vidi uživo
                    // umesto samo jedan fix na ping.
                    refreshBoostUntilMs = System.currentTimeMillis() + REFRESH_BOOST_DURATION_MS
                    applyProfile(computeProfile(currentSettings))
                    scheduleProfileReconfigOnBoostExpiry(REFRESH_BOOST_DURATION_MS)
                }
                // Uvek čisti SVE entrije (fresh + stale) da ne ostaje smeća u RTDB.
                runCatching { locationRepository.clearRefreshRequests(uid, requesters.keys) }
                    .onFailure { Timber.w(it, "Failed to clear refresh requests") }
            }
        }
    }

    private fun reconfigureIfNeeded() {
        val desired = computeProfile(currentSettings)
        if (desired != currentProfile) {
            Timber.i("Profile switch %s -> %s (mode=%s)", currentProfile, desired, currentSettings.batteryMode)
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

    // Profile resolution (prioritetom):
    //  1. SOS/refresh boost aktivan → BURST (najbrži interval) bez obzira na sve
    //  2. Battery mode MAX → HIGH (eksplicitno opt-in)
    //  3. Battery < 15% i ne puni se → LOW_THROTTLED (×2 default interval)
    //  4. Activity Recognition daje sigurnu detekciju → per-activity profil
    //     (VEHICLE/BICYCLE/WALKING/STILL)
    //  5. Default → LOW
    private fun computeProfile(settings: UserSettings): LocationProfile {
        val now = System.currentTimeMillis()
        if (now < sosBoostUntilMs || now < refreshBoostUntilMs) return LocationProfile.BURST
        if (settings.batteryMode == BatteryMode.MAX) return LocationProfile.HIGH
        val (battery, charging) = readBattery()
        val lowBatt = battery in 0..LOW_BATTERY_THRESHOLD && !charging
        if (lowBatt) return LocationProfile.LOW_THROTTLED
        return profileForActivity(detectedActivity) ?: LocationProfile.LOW
    }

    private fun profileForActivity(act: Int): LocationProfile? = when (act) {
        DetectedActivity.IN_VEHICLE -> LocationProfile.VEHICLE
        DetectedActivity.ON_BICYCLE -> LocationProfile.BICYCLE
        DetectedActivity.RUNNING, DetectedActivity.WALKING, DetectedActivity.ON_FOOT -> LocationProfile.WALKING
        DetectedActivity.STILL -> LocationProfile.STILL
        // TILTING, UNKNOWN — ambiguous, fallback na LOW (return null)
        else -> null
    }

    private fun activityName(type: Int): String = when (type) {
        DetectedActivity.IN_VEHICLE -> "VEHICLE"
        DetectedActivity.ON_BICYCLE -> "BICYCLE"
        DetectedActivity.ON_FOOT -> "ON_FOOT"
        DetectedActivity.WALKING -> "WALKING"
        DetectedActivity.RUNNING -> "RUNNING"
        DetectedActivity.STILL -> "STILL"
        DetectedActivity.TILTING -> "TILTING"
        DetectedActivity.UNKNOWN -> "UNKNOWN"
        else -> "OTHER($type)"
    }

    override fun onDestroy() {
        val lifetime = if (startedAtMs > 0L) System.currentTimeMillis() - startedAtMs else 0L
        lastFgsLifetimeMs = lifetime
        Timber.i("FGS destroy (lifetime=%dms)", lifetime)
        // Eksplicitno otkaži boost-expiry pre scope.cancel — bez ovog je trka: delay()
        // može da završi 1ms pre scope.cancel-a i applyProfile bi gađao polu-destroyed
        // fused klijent.
        boostExpiryJob?.cancel()
        boostExpiryJob = null
        try {
            fused.removeLocationUpdates(locationCallback)
        } catch (_: Exception) { /* no-op */ }
        unregisterActivityRecognition()
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
        /**
         * SOS aktivan ili peer-ov refresh ping. Bounded duration (30min/5min). Konzervativni
         * BURST: 60s interval (umesto agresivnih 30s) — i dalje 15x frequent vs LOW, ali
         * battery drain podnošljiv tokom tih nekoliko minuta.
         */
        BURST(intervalMs = 60_000L, fastestMs = 30_000L, displacementM = 0f),
        /** Battery mode MAX — eksplicitno opt-in. */
        HIGH(intervalMs = 300_000L, fastestMs = 120_000L, displacementM = 100f),
        /** Activity Recognition: VEHICLE — vozi se, treba česti fix (1.5min). */
        VEHICLE(intervalMs = 90_000L, fastestMs = 45_000L, displacementM = 0f),
        /** Activity Recognition: BICYCLE — biciklira, srednji interval (2min). */
        BICYCLE(intervalMs = 120_000L, fastestMs = 60_000L, displacementM = 30f),
        /** Activity Recognition: WALKING / RUNNING / ON_FOOT — hoda (4min). */
        WALKING(intervalMs = 240_000L, fastestMs = 120_000L, displacementM = 30f),
        /** Default — battery-friendly. Activity nepoznata ili TILTING. */
        LOW(intervalMs = 900_000L, fastestMs = 600_000L, displacementM = 300f),
        /** Activity Recognition: STILL — stoji/sedi, retko (20min). */
        STILL(intervalMs = 1_200_000L, fastestMs = 600_000L, displacementM = 500f),
        /** Battery < 15% i ne puni se — najređi interval (30min), štedi vlasniku. */
        LOW_THROTTLED(intervalMs = 1_800_000L, fastestMs = 900_000L, displacementM = 500f),
    }

    companion object {
        const val CHANNEL_ID = "krug_location"
        const val NOTIFICATION_ID = 1001
        const val ONE_SHOT_COOLDOWN_MS = 3 * 60_000L
        const val PUBLISH_FRESHNESS_MS = 12 * 60_000L
        /** Refresh ping-ovi stariji od ovog se ignorišu (verovatno zaboravljeni od dead FGS-a). */
        const val REFRESH_REQUEST_TTL_MS = 5 * 60_000L
        const val SOS_TTL_MS = 30 * 60_000L
        /** Posle SOS trigger-a, self FGS prelazi na BURST 30min za peers' real-time tracking. */
        const val SOS_BOOST_DURATION_MS = 30 * 60_000L
        /** Kad peer pošalje refresh ping, BURST 5min — ako se kreće, prati ga taj period. */
        const val REFRESH_BOOST_DURATION_MS = 5 * 60_000L
        /** Movement filter — manje od 15m kretanja preskače publish. */
        const val SIGNIFICANT_MOVEMENT_M = 15f
        /** Force publish za freshness signal čak i bez kretanja. */
        const val FORCE_PUBLISH_INTERVAL_MS = 90_000L
        /** Maksimum prihvatljive accuracy — fixevi gori od ovog su nepouzdani. */
        const val MAX_ACCEPTABLE_ACCURACY_M = 100f
        /** Battery below this % i ne puni se → throttle profile na ×2 interval. */
        const val LOW_BATTERY_THRESHOLD = 15
        private const val EXTRA_FORCE_REFRESH = "force_refresh"
        private const val EXTRA_SOS_BOOST = "sos_boost"
        const val EXTRA_ACTIVITY_CHANGED = "activity_changed"
        private const val ACTIVITY_RECOGNITION_REQUEST_CODE = 4242

        /**
         * Trenutno detektovana korisnička aktivnost (DetectedActivity constants).
         * Pisana iz ActivityRecognitionReceiver-a, čitana iz computeProfile.
         */
        @Volatile var detectedActivity: Int = DetectedActivity.UNKNOWN

        /** Live-process flag. Worker chita ovo da preskoči start ako je FGS već živ. */
        val isRunning = AtomicBoolean(false)

        /** Timestamp poslednjeg uspešnog publish-a. Worker chita za freshness check. */
        @Volatile var lastPublishAtMs: Long = 0L

        /** Lifetime poslednje instance — Worker koristi za kill-loop detekciju. */
        @Volatile var lastFgsLifetimeMs: Long = 0L

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

        /**
         * SOS trigger — postavlja BURST profil 30min. MapViewModel.triggerSos zove ovo
         * paralelno sa SosRepository.trigger() da peers dobijaju frequent location updates.
         */
        fun triggerSosBoost(context: Context) {
            if (!PermissionUtils.hasForegroundLocation(context)) {
                Timber.d("triggerSosBoost skipped — no location permission")
                return
            }
            ensureChannel(context)
            val intent = Intent(context, LocationTrackingService::class.java)
                .putExtra(EXTRA_SOS_BOOST, true)
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
