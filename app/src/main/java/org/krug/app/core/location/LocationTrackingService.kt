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
import android.os.SystemClock
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
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
    @Inject lateinit var batteryAlertNotifier: org.krug.app.core.battery.BatteryAlertNotifier
    @Inject lateinit var tripDetector: org.krug.app.core.driving.TripDetector
    @Inject lateinit var localPrefs: LocalPrefs
    @Inject lateinit var placeRepository: org.krug.app.core.places.PlaceRepository
    @Inject lateinit var placeEventNotifier: org.krug.app.core.places.PlaceEventNotifier
    @Inject lateinit var geofenceManager: org.krug.app.core.places.GeofenceManager
    @Inject lateinit var locationHistoryRepository: LocationHistoryRepository
    @Inject lateinit var speedingDetector: org.krug.app.core.speeding.SpeedingDetector
    @Inject lateinit var speedingRepository: org.krug.app.core.speeding.SpeedingRepository
    @Inject lateinit var speedingAlertNotifier: org.krug.app.core.speeding.SpeedingAlertNotifier
    @Inject lateinit var checkInRepository: org.krug.app.core.checkin.CheckInRepository
    @Inject lateinit var checkInNotifier: org.krug.app.core.checkin.CheckInNotifier
    @Inject lateinit var etaRepository: org.krug.app.core.eta.EtaRepository
    @Inject lateinit var etaNotifier: org.krug.app.core.eta.EtaNotifier
    @Inject lateinit var directionsRepository: org.krug.app.core.directions.DirectionsRepository
    @Inject lateinit var crashDetector: org.krug.app.core.crash.CrashDetector
    @Inject lateinit var crashAlertNotifier: org.krug.app.core.crash.CrashAlertNotifier
    @Inject lateinit var crashActionBus: org.krug.app.core.crash.CrashActionBus
    @Inject lateinit var deviceRegistry: org.krug.app.core.device.DeviceRegistry

    /**
     * Cache displayName-a self korisnika — puni observeSelfName() na startu FGS-a i
     * osvežava se dok god UserRepository emit-uje. Koristi se za speeding event-e
     * (SpeedingDetector emit-uje sa userName-om u payload-u da observers ne bi morali
     * dodatan fetch). Prazan string je fine fallback (Notifier ima „unknown_sender").
     */
    @Volatile private var selfDisplayName: String = ""

    /**
     * In-memory watermark speeding event ID-jeva koje smo već notifikovali (per-process,
     * kao lastNotifiedPlaceEventIds). Sprečava re-notif na svaki snapshot listener update.
     */
    private val notifiedSpeedingEventIds = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>(),
    )

    /** Analogno notifiedSpeedingEventIds — sprečava re-notif check-in event-a. */
    private val notifiedCheckInEventIds = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>(),
    )

    /** Per-share dedup za „started" i „arrived" notif-e — key = shareId, ne user id. */
    private val notifiedEtaStarted = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>(),
    )
    private val notifiedEtaArrived = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>(),
    )

    /**
     * Timestamp poslednjeg ETA update-a za sopstveni share. Throttle-uje directions API
     * pozive (ne recompute na svaki fix; jedan svakih ETA_UPDATE_INTERVAL_MS).
     */
    @Volatile private var lastEtaUpdateAtMs: Long = 0L

    /**
     * Ako je user u aktivnom ETA share modu — cache-ovan snapshot destination-a da bismo
     * mogli da radimo brz distance-to-destination check bez novog Firestore read-a.
     * Null = nema aktivnog share-a. Osvežava se preko observeSelfEtaShare().
     */
    @Volatile private var activeEtaShare: org.krug.app.core.eta.EtaShareModel? = null

    /**
     * Aktivan crash-countdown job — schedule-uje SOS trigger 10s posle detekcije.
     * Cancelled kad user tap-uje „Dobro sam" ili kad forsira „Pošalji odmah".
     */
    @Volatile private var crashCountdownJob: kotlinx.coroutines.Job? = null

    /** Flag „crash detektor je pokrenut" — sprečava double-start pri settings toggle-u. */
    @Volatile private var crashDetectorStarted: Boolean = false

    /**
     * Multi-device publish gate: cache-ovan lastActiveMs najskorije aktivnog uređaja u
     * user-ovom device registry-ju (self isključen). Ako je self uređaj skorije aktivan
     * (moj heartbeat > max drugih), self je primary → publish. Inače skip.
     * Osvežava se u observeSelfDevices() na svaku promenu subcollection-a.
     */
    @Volatile private var latestOtherDeviceActiveMs: Long = 0L

    /**
     * Per-uid map: poslednji `triggeredAt` koji je već notifikovan korisnika.
     * Lazy-load iz LocalPrefs u onCreate — bez persistence-a, ako Android ubije proces
     * (OOM, ANR, BootReceiver restart), isti SOS bi pri sledećoj observe-emisiji
     * fired-ovao notifikaciju ponovo. TTL filter u loadSosNotified() drop-uje stare.
     */
    private lateinit var knownSosTriggered: MutableMap<String, Long>

    /**
     * Per-uid map: timestamp poslednjeg battery alert-a. 12h TTL sprečava spam kad je
     * member trajno na niskoj bateriji. Kad batteryPct pređe CLEAR threshold (25%),
     * entry se briše pa sledeći pad ispod 20% opet trigeruje.
     */
    private lateinit var knownBatteryAlerted: MutableMap<String, Long>

    private lateinit var fused: FusedLocationProviderClient
    private var activityClient: ActivityRecognitionClient? = null
    private var activityPendingIntent: PendingIntent? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var currentSettings: UserSettings = UserSettings()
    @Volatile private var currentProfile: LocationProfile? = null

    /** Movement filter: poslednja PUBLISHED lokacija (ne svaki callback). */
    @Volatile private var lastPublishedLat: Double? = null
    @Volatile private var lastPublishedLng: Double? = null

    /**
     * elapsedRealtimeNanos poslednjeg PUBLISHED fix-a. Koristi se za monotonic guard
     * (odbaci fix koji je stariji od objavljenog) i plausibility check (implied brzina).
     * SystemClock.elapsedRealtimeNanos je monotonic since boot, nije osetljiv na promenu
     * sistemskog sata (nije loc.time koji je walll-clock).
     */
    @Volatile private var lastPublishedElapsedNanos: Long = 0L

    /** Trenutak start-a ove instance — onDestroy ga koristi za FGS lifetime telemetry. */
    @Volatile private var startedAtMs: Long = 0L

    /** Aktivni boost-expiry coroutine — cancel-uje se eksplicitno pre scope.cancel u onDestroy. */
    @Volatile private var boostExpiryJob: kotlinx.coroutines.Job? = null

    /**
     * Cleanup handle za `LocationRepository.bindOnlinePresence`. onDestroy ga zove da
     * otkači `.info/connected` listener + cancel-uje onDisconnect handler + eksplicitno
     * upisuje `online=false` (clean shutdown umesto da čekamo RTDB timeout).
     */
    @Volatile private var presenceCleanup: Runnable? = null

    /**
     * BURST profil: SOS aktivan ili peer-ov refresh ping. Drži veoma frequent
     * fix interval kratko vreme (30min SOS, 5min refresh). Posle isteka, profil
     * se prirodno vraća na battery mode default.
     */
    @Volatile private var sosBoostUntilMs: Long = 0L
    @Volatile private var refreshBoostUntilMs: Long = 0L

    /**
     * Spam cap za refresh ping-ove — peer pošalje "Osveži lokaciju", dobije BURST 5min.
     * Ako isti peer spamuje dugme u tom roku, ignorišemo dodatne ping-ove (boost je
     * već u toku, novi boost samo ekstenduje period čime BURST drift-uje indefinitno
     * i jede bateriju). Per-requester last-boost timestamp; ako je u poslednjih
     * REFRESH_BOOST_COOLDOWN_MS, skip.
     */
    private val lastBoostByRequester = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            if (!passesQualityGate(loc, isCachedLastLocation = false)) return
            val (battery, charging) = readBattery()
            reconfigureIfNeeded()
            // Temporary sharing auto-expiry: proverava na svaki fix. Ako je timer istekao,
            // gasi share + čisti flag. Peer-i odmah dobijaju "paused" preko Firestore
            // listener-a (ne moraju da čekaju sledeći publish jer sledeći publish ne dolazi
            // — share je off). Idempotentno: ako je već isteklo, sledeći callback samo skip.
            checkAndExpireTemporarySharing()
            if (!currentSettings.shareLocationGlobal) {
                Timber.d("Location sharing paused; skipping publish")
                return
            }
            // Multi-device publish gate — samo najskorije aktivan uređaj u user-ovom
            // device registry-ju publish-uje. Drugi ne publish-uje da izbegnemo RTDB
            // location race izmedju dva user-ova uređaja. Async heartbeat write ide
            // svejedno — na osnovu tog signala drugi uređaj zna „ja sam još živ".
            val nowMs = System.currentTimeMillis()
            scope.launch {
                firebaseAuth.currentUser?.uid?.let { deviceRegistry.heartbeat(it) }
            }
            if (!isPrimaryDevice(nowMs)) {
                Timber.d("Not primary device (other active ${nowMs - latestOtherDeviceActiveMs}ms ago); skip publish")
                return
            }
            // Movement filter — preskoči publish ako se nismo mnogo pomerili, OSIM ako je
            // bilo predugo od poslednjeg publish-a (peers očekuju fresh updatedAt signal).
            val uid = firebaseAuth.currentUser?.uid ?: return
            // Trip detection radi na SVAKI fix nezavisno od publish gate-a — čak i kad
            // movement filter odbaci publish (npr. staje na semaforu), speed je važan
            // signal za state machine (quiet timeout).
            tripDetector.onFix(
                uid = uid,
                lat = loc.latitude,
                lng = loc.longitude,
                speedMps = loc.speed,
                nowMs = System.currentTimeMillis(),
            )
            // Speeding detektor — filtrira sam po enabled flag-u, ne pozivamo uslovno
            // (state machine treba svaku brzinu da bi znao kad user padne ispod threshold-a).
            speedingDetector.onFix(
                uid = uid,
                userName = selfDisplayName,
                lat = loc.latitude,
                lng = loc.longitude,
                speedMps = loc.speed,
                accuracyM = loc.accuracy,
                nowMs = System.currentTimeMillis(),
                thresholdKmh = currentSettings.speedingThresholdKmh,
                enabled = currentSettings.speedingAlertsEnabled,
            )
            // ETA share update — throttled interno u maybeUpdateEtaShare-u; ne-op ako nema aktivnog share-a.
            maybeUpdateEtaShare(uid = uid, curLat = loc.latitude, curLng = loc.longitude)
            // Crash detektor — hrani brzinom da zna „user je bio u vožnji" kontekst.
            // No-op ako detektor nije startovan (crashDetectorStarted flag u syncCrashDetectorState).
            if (crashDetectorStarted) {
                crashDetector.onDrivingSpeed(loc.speed, System.currentTimeMillis())
            }
            if (!shouldPublish(loc)) {
                Timber.d("Skip publish: movement < ${SIGNIFICANT_MOVEMENT_M}m + recent publish")
                return
            }
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
                    lastPublishedElapsedNanos = loc.elapsedRealtimeNanos
                }.onFailure { ex ->
                    // FGS shutdown / scope cancel je normalan lifecycle event — ne loguj kao W
                    // (CrashlyticsTree bi to forward-ovao u dashboard kao false-positive).
                    if (ex is CancellationException) Timber.d("publish location cancelled (scope dying)")
                    else Timber.w(ex, "publish location failed")
                }
                // History write — skuplji nego RTDB publish, pa dodatan filter:
                // pomeranje >25m ILI >10min od poslednjeg zapisa. Bez ovog Firestore
                // se popuni na 4000+ dokumenata/dan/user.
                if (shouldWriteHistory(loc)) {
                    runCatching {
                        locationHistoryRepository.writePoint(
                            uid = uid, lat = loc.latitude, lng = loc.longitude,
                            accuracy = loc.accuracy, batteryPct = battery,
                            speed = loc.speed, bearing = loc.bearing,
                        )
                        lastHistoryLat = loc.latitude
                        lastHistoryLng = loc.longitude
                        lastHistoryTsMs = System.currentTimeMillis()
                    }.onFailure { ex ->
                        if (ex !is CancellationException) Timber.d("writeHistoryPoint failed: %s", ex.message)
                    }
                }
            }
        }
    }

    @Volatile private var lastHistoryLat: Double? = null
    @Volatile private var lastHistoryLng: Double? = null
    @Volatile private var lastHistoryTsMs: Long = 0L

    /**
     * `silentHours` format: "HH:MM-HH:MM" (24h, `null` = feature off).
     * Podržava wrap preko ponoći (npr "23:00-07:00" pokriva 23-24 i 0-7).
     */
    private fun isInSilentHours(silentHours: String?): Boolean {
        if (silentHours.isNullOrBlank()) return false
        val parts = silentHours.split("-")
        if (parts.size != 2) return false
        val (fromH, fromM) = parts[0].split(":").let { it[0].toIntOrNull() to it.getOrNull(1)?.toIntOrNull() }
        val (toH, toM) = parts[1].split(":").let { it[0].toIntOrNull() to it.getOrNull(1)?.toIntOrNull() }
        if (fromH == null || fromM == null || toH == null || toM == null) return false
        val cal = java.util.Calendar.getInstance()
        val nowMin = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        val fromMin = fromH * 60 + fromM
        val toMin = toH * 60 + toM
        return if (fromMin <= toMin) nowMin in fromMin..toMin
        else nowMin >= fromMin || nowMin <= toMin // wrap preko ponoći
    }

    private fun shouldWriteHistory(loc: android.location.Location): Boolean {
        val prevLat = lastHistoryLat
        val prevLng = lastHistoryLng
        val now = System.currentTimeMillis()
        if (prevLat == null || prevLng == null) return true
        val timeSince = now - lastHistoryTsMs
        if (timeSince >= HISTORY_MAX_INTERVAL_MS) return true
        val distance = FloatArray(1)
        android.location.Location.distanceBetween(prevLat, prevLng, loc.latitude, loc.longitude, distance)
        return distance[0] >= HISTORY_MIN_MOVEMENT_M
    }

    /**
     * Kvalitetni filteri koji sprečavaju "vraćanje unazad" na mapi kod peer-a:
     *  1) Age filter (samo za cached lastLocation) — Wi-Fi fingerprint iz fused cache-a
     *     može biti sat vremena star, i publish bi teleportovao člana.
     *  2) Accuracy — accuracy iznad thresholda znači unreliable fix.
     *  3) Monotonic guard — Android ponekad vrati fix čiji je elapsedRealtimeNanos
     *     stariji od poslednjeg objavljenog (fused interpolira iz starijih senzor sample-a).
     *  4) Plausibility (implied speed) — ako je implied brzina između poslednjeg fix-a i
     *     ovog iznad ~200 km/h, to je GPS outlier a ne stvarno kretanje.
     */
    private fun passesQualityGate(
        loc: android.location.Location,
        isCachedLastLocation: Boolean,
    ): Boolean {
        if (isCachedLastLocation) {
            val ageMs = (SystemClock.elapsedRealtimeNanos() - loc.elapsedRealtimeNanos) / 1_000_000L
            if (ageMs > LAST_LOCATION_MAX_AGE_MS) {
                Timber.d("Drop stale cached fix: ${ageMs}ms old (threshold ${LAST_LOCATION_MAX_AGE_MS}ms)")
                return false
            }
        }
        if (loc.accuracy > MAX_ACCEPTABLE_ACCURACY_M) {
            Timber.d("Drop low-accuracy fix: ${loc.accuracy}m (threshold ${MAX_ACCEPTABLE_ACCURACY_M}m)")
            return false
        }
        val lastElapsedNs = lastPublishedElapsedNanos
        if (lastElapsedNs > 0L && loc.elapsedRealtimeNanos < lastElapsedNs) {
            Timber.d("Drop non-monotonic fix (older than last published)")
            return false
        }
        val lastLat = lastPublishedLat
        val lastLng = lastPublishedLng
        if (lastLat != null && lastLng != null && lastElapsedNs > 0L) {
            val deltaSec = (loc.elapsedRealtimeNanos - lastElapsedNs) / 1_000_000_000.0
            if (deltaSec > 0.0) {
                val results = FloatArray(1)
                android.location.Location.distanceBetween(
                    lastLat, lastLng, loc.latitude, loc.longitude, results,
                )
                val impliedSpeed = results[0] / deltaSec
                if (impliedSpeed > MAX_PLAUSIBLE_SPEED_MPS) {
                    Timber.d(
                        "Drop implausible fix: ${results[0].toInt()}m in ${"%.1f".format(deltaSec)}s " +
                            "(${impliedSpeed.toInt()}m/s, threshold ${MAX_PLAUSIBLE_SPEED_MPS.toInt()}m/s)",
                    )
                    return false
                }
            }
        }
        return true
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

    /**
     * Temporary sharing auto-off. Ako je user postavio "deli 1h/4h/do kraja dana", ova
     * metoda ga na svakom fix-u proverava. Kad istekne, gasi share + čisti expiry field.
     * Idempotentno — sledeći poziv nakon expire samo vidi shareUntilMs=null i vrati odmah.
     */
    private fun checkAndExpireTemporarySharing() {
        val until = currentSettings.shareUntilMs ?: return
        if (System.currentTimeMillis() < until) return
        val uid = firebaseAuth.currentUser?.uid ?: return
        Timber.i("Temporary sharing expired (until=%d), auto-pausing", until)
        scope.launch {
            runCatching {
                settingsRepository.updateShareGlobal(uid, false)
                settingsRepository.updateShareUntil(uid, null)
            }.onFailure { Timber.w(it, "Failed to auto-expire temporary sharing") }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Defensive init lateinit knownSosTriggered PRE bilo kog ranog return-a — sprečava
        // NPE u onStartCommand/observeCircleSos ako Android iz nekog razloga uđe u
        // metode pre dovršetka onCreate-a. Empty mapa je idempotent početni state.
        knownSosTriggered = mutableMapOf()
        knownBatteryAlerted = mutableMapOf()
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
        // Override defensive empty init sa stvarnim disk-loaded podacima (sa TTL prune).
        knownSosTriggered = localPrefs.loadSosNotified(SOS_TTL_MS)
        knownBatteryAlerted = localPrefs.loadBatteryAlerted(BATTERY_ALERT_TTL_MS)
        Timber.i(
            "FGS start (SOS dedup=%d, battery dedup=%d)",
            knownSosTriggered.size,
            knownBatteryAlerted.size,
        )
        // Bind server-side presence — dok FGS živi, RTDB drži `online=true` na naš record.
        // Kad process umre (force stop, low memory kill, network loss > ~30s), server sam
        // postavlja online=false preko onDisconnect handler-a. Peer-i tako imaju precizan
        // signal umesto da moraju da izračunavaju iz updatedAt staleness-a.
        firebaseAuth.currentUser?.uid?.let { uid ->
            presenceCleanup = locationRepository.bindOnlinePresence(uid)
        }
        observeSettings()
        observeSelfName()
        observeRefreshRequests()
        observeCircleSos()
        observeCirclePlaces()
        observeCircleBattery()
        observeCircleSpeeding()
        observeCircleCheckIns()
        observeCircleEta()
        observeSelfEtaShare()
        observeCrashActions()
        observeSelfDevices()
        registerActivityRecognition()
        startHeartbeatLoop()
    }

    /**
     * Punjenje `selfDisplayName` cache-a. UserRepository emit-uje displayName preko
     * Firestore listener-a — pratimo dokle god je FGS živ. Bez ovog, SpeedingDetector.emit
     * ne bi imao userName za payload i observers bi videli prazno ime (fallback na
     * „Circle member" u notif copy).
     */
    private fun observeSelfName() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        scope.launch {
            userRepository.observeUser(uid).filterNotNull().collectLatest { u ->
                val resolved = u.displayName.takeIf { it.isNotBlank() }
                    ?: u.email.substringBefore('@').takeIf { it.isNotBlank() }
                    ?: org.krug.app.core.util.DeviceNames.friendly(u.deviceModel)
                        .takeIf { it.isNotBlank() }
                    ?: ""
                selfDisplayName = resolved
            }
        }
    }

    /**
     * Prati speeding event-e svih krugova, dispatch-uje notif kao za placeEvents.
     * Isti filter stack: sopstveni event skip, notif-toggle, silent hours, per-event
     * dedup preko `notifiedSpeedingEventIds`. NEMA per-place mute (govore o brzini
     * konkretnog usera, ne o lokaciji).
     */
    private fun observeCircleSpeeding() {
        val selfUid = firebaseAuth.currentUser?.uid ?: return
        scope.launch {
            circleRepository.observeMyCircles(selfUid)
                .flatMapLatest { circles ->
                    val circleIds = circles.map { it.id }
                    if (circleIds.isEmpty()) {
                        flowOf(emptyList<org.krug.app.core.speeding.SpeedingEventModel>())
                    } else {
                        combine(
                            circleIds.map { cid -> speedingRepository.observeRecentEvents(cid) },
                        ) { arrays -> arrays.toList().flatten() }
                    }
                }
                .collectLatest { events ->
                    val cutoff = System.currentTimeMillis() - SPEEDING_EVENT_MAX_AGE_MS
                    events.forEach { evt ->
                        if (evt.userId == selfUid) return@forEach
                        if (evt.id in notifiedSpeedingEventIds) return@forEach
                        val ts = evt.timestamp?.time ?: 0L
                        if (ts < cutoff) {
                            notifiedSpeedingEventIds.add(evt.id)
                            return@forEach
                        }
                        notifiedSpeedingEventIds.add(evt.id)
                        if (isInSilentHours(currentSettings.silentHours)) return@forEach
                        speedingAlertNotifier.notifySpeeding(evt)
                    }
                }
        }
    }

    /**
     * Periodični heartbeat — refresh-uje updatedAt na RTDB bez publish-a novog fix-a.
     *
     * Scenario koji ovo rešava: user je zaključao telefon i stoji u mestu.
     * - FGS je živ (isRunning=true)
     * - RTDB `.info/connected` je i dalje true (nema disconnect-a)
     * - Ali GPS callback je throttled od strane Doze mode-a (može trajati 15+min bez fix-a)
     * - Peers vide `online=true` (RTDB radi) ali `updatedAt` je stariji od 5min
     * - `MemberWithLocation.isOffline()` = true → user greškom prikazan kao offline
     *
     * Heartbeat piše samo `updatedAt=ServerValue.TIMESTAMP` svakih HEARTBEAT_INTERVAL_MS
     * ako je poslednji publish stariji od tog intervala. Cheap ~200B write.
     *
     * NE menja lat/lng/battery — te vrednosti ostaju "poslednje poznate" dok GPS ne
     * fix-uje svež. Ali peers više neće videti isOffline() jer je updatedAt fresh.
     *
     * Ako je publishLocation upravo pisao (< HEARTBEAT_INTERVAL_MS), heartbeat skipuje —
     * `updatedAt` je već svež kroz publish path.
     */
    private fun startHeartbeatLoop() {
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(HEARTBEAT_INTERVAL_MS)
                val uid = firebaseAuth.currentUser?.uid ?: continue
                if (!currentSettings.shareLocationGlobal) continue
                val sinceLastPublish = System.currentTimeMillis() - lastPublishAtMs
                if (sinceLastPublish < HEARTBEAT_INTERVAL_MS) continue
                runCatching { locationRepository.heartbeat(uid) }
                    .onSuccess { Timber.d("heartbeat ok (age=%dms)", sinceLastPublish) }
            }
        }
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
            // ACTIVITY_RECOGNITION je runtime permission; user je može povući između
            // register/unregister-a. runCatching pokriva SecurityException.
            @Suppress("MissingPermission")
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
            // forceRefresh = user tap "Osveži"; ide u HIGH_ACCURACY GPS bez cache-a.
            requestOneShotFix(userInitiated = forceRefresh)
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
    private fun requestOneShotFix(userInitiated: Boolean = false) {
        Timber.d(
            "requestOneShotFix invoked (userInitiated=%s, shareGlobal=%s)",
            userInitiated, currentSettings.shareLocationGlobal,
        )
        if (!currentSettings.shareLocationGlobal) return
        val uid = firebaseAuth.currentUser?.uid ?: run {
            Timber.w("requestOneShotFix: no firebase user")
            return
        }
        // User-initiated refresh (tap "Osveži" ili peer refresh ping): PRESKOČI cache
        // publish. User očekuje FRESH GPS, ne 30s stari Wi-Fi fingerprint. Cache pub
        // ima smisla za automatic entry-je (boot, worker) gde je "brz answer" > "svež".
        if (!userInitiated) {
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
        }
        // Korak 2: jednokratni location update.
        //  - User-initiated → HIGH_ACCURACY (stvarno GPS satelit), 5s max age, čekaj fresh fix.
        //    Bez ovog, "Osveži" vraća stari Wi-Fi cache dok su user-i u kolima 300m dalje.
        //  - Automatic → BALANCED (Wi-Fi/cell OK), 60s cache, ne čeka GPS. Brz odgovor važniji.
        val priority = if (userInitiated) Priority.PRIORITY_HIGH_ACCURACY
        else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        val maxAge = if (userInitiated) 5_000L else 60_000L
        val waitForAccurate = userInitiated
        val req = LocationRequest.Builder(priority, 0L)
            .setMaxUpdates(1)
            .setMaxUpdateAgeMillis(maxAge)
            .setWaitForAccurateLocation(waitForAccurate)
            // Ako user-initiated, ne odustaj brzo od GPS fix-a. 15s je razuman timeout
            // (obično fresh GPS stigne u 3-8s u otvorenom, do 15s indoor).
            .setDurationMillis(if (userInitiated) 15_000L else Long.MAX_VALUE)
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
        // Cached fix iz fused.lastLocation može biti sat vremena star (Wi-Fi fingerprint
        // od pre — user je u međuvremenu prešao 5km). Age gate mora da bude tu SAMO za
        // taj slučaj; "one-shot-updates" callback je uvek fresh fix.
        val isCachedFix = source == "last-location"
        if (!passesQualityGate(loc, isCachedLastLocation = isCachedFix)) return
        val (battery, charging) = readBattery()
        // Bearing i speed iz Android Location-a. `hasBearing()` / `hasSpeed()` su false ako
        // GPS još nije fix-ovao smer (statičan user, indoor, fresh launch). 0 default je OK
        // jer course-up logika gleda `speed > threshold` pa 0 znači "miruj north-up".
        val bearing = if (loc.hasBearing()) loc.bearing else 0f
        val speed = if (loc.hasSpeed()) loc.speed else 0f
        scope.launch {
            runCatching {
                locationRepository.publish(
                    uid = uid,
                    lat = loc.latitude,
                    lng = loc.longitude,
                    accuracy = loc.accuracy,
                    batteryPct = battery,
                    isCharging = charging,
                    bearing = bearing,
                    speed = speed,
                )
                lastPublishAtMs = System.currentTimeMillis()
                lastPublishedLat = loc.latitude
                lastPublishedLng = loc.longitude
                lastPublishedElapsedNanos = loc.elapsedRealtimeNanos
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
                // Crash detector se pokreće/gasi na promenu settings-a. Bez pokretanja u
                // ovom pathu, dodavanje toggle-a u runtime ne bi imalo efekta dok FGS ne
                // resttart-uje.
                syncCrashDetectorState()
            }
        }
    }

    /** Idempotentno: pokreni detektor ako je flag true a nije running; stopiraj ako je flag false a running. */
    private fun syncCrashDetectorState() {
        if (currentSettings.crashDetectionEnabled) {
            if (!crashDetectorStarted) {
                crashDetector.start { onCrashDetected() }
                crashDetectorStarted = true
            }
        } else {
            if (crashDetectorStarted) {
                crashDetector.stop()
                crashDetectorStarted = false
                // Otkaži tekući countdown ako user gasi toggle usred njega.
                crashCountdownJob?.cancel()
                crashCountdownJob = null
                crashAlertNotifier.cancel()
            }
        }
    }

    /**
     * Countdown flow — okinuto iz CrashDetector-a. Zakazuje SOS trigger za CRASH_COUNTDOWN_SEC
     * sekundi u budućnosti, update-uje notif svake sekunde da user vidi countdown.
     * Otkaz preko CrashActionBus-a (observeCrashActions).
     */
    private fun onCrashDetected() {
        // Ako je već aktivan countdown u toku, ignore double-fire (rate-limit je i u detektoru).
        if (crashCountdownJob?.isActive == true) return
        crashCountdownJob = scope.launch {
            for (remaining in CRASH_COUNTDOWN_SEC downTo 1) {
                crashAlertNotifier.postCountdown(remaining)
                kotlinx.coroutines.delay(1_000L)
            }
            triggerCrashSos()
            crashAlertNotifier.cancel()
        }
    }

    private fun observeCrashActions() {
        scope.launch {
            crashActionBus.actions.collect { action ->
                when (action) {
                    org.krug.app.core.crash.CrashAction.Cancel -> {
                        crashCountdownJob?.cancel()
                        crashCountdownJob = null
                        crashAlertNotifier.cancel()
                        Timber.i("Crash SOS cancelled by user")
                    }
                    org.krug.app.core.crash.CrashAction.SendNow -> {
                        crashCountdownJob?.cancel()
                        crashCountdownJob = null
                        crashAlertNotifier.cancel()
                        triggerCrashSos()
                        Timber.i("Crash SOS forced immediate by user")
                    }
                }
            }
        }
    }

    /**
     * Trigger SOS iz crash detekcije. Reuse-uje SosRepository sa jasnim tagom u message-u
     * da observers znaju da je auto-detected (razlikuje od ručnog SOS-a).
     */
    private fun triggerCrashSos() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        val activeCircle = runCatching {
            kotlinx.coroutines.runBlocking {
                circleRepository.observeMyCircles(uid).first().firstOrNull()
            }
        }.getOrNull()
        scope.launch {
            runCatching {
                sosRepository.trigger(
                    uid = uid,
                    lat = 0.0, lng = 0.0,
                    circleId = activeCircle?.id,
                    senderName = selfDisplayName,
                    circleName = activeCircle?.name,
                    message = "Automatic crash alert",
                )
            }.onFailure { Timber.w(it, "Crash SOS trigger failed") }
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

    /**
     * Persistovan per-krug high-watermark za `placeEvents`. Event čiji je timestamp
     * <= lastSeenTsByCircle[cid] se ignoriše (replay guard koji preživljava app kill).
     * Fallback za novi krug (nema entry) je `placesListenerStartedAt` iz istog session-a,
     * pa novi krug ne dobija spam iz prošlosti pri prvom listen-u.
     */
    private val lastSeenTsByCircle = java.util.Collections.synchronizedMap(HashMap<String, Long>())
    /** Fallback timestamp za krugove koji još nemaju persistovan lastSeen. */
    @Volatile private var placesListenerStartedAt: Long = 0L
    private val notifiedPlaceEventIds = java.util.Collections.synchronizedSet(HashSet<String>())
    /** Snapshot muted placeId-jeva — populate iz places tracker block-a. */
    private val mutedPlaceIds = java.util.Collections.synchronizedSet(HashSet<String>())

    /**
     * Registruje geofence-e za sve places-e svih krugova + observe-uje placeEvents
     * subcollection radi lokalne notifikacije. Sopstveni event-i se filtrira
     * (ne notifikuje sebe za svoj enter/exit).
     */
    private fun observeCirclePlaces() {
        val selfUid = firebaseAuth.currentUser?.uid ?: return
        placesListenerStartedAt = System.currentTimeMillis()
        // Reload persist-ovanog per-krug lastSeen ts u memory. Novi krug bez entry-ja
        // koristi placesListenerStartedAt fallback (ne dobija replay iz prošlosti).
        synchronized(lastSeenTsByCircle) {
            lastSeenTsByCircle.clear()
            lastSeenTsByCircle.putAll(localPrefs.loadLastSeenPlaceEventTs())
        }
        // 1) Prati sve places i re-registruj geofence-e kad se lista promeni.
        scope.launch {
            circleRepository.observeMyCircles(selfUid)
                .flatMapLatest { circles ->
                    val circleIds = circles.map { it.id }
                    if (circleIds.isEmpty()) flowOf(emptyList())
                    else combine(
                        circleIds.map { cid ->
                            placeRepository.observePlaces(cid).map { places -> cid to places }
                        },
                    ) { pairs -> pairs.toList() }
                }
                .collectLatest { pairs ->
                    val entries = pairs.flatMap { (cid, places) ->
                        places.map { p ->
                            org.krug.app.core.places.GeofenceEntry(
                                circleId = cid, placeId = p.id,
                                lat = p.lat, lng = p.lng, radius = p.radius,
                            )
                        }
                    }
                    // Snapshot muted placeIds — koristi se u notif dispatch-u ispod.
                    val muted = pairs.flatMap { it.second }.filter { it.muted }.map { it.id }
                    synchronized(mutedPlaceIds) {
                        mutedPlaceIds.clear()
                        mutedPlaceIds.addAll(muted)
                    }
                    geofenceManager.removeAll()
                    if (entries.isNotEmpty()) geofenceManager.registerAll(entries)
                }
        }
        // 2) Prati placeEvents svih krugova i pokazuj lokalnu notif za tuđe event-e.
        // Zadržavamo cid preko pair-a da bismo mogli da ga proslijedimo u notif intent.
        scope.launch {
            circleRepository.observeMyCircles(selfUid)
                .flatMapLatest { circles ->
                    val circleIds = circles.map { it.id }
                    if (circleIds.isEmpty()) flowOf(emptyList<Pair<String, org.krug.app.core.places.PlaceEventModel>>())
                    else combine(
                        circleIds.map { cid ->
                            placeRepository.observeRecentEvents(cid).map { events ->
                                events.map { cid to it }
                            }
                        },
                    ) { arrays -> arrays.toList().flatten() }
                }
                .collectLatest { pairs ->
                    var persistDirty = false
                    pairs.forEach { (cid, evt) ->
                        if (evt.userId == selfUid) return@forEach
                        if (evt.id in notifiedPlaceEventIds) return@forEach
                        val ts = evt.timestamp?.time ?: 0L
                        // Per-krug high-watermark: koristi persist-ovanu vrednost ako
                        // postoji, u suprotnom current-session fallback (placesListenerStartedAt).
                        val watermark = synchronized(lastSeenTsByCircle) {
                            lastSeenTsByCircle[cid]
                        } ?: placesListenerStartedAt
                        if (ts <= watermark) {
                            notifiedPlaceEventIds.add(evt.id)
                            return@forEach
                        }
                        notifiedPlaceEventIds.add(evt.id)
                        // Podigni watermark odmah — čak i ako ne notifikujemo (silent hours,
                        // muted place), event je "viđen" pa ne treba da se replay-uje posle
                        // restart-a. Persist-ujemo u batch-u na kraju collect-a.
                        synchronized(lastSeenTsByCircle) {
                            val prev = lastSeenTsByCircle[cid] ?: 0L
                            if (ts > prev) {
                                lastSeenTsByCircle[cid] = ts
                                persistDirty = true
                            }
                        }
                        if (!currentSettings.placeNotifsEnabled) return@forEach
                        if (isInSilentHours(currentSettings.silentHours)) return@forEach
                        if (evt.placeId in mutedPlaceIds) return@forEach
                        placeEventNotifier.notifyEvent(evt, circleId = cid)
                    }
                    if (persistDirty) {
                        val snapshot = synchronized(lastSeenTsByCircle) {
                            HashMap(lastSeenTsByCircle)
                        }
                        localPrefs.saveLastSeenPlaceEventTs(snapshot)
                    }
                }
        }
    }

    /**
     * Observe-uje battery% svih članova svih krugova kojima pripadam.
     * Trigger-uje `BatteryAlertNotifier.notifyLowBattery(...)` kad peer padne ispod
     * PEER_BATTERY_ALERT_THRESHOLD (20%) i nije na punjaču. Dedup 12h per peer.
     * Kad batteryPct pređe PEER_BATTERY_CLEAR_THRESHOLD (25%), entry iz dedup mape
     * se briše pa sledeći pad ispod 20% opet trigeruje (npr. sutradan).
     *
     * Poštuje `UserSettings.batteryAlertsEnabled` i silent hours (isti kao Place event-i).
     */
    /**
     * Prati sopstveni ETA share iz svih krugova — trenutno pretpostavljamo da user ima
     * najviše jedan aktivan share u bilo kom krugu. Uzimamo prvi non-null. Cache-uje se u
     * `activeEtaShare` za brzu proveru u locationCallback-u.
     */
    private fun observeSelfEtaShare() {
        val selfUid = firebaseAuth.currentUser?.uid ?: return
        scope.launch {
            circleRepository.observeMyCircles(selfUid)
                .flatMapLatest { circles ->
                    val circleIds = circles.map { it.id }
                    if (circleIds.isEmpty()) {
                        flowOf(null as org.krug.app.core.eta.EtaShareModel?)
                    } else {
                        combine(
                            circleIds.map { cid -> etaRepository.observeMyShare(cid, selfUid) },
                        ) { arrays -> arrays.firstNotNullOfOrNull { it } }
                    }
                }
                .collectLatest { share ->
                    activeEtaShare = share
                }
        }
    }

    /**
     * Prati tuđe ETA share-ove iz svih krugova. Emit-uje started/arrived notif-e sa
     * dedup-om preko notifiedEtaStarted / notifiedEtaArrived skupova.
     */
    private fun observeCircleEta() {
        val selfUid = firebaseAuth.currentUser?.uid ?: return
        scope.launch {
            circleRepository.observeMyCircles(selfUid)
                .flatMapLatest { circles ->
                    val circleIds = circles.map { it.id }
                    if (circleIds.isEmpty()) {
                        flowOf(emptyList<org.krug.app.core.eta.EtaShareModel>())
                    } else {
                        combine(
                            circleIds.map { cid -> etaRepository.observeActiveShares(cid) },
                        ) { arrays -> arrays.toList().flatten() }
                    }
                }
                .collectLatest { shares ->
                    Timber.d("observeCircleEta: %d shares received", shares.size)
                    shares.forEach { share ->
                        if (share.userId == selfUid) return@forEach
                        val hasArrived = share.arrivedAt != null
                        if (hasArrived) {
                            if (share.id !in notifiedEtaArrived) {
                                notifiedEtaArrived.add(share.id)
                                if (isInSilentHours(currentSettings.silentHours)) {
                                    Timber.d("observeCircleEta: silent hours, skip arrived notif for %s", share.userId)
                                } else {
                                    etaNotifier.notifyArrived(share)
                                }
                            }
                        } else {
                            if (share.id !in notifiedEtaStarted) {
                                notifiedEtaStarted.add(share.id)
                                if (isInSilentHours(currentSettings.silentHours)) {
                                    Timber.d("observeCircleEta: silent hours, skip started notif for %s", share.userId)
                                } else {
                                    Timber.i("observeCircleEta: firing started notif for uid=%s dest=%s", share.userId, share.destinationLabel)
                                    etaNotifier.notifyStarted(share)
                                }
                            } else {
                                Timber.d("observeCircleEta: already notified for %s", share.id)
                            }
                        }
                    }
                }
        }
    }

    /**
     * Live ETA update pipeline — poziva se iz locationCallback-a kad postoji aktivan
     * sopstveni share. Throttle na 60s (ETA_UPDATE_INTERVAL_MS), directions request u
     * IO scope-u da ne blokira location publish tok. Kad je remaining < arrivalRadiusM,
     * marks arrived.
     */
    private fun maybeUpdateEtaShare(uid: String, curLat: Double, curLng: Double) {
        val share = activeEtaShare ?: return
        val now = System.currentTimeMillis()
        // Ako je već arrivedAt označen, ne diramo — dozvoli TTL/cancel put da uradi cleanup.
        if (share.arrivedAt != null) return
        val distToDest = distanceMeters(curLat, curLng, share.destinationLat, share.destinationLng)
        // Arrival check — nezavisan od throttle-a (užurbani prilaz destinaciji ne treba čekati).
        if (distToDest <= ETA_ARRIVAL_RADIUS_M) {
            scope.launch {
                val circleIds = runCatching {
                    circleRepository.observeMyCircles(uid).first().map { it.id }
                }.getOrDefault(emptyList())
                if (circleIds.isNotEmpty()) {
                    etaRepository.markArrived(circleIds, uid)
                    // Scheduled cleanup — bez TTL-a na etaShares (Console-side), doc bi ostao
                    // zauvek posle arrival-a. Delay 15min: dovoljno da observers vide „stigao/la"
                    // banner i notif, a onda auto-cleanup. Ako user pokrene novi share pre isteka,
                    // startShare prepisuje doc (docId=uid), pa cancel neće obrisati taj novi.
                    // Zato pre delete-a proveravamo activeEtaShare snapshot.
                    scope.launch {
                        kotlinx.coroutines.delay(ETA_ARRIVED_CLEANUP_DELAY_MS)
                        val current = activeEtaShare
                        // Ako je user u međuvremenu pokrenuo novi share, arrivedAt će biti null →
                        // ne diramo. Ako je isti share (arrivedAt != null), brišemo.
                        if (current?.arrivedAt != null) {
                            etaRepository.cancelShare(circleIds, uid)
                        }
                    }
                }
            }
            return
        }
        if (now - lastEtaUpdateAtMs < ETA_UPDATE_INTERVAL_MS) return
        lastEtaUpdateAtMs = now
        scope.launch {
            val route = directionsRepository.driveEta(
                originLat = curLat, originLng = curLng,
                destLat = share.destinationLat, destLng = share.destinationLng,
            )
            val (etaMin, remKm) = if (route != null) {
                val min = (route.durationSec / 60.0).toInt().coerceAtLeast(1)
                val km = route.distanceMeters / 1000.0
                min to km
            } else {
                // Fallback: haversine / 40 km/h.
                val km = distToDest / 1000.0
                val min = ((km / 40.0) * 60.0).toInt().coerceAtLeast(1)
                min to km
            }
            val circleIds = runCatching {
                circleRepository.observeMyCircles(uid).first().map { it.id }
            }.getOrDefault(emptyList())
            if (circleIds.isNotEmpty()) {
                etaRepository.updateShare(
                    circleIds = circleIds,
                    userId = uid,
                    currentLat = curLat, currentLng = curLng,
                    etaMinutes = etaMin, remainingKm = remKm,
                )
            }
        }
    }

    private fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val out = FloatArray(1)
        android.location.Location.distanceBetween(lat1, lng1, lat2, lng2, out)
        return out[0].toDouble()
    }

    /**
     * Prati device registry i cache-uje `latestOtherDeviceActiveMs` — publish gate koristi
     * ovaj signal da odluči je li ovaj uređaj primary (najskorije aktivan). Bez ovog,
     * dva uređaja istog user-a bi naizmenično prepisivala RTDB lokaciju i peers bi videli
     * jitter (kuća ↔ posao).
     */
    private fun observeSelfDevices() {
        val selfUid = firebaseAuth.currentUser?.uid ?: return
        val myId = deviceRegistry.deviceId
        scope.launch {
            deviceRegistry.observeDevices(selfUid).collectLatest { devices ->
                val others = devices.filter { it.deviceId != myId }
                val mostRecent = others.maxByOrNull { it.lastActiveMs }
                latestOtherDeviceActiveMs = mostRecent?.lastActiveMs ?: 0L
                latestOtherDeviceId = mostRecent?.deviceId
            }
        }
    }

    /**
     * Publish gate — vraća true ako je ovaj uređaj primary za publikovanje lokacije.
     *
     * Pravilo: self je primary AKO
     *  (a) nema drugih uređaja (fresh install / single-device user), ILI
     *  (b) drugi uređaj nije aktivan u poslednjih `OTHER_DEVICE_ACTIVE_WINDOW_MS`
     *      (npr. tablet je izlaznut, samo telefon vozi). Bez ovog, telefon ne bi
     *      publish-ovao dok god tablet ima recent heartbeat — ali tablet je off pa
     *      lokacija ne stiže.
     *
     * Kad su OBA uređaja aktivna (rare edge case: user ostavio oba upaljena):
     *  (c) primary je onaj sa lex-manjim deviceId string-om — deterministički,
     *      obe strane se slažu o istom rezultatu bez cross-device coordinacije.
     */
    private fun isPrimaryDevice(nowMs: Long): Boolean {
        if (latestOtherDeviceActiveMs == 0L) return true
        // Drugi je „recently active" ako je heartbeat unutar prozora.
        val otherRecentlyActive = (nowMs - latestOtherDeviceActiveMs) < OTHER_DEVICE_ACTIVE_WINDOW_MS
        if (!otherRecentlyActive) return true
        // Oba aktivna — deterministic tiebreaker.
        return deviceRegistry.deviceId < latestOtherDeviceId.orEmpty()
    }

    /** Cache-ovan deviceId najskorije aktivnog drugog uređaja — za tiebreaker u isPrimaryDevice. */
    @Volatile private var latestOtherDeviceId: String? = null

    private fun observeCircleCheckIns() {
        val selfUid = firebaseAuth.currentUser?.uid ?: return
        scope.launch {
            circleRepository.observeMyCircles(selfUid)
                .flatMapLatest { circles ->
                    val circleIds = circles.map { it.id }
                    if (circleIds.isEmpty()) {
                        flowOf(emptyList<org.krug.app.core.checkin.CheckInEventModel>())
                    } else {
                        combine(
                            circleIds.map { cid -> checkInRepository.observeRecentEvents(cid) },
                        ) { arrays -> arrays.toList().flatten() }
                    }
                }
                .collectLatest { events ->
                    val cutoff = System.currentTimeMillis() - CHECKIN_EVENT_MAX_AGE_MS
                    events.forEach { evt ->
                        if (evt.userId == selfUid) return@forEach
                        if (evt.id in notifiedCheckInEventIds) return@forEach
                        val ts = evt.timestamp?.time ?: 0L
                        if (ts < cutoff) {
                            notifiedCheckInEventIds.add(evt.id)
                            return@forEach
                        }
                        notifiedCheckInEventIds.add(evt.id)
                        if (isInSilentHours(currentSettings.silentHours)) return@forEach
                        checkInNotifier.notifyCheckIn(evt)
                    }
                }
        }
    }

    private fun observeCircleBattery() {
        val selfUid = firebaseAuth.currentUser?.uid ?: return
        scope.launch {
            circleRepository.observeMyCircles(selfUid)
                .flatMapLatest { circles ->
                    val others = circles.flatMap { it.memberIds }.toSet() - selfUid
                    if (others.isEmpty()) flowOf(emptyMap<String, LocationModel?>())
                    else combine(
                        others.map { uid ->
                            locationRepository.observe(uid).map { loc -> uid to loc }
                        },
                    ) { pairs -> pairs.toMap() }
                }
                .collectLatest { locByUid ->
                    if (!currentSettings.batteryAlertsEnabled) return@collectLatest
                    if (isInSilentHours(currentSettings.silentHours)) return@collectLatest
                    val now = System.currentTimeMillis()
                    var dirty = false
                    locByUid.forEach { (uid, loc) ->
                        val battery = loc?.batteryPct ?: -1
                        // Nedostupno / nepoznato / stariji client bez baterije field-a: preskoči.
                        if (battery < 0) return@forEach
                        // Hysteresis: iznad clear thresholda briši dedup entry (recovered).
                        if (battery >= PEER_BATTERY_CLEAR_THRESHOLD) {
                            if (knownBatteryAlerted.remove(uid) != null) {
                                batteryAlertNotifier.cancel(uid)
                                dirty = true
                            }
                            return@forEach
                        }
                        // Iznad alert thresholda ali ispod clear-a: no-op (grey zone).
                        if (battery >= PEER_BATTERY_ALERT_THRESHOLD) return@forEach
                        // Ispod 20% — trigger case.
                        // Charging = user zna, ne treba spam. Ne trigeruj i ne memoriši dedup.
                        if (loc?.charging == true) return@forEach
                        val last = knownBatteryAlerted[uid]
                        if (last != null && (now - last) < BATTERY_ALERT_TTL_MS) return@forEach
                        knownBatteryAlerted[uid] = now
                        dirty = true
                        scope.launch {
                            val name = fetchDisplayName(uid)
                            batteryAlertNotifier.notifyLowBattery(uid, name, battery)
                        }
                    }
                    if (dirty) localPrefs.saveBatteryAlerted(knownBatteryAlerted)
                }
        }
    }

    private suspend fun handleSosUpdate(uid: String, sos: SosModel?, myCircleIds: Set<String>) {        val now = System.currentTimeMillis()
        // Aktivni SOS: (1) postoji, (2) triggeredAt validan i unutar TTL, (3) u scope-u
        // (legacy SOS bez circleId-a prolazi kao fallback za backward compat sa starim
        // klijentima). takeIf uklanja !! ispod jer smart-cast održava non-null.
        val activeSos = sos?.takeIf {
            it.triggeredAt > 0L &&
                (now - it.triggeredAt) < SOS_TTL_MS &&
                (it.circleId == null || it.circleId in myCircleIds)
        }
        val previousTs = knownSosTriggered[uid]
        when {
            activeSos != null && previousTs != activeSos.triggeredAt -> {
                // Prvo iz payload-a (zero-latency, set u trenutku trigger-a). Legacy SOS-i
                // (pre v0.2) nemaju senderName — fallback na observeUser fetch.
                val name = activeSos.senderName?.takeIf { it.isNotBlank() } ?: fetchDisplayName(uid)
                val circleName = activeSos.circleName?.takeIf { it.isNotBlank() }
                sosNotifier.notifySos(uid, name, circleName)
                knownSosTriggered[uid] = activeSos.triggeredAt
                localPrefs.saveSosNotified(knownSosTriggered)
                Timber.d("SOS notification fired for $uid ($name) circleId=${activeSos.circleId}")
            }
            activeSos == null && previousTs != null -> {
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
                // Spam cap — filtriraj fresh kroz per-requester cooldown. Ako je isti peer
                // već trigger-ovao boost u poslednjih REFRESH_BOOST_COOLDOWN_MS, ignoriši
                // dodatne ping-ove (boost je još u toku, novi bi samo drift-ovao BURST).
                val accepted = fresh.filterKeys { reqUid ->
                    val lastTs = lastBoostByRequester[reqUid] ?: 0L
                    now - lastTs >= REFRESH_BOOST_COOLDOWN_MS
                }
                val throttled = fresh.keys - accepted.keys
                if (throttled.isNotEmpty()) {
                    Timber.d("Throttled ${throttled.size} refresh request(s) within cooldown window")
                }
                if (accepted.isNotEmpty()) {
                    Timber.i("Refresh request accepted from ${accepted.size} member(s) — pulling fresh fix + boost")
                    accepted.keys.forEach { lastBoostByRequester[it] = now }
                    // Peer explicit refresh ping — isti kao user-tap "Osveži": HIGH_ACCURACY
                    // GPS, ne stari Wi-Fi cache. Peer je verovatno prekinuo šta radi da bi
                    // dobio svežu lokaciju, nema smisla vratiti mu keš.
                    requestOneShotFix(userInitiated = true)
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
        // Trip u toku — pokušaj clean flush pre nego što proces umre. Bez ovog, trip
        // koji nije stigao do 3-min quiet timeout-a se gubi ako Android proces ubije
        // (memorija pritisak, force stop). Snapshot uid PRE flush-a jer flush pokreće
        // async write na scope-u koji možda već umire.
        firebaseAuth.currentUser?.uid?.let { uid ->
            runCatching { tripDetector.flush(uid) }
        }
        // Eksplicitno otkaži boost-expiry pre scope.cancel — bez ovog je trka: delay()
        // može da završi 1ms pre scope.cancel-a i applyProfile bi gađao polu-destroyed
        // fused klijent.
        boostExpiryJob?.cancel()
        boostExpiryJob = null
        // Crash detector unregister — SensorEventListener nije životni ciklus service-a,
        // moramo ručno da unregister-ujemo da SensorManager ne drži referencu na dead FGS.
        if (crashDetectorStarted) {
            crashDetector.stop()
            crashDetectorStarted = false
        }
        crashCountdownJob?.cancel()
        crashCountdownJob = null
        // Presence cleanup pre scope.cancel — otkači RTDB listener + eksplicitno online=false,
        // pa peer-i odmah vide "offline" umesto da čekaju RTDB server-side disconnect timeout.
        presenceCleanup?.run()
        presenceCleanup = null
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
        // setLargeIcon namerno NIJE postavljen — Samsung One UI ionako prikazuje launcher
        // app icon levo od notification-a kao "app source indicator", pa setLargeIcon je
        // pravio drugu redundantnu ikonu desno. Single icon = clean look.
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
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
        /** Spam cap — isti peer ne može da trigger-uje novi boost dok ovaj nije istekao. */
        const val REFRESH_BOOST_COOLDOWN_MS = REFRESH_BOOST_DURATION_MS
        /** Movement filter — manje od 15m kretanja preskače publish. */
        const val SIGNIFICANT_MOVEMENT_M = 15f
        /** History write threshold — više od publish (15m) zbog cost-a. */
        const val HISTORY_MIN_MOVEMENT_M = 25f
        /** Max interval bez history write-a (piše se svakako da linija nije prekinuta). */
        const val HISTORY_MAX_INTERVAL_MS = 10 * 60_000L
        /** Force publish za freshness signal čak i bez kretanja. */
        const val FORCE_PUBLISH_INTERVAL_MS = 90_000L
        /**
         * Heartbeat interval — updatedAt-only write kad publish nije bio u ovom prozoru.
         * 3 min < isOffline() prag (5 min), pa peers uvek vide fresh updatedAt dok je FGS živ.
         * Cheaper od force publish (~200B vs full location payload).
         */
        const val HEARTBEAT_INTERVAL_MS = 3 * 60_000L
        /** Maksimum prihvatljive accuracy — fixevi gori od ovog su nepouzdani. */
        const val MAX_ACCEPTABLE_ACCURACY_M = 100f
        /**
         * Age gate za cached lastLocation (fused.lastLocation). Cache može biti star
         * satima (Wi-Fi fingerprint iz drugog kraja grada), publish bi teleportovao člana.
         */
        const val LAST_LOCATION_MAX_AGE_MS = 30_000L
        /**
         * Implied brzina prag (~200 km/h). Iznad je GPS outlier ne stvarno kretanje —
         * pokriva slučaj cell-tower fallback fix-a koji "vrati unazad 200m za par sekundi".
         */
        const val MAX_PLAUSIBLE_SPEED_MPS = 55f
        /** Battery below this % i ne puni se → throttle profile na ×2 interval. */
        const val LOW_BATTERY_THRESHOLD = 15
        /** Battery alert notif se okida kad peer padne ispod ovog % (i nije na punjaču). */
        const val PEER_BATTERY_ALERT_THRESHOLD = 20
        /** Hysteresis — dedup entry za peer-a se briše kad batteryPct pređe ovo (spreči flicker oko 20). */
        const val PEER_BATTERY_CLEAR_THRESHOLD = 25
        /** Rate-limit za battery alert notif per peer — 12h. */
        const val BATTERY_ALERT_TTL_MS = 12 * 3600 * 1000L

        /**
         * Speeding event max age — event stariji od ovog se ne notif-uje (samo se markira
         * kao „viđen" u notifiedSpeedingEventIds). 30min pokriva realne kašnjenja kruga u
         * offline modu; stariji su verovatno replay pri startupu.
         */
        const val SPEEDING_EVENT_MAX_AGE_MS = 30 * 60_000L

        /**
         * Check-in event max age — check-in-i su „ovog trenutka" signali, stariji od 15min
         * verovatno replay pri startupu. Ovo je kraće od speeding jer check-in je više
         * time-sensitive („javio se ovo veče" nema smisla ujutro).
         */
        const val CHECKIN_EVENT_MAX_AGE_MS = 15 * 60_000L

        /** ETA update refresh cadence — nema koristi od češćeg recompute-a (traffic se ne menja sekundno). */
        const val ETA_UPDATE_INTERVAL_MS = 60_000L

        /** User je „stigao" kad je unutar ovog radijusa destinacije. Usklađeno sa Place min radius. */
        const val ETA_ARRIVAL_RADIUS_M = 100.0

        /**
         * Koliko dugo etaShare doc ostaje u Firestore-u posle arrivedAt marker-a pre nego
         * što ga LocationTrackingService obriše. 15min = dovoljno da observers vide notif
         * + banner na mapi, ali ne toliko da doc smeta forever.
         */
        const val ETA_ARRIVED_CLEANUP_DELAY_MS = 15 * 60_000L

        /** Crash countdown pre auto-SOS-a — 10s daje user-u vreme da otkaže false-positive. */
        const val CRASH_COUNTDOWN_SEC = 10

        /**
         * Prozor „drugi uređaj je nedavno aktivan" — ako je heartbeat drugog uređaja
         * unutar ovog vremena, tretiramo ga kao active i primary logic ide na tiebreaker.
         * 90s pokriva 60s heartbeat interval + doza margine za mrežni jitter.
         */
        const val OTHER_DEVICE_ACTIVE_WINDOW_MS = 90_000L
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
