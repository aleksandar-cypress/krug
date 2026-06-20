package org.krug.app.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.krug.app.core.auth.AuthRepository
import org.krug.app.core.circle.CircleRepository
import org.krug.app.core.location.LocationModel
import org.krug.app.core.location.LocationRepository
import org.krug.app.core.prefs.LocalPrefs
import org.krug.app.core.sos.SosModel
import org.krug.app.core.sos.SosRepository
import org.krug.app.core.user.UserModel
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
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MapViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val circleRepository: CircleRepository,
    private val locationRepository: LocationRepository,
    private val firestore: FirebaseFirestore,
    private val sosRepository: SosRepository,
    private val localPrefs: LocalPrefs,
) : ViewModel() {

    init {
        localPrefs.onboardingCompleted = true
    }

    private val authFlow = authRepository.observeAuthState()

    val uiState: StateFlow<MapUiState> = authFlow.flatMapLatest { user ->
        if (user == null) flowOf(MapUiState())
        else combineForUser(user.uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    fun setActiveCircle(id: String) {
        localPrefs.setActiveCircleId(id)
    }

    fun triggerSos() {
        val uid = authRepository.currentUser?.uid ?: return
        val loc = uiState.value.selfLocation
        val circleId = uiState.value.activeCircleId
        viewModelScope.launch {
            runCatching {
                sosRepository.trigger(
                    uid = uid,
                    lat = loc?.lat ?: 0.0,
                    lng = loc?.lng ?: 0.0,
                    circleId = circleId,
                )
            }.onFailure { Timber.w(it, "Failed to trigger SOS") }
        }
    }

    fun clearSos() {
        val uid = authRepository.currentUser?.uid ?: return
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
        return combine(circlesFlow, localPrefs.activeCircleIdFlow) { circles, stored ->
            circles to stored
        }.flatMapLatest { (circles, storedActive) ->
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
            observeUser(uid),
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

    private fun observeUser(uid: String): Flow<UserModel?> = callbackFlow {
        val reg = firestore.collection("users").document(uid)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Timber.w(error, "observeUser error for $uid")
                    trySend(null)
                    return@addSnapshotListener
                }
                trySend(snap?.toObject(UserModel::class.java))
            }
        awaitClose { reg.remove() }
    }
}
