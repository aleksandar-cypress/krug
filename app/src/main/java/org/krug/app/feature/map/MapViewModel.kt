package org.krug.app.feature.map

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import org.krug.app.R
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.krug.app.core.auth.AuthRepository
import org.krug.app.core.checkin.CheckInRepository
import org.krug.app.core.circle.CircleRepository
import org.krug.app.core.directions.DirectionsRepository
import org.krug.app.core.directions.GeocodingRepository
import org.krug.app.core.eta.EtaRepository
import org.krug.app.core.location.LocationModel
import org.krug.app.core.location.LocationRepository
import org.krug.app.core.map.MapStyleOption
import org.krug.app.core.places.PlaceEventModel
import org.krug.app.core.places.PlaceModel
import org.krug.app.core.places.PlaceRepository
import org.krug.app.core.prefs.LocalPrefs
import org.krug.app.core.sos.SosModel
import org.krug.app.core.sos.SosRepository
import org.krug.app.core.user.UserRepository
import org.krug.app.core.util.DeviceNames
import org.krug.app.core.util.NetworkMonitor
import org.krug.app.core.util.PowerSaveMonitor
import timber.log.Timber

/**
 * One-shot UI signali iz MapViewModel-a. Compose collector-i ih hvataju kroz LaunchedEffect
 * i pretvaraju u Toast/Snackbar. Ako screen nije aktivan, event se dropuje (SharedFlow
 * replay=0) — check-in feedback nije relevantan ako je user već izašao iz mape.
 */
sealed class MapEvent {
    object CheckInSent : MapEvent()
    object CheckInFailed : MapEvent()
}

data class MemberWithLocation(
    val uid: String,
    val displayName: String,
    val deviceModel: String,
    val photoUrl: String?,
    val location: LocationModel?,
    val sos: SosModel?,
    val isSelf: Boolean,
    val isChild: Boolean = false,
)

data class CircleBrief(val id: String, val name: String, val colorHex: String, val iconKey: String)

