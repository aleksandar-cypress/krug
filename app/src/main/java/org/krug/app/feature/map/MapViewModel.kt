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
import timber.log.Timber

data class MemberWithLocation(
    val uid: String,
    val displayName: String,
    val deviceModel: String,
    val photoUrl: String?,
    val location: LocationModel?,
    val sos: SosModel?,
    val isSelf: Boolean,
)

data class CircleBrief(val id: String, val name: String)

data class MapUiState(
    val members: List<MemberWithLocation> = emptyList(),
    val selfLocation: LocationModel? = null,
    val selfUid: String? = null,
    val selfSosActive: Boolean = false,
    val myCircles: List<CircleBrief> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MapViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val circleRepository: CircleRepository,
    private val locationRepository: LocationRepository,
    private val firestore: FirebaseFirestore,
    private val sosRepository: SosRepository,
    localPrefs: LocalPrefs,
) : ViewModel() {

    init {
        localPrefs.onboardingCompleted = true
    }

    private val authFlow = authRepository.observeAuthState()

    val uiState: StateFlow<MapUiState> = authFlow.flatMapLatest { user ->
        if (user == null) flowOf(MapUiState())
        else combineForUser(user.uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    fun triggerSos() {
        val uid = authRepository.currentUser?.uid ?: return
        val loc = uiState.value.selfLocation
        viewModelScope.launch {
            runCatching {
                sosRepository.trigger(
                    uid = uid,
                    lat = loc?.lat ?: 0.0,
                    lng = loc?.lng ?: 0.0,
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
        return circlesFlow.flatMapLatest { circles ->
            val briefs = circles.map { CircleBrief(it.id, it.name) }
            val uids = circles.flatMap { it.memberIds }.toMutableSet().apply { add(selfUid) }.toList()
            if (uids.isEmpty()) flowOf(MapUiState(selfUid = selfUid, myCircles = briefs))
            else combine(uids.map { memberFlow(it, selfUid) }) { arr ->
                val members = arr.toList()
                val self = members.firstOrNull { it.isSelf }
                MapUiState(
                    members = members,
                    selfLocation = self?.location,
                    selfUid = selfUid,
                    selfSosActive = self?.sos != null,
                    myCircles = briefs,
                )
            }
        }
    }

    private fun memberFlow(uid: String, selfUid: String): Flow<MemberWithLocation> =
        combine(
            observeUser(uid),
            locationRepository.observe(uid),
            sosRepository.observe(uid),
        ) { user, loc, sos ->
            val nameFromUser = user?.displayName.orEmpty()
            val emailPrefix = user?.email.orEmpty().substringBefore('@')
            val device = user?.deviceModel.orEmpty()
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
