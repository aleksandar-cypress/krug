package org.krug.app.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
import org.krug.app.core.circle.CircleRepository
import org.krug.app.core.directions.DirectionsRepository
import org.krug.app.core.location.LocationModel
import org.krug.app.core.location.LocationRepository
import org.krug.app.core.prefs.LocalPrefs
import org.krug.app.core.sos.SosModel
import org.krug.app.core.sos.SosRepository
import org.krug.app.core.user.UserRepository
import org.krug.app.core.util.DeviceNames
import timber.log.Timber

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
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MapViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val circleRepository: CircleRepository,
    private val locationRepository: LocationRepository,
    private val sosRepository: SosRepository,
    private val localPrefs: LocalPrefs,
    private val directionsRepository: DirectionsRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    /**
     * Driving distance fetch — koristi se iz MemberDetailSheet preko LaunchedEffect-a.
     * Vraća putnu distance u metrima (Mapbox Directions API), ili null ako je network fail
     * ili koordinate identične. UI fallback-uje na haversine dok je null.
     */
    suspend fun loadDrivingDistance(
        fromLat: Double, fromLng: Double,
        toLat: Double, toLng: Double,
    ): Double? = directionsRepository.drivingDistanceMeters(fromLat, fromLng, toLat, toLng)

    init {
        localPrefs.onboardingCompleted = true
    }

    private val authFlow = authRepository.observeAuthState()

    val uiState: StateFlow<MapUiState> = authFlow.flatMapLatest { user ->
        if (user == null) flowOf(MapUiState())
        else combineForUser(user.uid)
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

    fun clearSos() {
        val uid = authRepository.currentUser?.uid ?: return
        Timber.i("SOS cleared uid=%s", uid)
        viewModelScope.launch {
            runCatching { sosRepository.clear(uid) }
                .onFailure { Timber.w(it, "Failed to clear SOS") }
        }
    }

    fun refreshMember(targetUid: String) {
        val selfUid = authRepository.currentUser?.uid ?: return
        if (targetUid == selfUid) return
        viewModelScope.launch {
            runCatching { locationRepository.requestRefresh(targetUid, selfUid) }
                .onFailure { Timber.w(it, "Failed to ping refresh") }
        }
    }

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
            combine(
                combine(uids.map { memberFlow(it, selfUid) }) { it.toList() },
                childMapFlow,
            ) { arr, childMap ->
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