data class MapUiState(
    val members: List<MemberWithLocation> = emptyList(),
    val selfLocation: LocationModel? = null,
    val selfUid: String? = null,
    val selfSosActive: Boolean = false,
    val myCircles: List<CircleBrief> = emptyList(),
    val activeCircleId: String? = null,
    /** True nakon prvog Firestore snapshot-a — sprečava flicker empty-state CTA dok se ne učita. */
    val circlesLoaded: Boolean = false,
    /**
     * True ako je Firestore observe pao (network down, permission denied, App Check fail).
     * UI razlikuje "user nema krugove" (empty + !circlesError) od "Firestore down"
     * (empty + circlesError) — drugi case zaslužuje retry banner umesto onboarding CTA.
     */
    val circlesError: Boolean = false,
    /**
     * Reactive network state preko ConnectivityManager. False → offline banner sa
     * "poslednje ažuriranje pre X min" na osnovu selfLocation.updatedAt.
     */
    val isOnline: Boolean = true,
    /**
     * True kad je sistemski Battery Saver mod aktivan. UI banner upozorava da
     * lokacija ide ređim intervalom dok je Saver on.
     */
    val isPowerSaveMode: Boolean = false,
    /**
     * Sopstveni aktivan ETA share (prva ne-null vrednost iz svih krugova). Null = nema
     * aktivnog share-a. UI koristi za banner „Deliš ETA: 15 min do Y".
     */
    val myEtaShare: org.krug.app.core.eta.EtaShareModel? = null,
    /**
     * Aktivni ETA share-ovi ostalih članova aktivnog kruga (self isključen). UI ih render-uje
     * kao destination pin-ove na mapi — svaki član kruga vidi gde ostali idu.
     */
    val otherEtaShares: List<org.krug.app.core.eta.EtaShareModel> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MapViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val circleRepository: CircleRepository,
    private val locationRepository: LocationRepository,
    private val sosRepository: SosRepository,
    private val localPrefs: LocalPrefs,
    private val userRepository: UserRepository,
    private val networkMonitor: NetworkMonitor,
    private val powerSaveMonitor: PowerSaveMonitor,
    private val placeRepository: PlaceRepository,
    private val checkInRepository: CheckInRepository,
    private val geocodingRepository: GeocodingRepository,
    private val directionsRepository: DirectionsRepository,
    private val etaRepository: EtaRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    /**
     * One-shot UI event bus. `emit` iz VM-a, `collect` u Composable-u kroz LaunchedEffect
     * (SharedFlow drop-uje event kad nema active collector-a — savršeno za toast poruke
     * koje su relevantne samo ako je user na screen-u u trenutku emit-a).
     */
    private val _events = kotlinx.coroutines.flow.MutableSharedFlow<MapEvent>(
        replay = 0,
        extraBufferCapacity = 4,
    )
    val events: kotlinx.coroutines.flow.SharedFlow<MapEvent> = _events

    /**
     * Efektivan aktivan circleId sa fallback logikom — MORA da bude konzistentno sa
     * `combineForUser` (linija 275): ako stored activeCircleId ne postoji među user-ovim
     * krugovima ili je null, pada na prvi krug. Ranije je `activePlaces` observirao
     * direktno `localPrefs.activeCircleIdFlow` bez fallback-a → za fresh user-e koji
     * nikad nisu eksplicitno pozvali setActiveCircle, `activeCircleId` je null pa je
     * `activePlaces` = empty čak i kad ima krug sa mestima → mape prikazuje members
     * (koji imaju fallback) ali ne i places.
     */
    private val effectiveActiveCircleId: Flow<String?> = authRepository.observeAuthState()
        .flatMapLatest { user ->
            if (user == null) flowOf(null)
            else circleRepository.observeMyCircles(user.uid)
                .combine(localPrefs.activeCircleIdFlow) { circles, stored ->
                    (circles.firstOrNull { it.id == stored } ?: circles.firstOrNull())?.id
                }
        }

    /** Trenutno izabran stil mape (Settings → Map style). Observed od MapScreen-a za dinamički style change. */
    val mapStyle: StateFlow<MapStyleOption> = localPrefs.mapStyleKeyFlow
        .map { MapStyleOption.fromKey(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapStyleOption.DEFAULT)

    /** Places za trenutno aktivan krug — MapScreen ih rendera kao pinove. */
    val activePlaces: StateFlow<List<PlaceModel>> = effectiveActiveCircleId.flatMapLatest { activeId ->
        if (activeId.isNullOrBlank()) flowOf(emptyList())
        else placeRepository.observePlaces(activeId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Poslednji event po placeId za aktivan krug — koristi se u PlaceDetailSheet. */
    val eventsByPlace: StateFlow<Map<String, PlaceEventModel>> = run {
        effectiveActiveCircleId.flatMapLatest { activeId ->
            if (activeId.isNullOrBlank()) flowOf(emptyList())
            else placeRepository.observeRecentEvents(activeId, limit = 100)
        }.map { events ->
            // Zadrži samo najnoviji event po placeId (lista je već DESC po timestamp-u).
            events.groupBy { it.placeId }.mapValues { (_, evts) -> evts.first() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    }

    fun deletePlace(placeId: String) {
        viewModelScope.launch {
            // Koristi effectiveActiveCircleId (sa fallback-om) umesto raw prefs — za
            // fresh usere koji nikad nisu setActiveCircle pozvali, raw je null pa je
            // delete silently no-op. Fallback resolves prvi krug user-a.
            val activeId = effectiveActiveCircleId.first() ?: return@launch
            runCatching { placeRepository.deletePlace(activeId, placeId) }
                .onFailure { Timber.w(it, "deletePlace from MapViewModel failed") }
        }
    }

    fun togglePlaceMute(placeId: String, muted: Boolean) {
        viewModelScope.launch {
            val activeId = effectiveActiveCircleId.first() ?: return@launch
            runCatching { placeRepository.setMuted(activeId, placeId, muted) }
                .onFailure { Timber.w(it, "togglePlaceMute failed") }
        }
    }

    fun lastSeenWhatsNewVersion(): Int = localPrefs.lastSeenWhatsNewVersion
    fun markWhatsNewSeen(version: Int) {
        localPrefs.lastSeenWhatsNewVersion = version
    }

    /** True kad je prošlo > 7 dana od poslednjeg re-prompt-a (ili prvi put). */
    fun batteryPromptCooldownExpired(): Boolean {
        val last = localPrefs.lastBatteryPromptMs
        if (last == 0L) return true
        val week = 7L * 24 * 60 * 60 * 1000
        return System.currentTimeMillis() - last > week
    }

    fun markBatteryPromptShown() {
        localPrefs.lastBatteryPromptMs = System.currentTimeMillis()
    }

    init {
        localPrefs.onboardingCompleted = true
    }

    private val authFlow = authRepository.observeAuthState()

    val uiState: StateFlow<MapUiState> = authFlow.flatMapLatest { user ->
        val base = if (user == null) flowOf(MapUiState()) else combineForUser(user.uid)
        combine(
            base,
            networkMonitor.isOnline,
            powerSaveMonitor.isOnSaver,
        ) { state, online, saver -> state.copy(isOnline = online, isPowerSaveMode = saver) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    fun setActiveCircle(id: String) {
        Timber.i("Active circle changed to %s", id)
        localPrefs.setActiveCircleId(id)
    }

    fun triggerSos() {
        val uid = authRepository.currentUser?.uid ?: return
        val snapshot = uiState.value
        val loc = snapshot.selfLocation
        val circleId = snapshot.activeCircleId
        val circleName = snapshot.myCircles.firstOrNull { it.id == circleId }?.name
        viewModelScope.launch {
            // Ime se rešava sinhronizno — UI selfMember može biti null ako user pritisne
            // SOS dok se members lista još učitava (auth → Firestore round-trip). Bulletproof:
            // 1) UI snapshot (instant), 2) FirebaseAuth profile, 3) UserRepository.observeUser
            // (3s timeout), 4) DeviceNames.friendly fallback nikad ne bi trebao pasti.
            val senderName = resolveSenderName(uid, snapshot)
            Timber.i(
                "SOS triggered uid=%s circleId=%s sender=%s",
                uid, circleId ?: "(none)", senderName ?: "(unknown)",
            )
            runCatching {
                sosRepository.trigger(
                    uid = uid,
                    lat = loc?.lat ?: 0.0,
                    lng = loc?.lng ?: 0.0,
                    circleId = circleId,
                    senderName = senderName,
                    circleName = circleName,
                )
            }.onFailure { Timber.w(it, "Failed to trigger SOS") }
        }
    }

    private suspend fun resolveSenderName(uid: String, snapshot: MapUiState): String? {
        snapshot.members.firstOrNull { it.isSelf }?.displayName
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        authRepository.currentUser?.displayName
            ?.takeIf { !it.isNullOrBlank() }
            ?.let { return it }
        // Firestore fetch sa 3s timeout-om — UserRepository je već observable, povučemo
        // prvi non-null snapshot. DeviceNames.friendly fallback garantuje da retko vraćamo
        // null (samo ako fetch propadne / device label prazan).
        val fromDoc = withTimeoutOrNull(3_000L) {
            userRepository.observeUser(uid).filterNotNull().first()
        } ?: return null
        return fromDoc.displayName.takeIf { it.isNotBlank() }
            ?: fromDoc.email.substringBefore('@').takeIf { it.isNotBlank() }
            ?: DeviceNames.friendly(fromDoc.deviceModel).takeIf { it.isNotBlank() }
    }

    /**
     * Safe check-in — pošalje „Stigao/la sam" u sve krugove usera. Reverse-geocode
     * radi opcionalno sa 4s timeout-om — ako faila, event ide bez `placeLabel` i
     * observers vide generički „Bezbedno" tekst. Emit-uje MapEvent.CheckInSent /
     * CheckInFailed za toast prikaz.
     */
    fun sendCheckIn() {
        val uid = authRepository.currentUser?.uid ?: return
        val snapshot = uiState.value
        val loc = snapshot.selfLocation
        if (loc == null) {
            viewModelScope.launch { _events.emit(MapEvent.CheckInFailed) }
            return
        }
        val circleIds = snapshot.myCircles.map { it.id }
        if (circleIds.isEmpty()) {
            viewModelScope.launch { _events.emit(MapEvent.CheckInFailed) }
            return
        }
        viewModelScope.launch {
            val senderName = resolveSenderName(uid, snapshot).orEmpty()
            val label = withTimeoutOrNull(4_000L) {
                geocodingRepository.reverse(loc.lat, loc.lng)
            }.orEmpty()
            val result = runCatching {
                checkInRepository.logCheckIn(
                    circleIds = circleIds,
                    userId = uid,
                    userName = senderName,
                    lat = loc.lat,
                    lng = loc.lng,
                    placeLabel = label,
                )
            }
            if (result.isSuccess) {
                _events.emit(MapEvent.CheckInSent)
            } else {
                Timber.w(result.exceptionOrNull(), "Check-in send failed")
                _events.emit(MapEvent.CheckInFailed)
            }
        }
    }

    /**
     * Pokreće ETA share prema odabranoj destinaciji. Radi initial directions fetch (za
     * seed etaMinutes/remainingKm) — ako faila, koristi haversine estimate. Live update
     * dalje ide iz LocationTrackingService-a.
     */
    fun startEtaShare(destLat: Double, destLng: Double, destLabel: String) {
        val uid = authRepository.currentUser?.uid ?: return
        val snapshot = uiState.value
        val loc = snapshot.selfLocation ?: return
        val circleIds = snapshot.myCircles.map { it.id }
        if (circleIds.isEmpty()) return
        viewModelScope.launch {
            val senderName = resolveSenderName(uid, snapshot).orEmpty()
            val route = withTimeoutOrNull(6_000L) {
                directionsRepository.driveEta(loc.lat, loc.lng, destLat, destLng)
            }
            val (etaMin, remKm) = if (route != null) {
                (route.durationSec / 60.0).toInt().coerceAtLeast(1) to (route.distanceMeters / 1000.0)
            } else {
                val km = haversineKmSimple(loc.lat, loc.lng, destLat, destLng)
                ((km / 40.0) * 60.0).toInt().coerceAtLeast(1) to km
            }
            runCatching {
                etaRepository.startShare(
                    circleIds = circleIds,
                    userId = uid,
                    userName = senderName,
                    destinationLat = destLat, destinationLng = destLng,
                    destinationLabel = destLabel,
                    currentLat = loc.lat, currentLng = loc.lng,
                    initialEtaMinutes = etaMin,
                    initialRemainingKm = remKm,
                )
            }.onFailure { Timber.w(it, "startEtaShare failed") }
        }
    }

    fun cancelEtaShare() {
        val uid = authRepository.currentUser?.uid ?: return
        val circleIds = uiState.value.myCircles.map { it.id }
        if (circleIds.isEmpty()) return
        viewModelScope.launch {
            runCatching { etaRepository.cancelShare(circleIds, uid) }
                .onFailure { Timber.w(it, "cancelEtaShare failed") }
        }
    }

    /**
     * Search destinacije za ETA share dialog. Wrapper preko GeocodingRepository koji
     * dodaje self proximity boost (Beograd rezultati iznad global-a). Vraća listu za
     * autocomplete UI.
     */
    suspend fun searchDestinations(query: String): List<GeocodingRepository.Suggestion> {
        val self = uiState.value.selfLocation
        val result = geocodingRepository.search(
            query = query,
            proximityLat = self?.lat,
            proximityLng = self?.lng,
        )
        return when (result) {
            is GeocodingRepository.SearchResult.Success -> result.suggestions
            else -> emptyList()
        }
    }

    private fun haversineKmSimple(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val out = FloatArray(1)
        android.location.Location.distanceBetween(lat1, lng1, lat2, lng2, out)
        return out[0].toDouble() / 1000.0
    }

    /**
     * Fetch real road distance via Mapbox Directions API. Vraća km ili null ako API pukne
     * (network, timeout, no route). MemberDetailSheet zove ovo pri otvaranju sheet-a — user
     * je aktivno pregleda jednog člana i road distance je vredna informacija (razlika od
     * vazdušne linije može biti 30-50% u urbanoj mreži zbog reka, autoputa, žica).
     *
     * Ne cache-ujemo — svaki poziv je fresh Directions request. Route se menja kroz saobraćaj
     * i vremenske uslove; cached vrednost bi mogla biti stara. Cost analiza: user otvara
     * 5-10 member sheet-ova dnevno → daleko od Mapbox besplatne kvote.
     */
    suspend fun roadDistanceKm(
        fromLat: Double, fromLng: Double,
        toLat: Double, toLng: Double,
    ): Double? {
        val route = withTimeoutOrNull(4_000L) {
            directionsRepository.driveEta(fromLat, fromLng, toLat, toLng)
        } ?: return null
        return route.distanceMeters / 1000.0
    }

    fun clearSos() {
        val uid = authRepository.currentUser?.uid ?: return
        Timber.i("SOS cleared uid=%s", uid)
        viewModelScope.launch {
            runCatching { sosRepository.clear(uid) }
                .onFailure { Timber.w(it, "Failed to clear SOS") }
        }
    }

    /**
     * Activity Recognition pre-prompt — pokazujemo brand dijalog sa rationale-om pre nego
     * što system permission dialog iskoči. Bez ovog, user vidi golu sistemsku poruku
     * "Allow Krug to access physical activity?" bez konteksta zašto je tražimo. Flag se
     * trajno set-uje čim user reaguje (allow ili dismiss) — ne nudi se ponovo.
     */
    fun shouldShowActivityRecPrompt(): Boolean = !localPrefs.activityRecPromptShown

    fun markActivityRecPromptShown() {
        localPrefs.activityRecPromptShown = true
    }

    fun refreshMember(targetUid: String) {
        val selfUid = authRepository.currentUser?.uid ?: return
        if (targetUid == selfUid) return
        viewModelScope.launch {
            runCatching { locationRepository.requestRefresh(targetUid, selfUid) }
                .onFailure { Timber.w(it, "Failed to ping refresh") }
        }
    }

    /**
     * Auto-refresh svih članova čije je poslednje ažuriranje starije od [staleThresholdMs].
     * Poziva se pri otvaranju MapScreen-a i pri ON_RESUME. Za svakog stale člana šalje
     * RTDB refresh ping; ako je njihov FGS živ (Doze/screen off ali proces živi), oni će
     * u kratkom roku publish-ovati svežu lokaciju. Ako je FGS mrtav, ping ostaje bez
     * odgovora — ne šteti, samo pojede par sekundi.
     *
     * Ne pinguje self niti long-offline članove (24h+) jer je verovatnoća uspeha nula.
     * Rate-limit: `lastAutoRefreshMs` per uid — ne pinguj isti uid češće od 60s (tim
     * FGS-om je već poslao BURST boost).
     */
    private val lastAutoRefreshMs = mutableMapOf<String, Long>()
    fun refreshStaleMembers(staleThresholdMs: Long = 3L * 60L * 1000L) {
        val selfUid = authRepository.currentUser?.uid ?: return
        val now = System.currentTimeMillis()
        val members = uiState.value.members
        members.forEach { m ->
            if (m.uid == selfUid) return@forEach
            val loc = m.location ?: return@forEach
            val age = now - loc.updatedAt
            // Long-offline 24h+ preskoči — FGS je verovatno mrtav.
            if (age > 24L * 60L * 60L * 1000L) return@forEach
            // Fresh — ne treba refresh.
            if (age < staleThresholdMs) return@forEach
            // Rate-limit.
            val last = lastAutoRefreshMs[m.uid] ?: 0L
            if (now - last < 60_000L) return@forEach
            lastAutoRefreshMs[m.uid] = now
            viewModelScope.launch {
                runCatching { locationRepository.requestRefresh(m.uid, selfUid) }
                    .onFailure { Timber.d("auto-refresh ping failed for %s: %s", m.uid, it.message) }
            }
        }
    }

    /**
     * Auto-status po uid-u: "Kuća" / "U pokretu • 45 km/h" / null.
     * Priority: place presence > motion > null.
     */
    val autoStatusByUid: StateFlow<Map<String, String>> = uiState
        .combine(activePlaces) { state, places ->
            state.members.mapNotNull { m ->
                val loc = m.location ?: return@mapNotNull null
                val now = System.currentTimeMillis()
                if (now - loc.updatedAt > 15 * 60_000L) return@mapNotNull null
                val place = places.firstOrNull { p ->
                    val d = FloatArray(1)
                    android.location.Location.distanceBetween(loc.lat, loc.lng, p.lat, p.lng, d)
                    d[0] <= p.radius
                }
                if (place != null) return@mapNotNull m.uid to place.name
                if (loc.speed > 3f) {
                    val kmh = (loc.speed * 3.6f).toInt()
                    return@mapNotNull m.uid to appContext.getString(R.string.auto_status_moving, kmh)
                }
                null
            }.toMap()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private fun combineForUser(selfUid: String): Flow<MapUiState> {
        val circlesFlow = circleRepository.observeMyCircles(selfUid)
        return combine(
            circlesFlow,
            localPrefs.activeCircleIdFlow,
            circleRepository.lastSnapshotError,
        ) { circles, stored, error ->
            Triple(circles, stored, error)
        }.flatMapLatest { (circles, storedActive, error) ->
            val briefs = circles.map { CircleBrief(it.id, it.name, it.colorHex, it.iconKey) }
            // Aktivni krug = ono što je user izabrao (ako i dalje postoji), inače prvi.
            val active = circles.firstOrNull { it.id == storedActive } ?: circles.firstOrNull()
            // Mapa pokazuje samo članove aktivnog kruga (+ self).
            val uids = if (active == null) setOf(selfUid).toList()
            else (active.memberIds.toSet() + selfUid).toList()
            val childMapFlow = if (active == null) flowOf(emptyMap())
            else circleRepository.observeMembersChildMap(active.id)
            val myEtaFlow = if (active == null) flowOf(null as org.krug.app.core.eta.EtaShareModel?)
            else etaRepository.observeMyShare(active.id, selfUid)
            val allEtaFlow = if (active == null) flowOf(emptyList<org.krug.app.core.eta.EtaShareModel>())
            else etaRepository.observeActiveShares(active.id)
            combine(
                combine(uids.map { memberFlow(it, selfUid) }) { it.toList() },
                childMapFlow,
                myEtaFlow,
                allEtaFlow,
            ) { arr, childMap, myEta, allEta ->
                val now = System.currentTimeMillis()
                val activeId = active?.id
                // Defensive UI filter — SOS stariji od TTL ili koji nije za aktivni krug
                // se tretira kao neaktivan na ovoj mapi. (Legacy SOS bez circleId-a prolaze.)
                val members = arr.map { m ->
                    val sos = m.sos
                    val keep = sos != null &&
                        now - sos.triggeredAt < SOS_TTL_MS &&
                        (sos.circleId == null || sos.circleId == activeId)
                    val withSos = if (keep) m else m.copy(sos = null)
                    withSos.copy(isChild = childMap[m.uid] == true)
                }
                val self = members.firstOrNull { it.isSelf }
                // Auto-clear: ako je self SOS prešao TTL, obriši u RTDB.
                if (self?.sos == null) {
                    val rawSelf = arr.firstOrNull { it.isSelf }
                    if (rawSelf?.sos != null && now - rawSelf.sos.triggeredAt >= SOS_TTL_MS) {
                        viewModelScope.launch {
                            runCatching { sosRepository.clear(selfUid) }
                                .onFailure { Timber.w(it, "Failed to auto-clear stale self SOS") }
                        }
                    }
                }
                MapUiState(
                    members = members,
                    selfLocation = self?.location,
                    selfUid = selfUid,
                    selfSosActive = self?.sos != null,
                    myCircles = briefs,
                    activeCircleId = active?.id,
                    circlesLoaded = true,
                    circlesError = error != null && circles.isEmpty(),
                    myEtaShare = myEta,
                    otherEtaShares = allEta.filter { it.userId != selfUid },
                )
            }
        }
    }

    companion object {
        /** SOS posle ovog vremena se automatski tretira kao neaktivan i čisti. */
        const val SOS_TTL_MS = 30 * 60_000L
    }

    private fun memberFlow(uid: String, selfUid: String): Flow<MemberWithLocation> =
        combine(
            // UserRepository.observeUser je single source of truth — ranije smo imali
            // lokalni observeUser callbackFlow koji je pravio paralelni Firebase listener
            // (duplikat traffic + duplikat state) na isti users/{uid} doc.
            userRepository.observeUser(uid),
            locationRepository.observe(uid),
            sosRepository.observe(uid),
        ) { user, loc, sos ->
            // Postojeći user-i imaju raw device kod u displayName (anonimni sign-in pre
            // friendly mapping-a). Transformišemo i tu da bi sve bilo konzistentno.
            val nameFromUser = DeviceNames.friendly(user?.displayName.orEmpty())
            val emailPrefix = user?.email.orEmpty().substringBefore('@')
            val rawDevice = user?.deviceModel.orEmpty()
            val device = DeviceNames.friendly(rawDevice)
            MemberWithLocation(
                uid = uid,
                displayName = nameFromUser.ifBlank { emailPrefix.ifBlank { device } },
                deviceModel = device,
                photoUrl = user?.photoUrl,
                location = loc,
                sos = sos,
                isSelf = uid == selfUid,
            )
        }

}
