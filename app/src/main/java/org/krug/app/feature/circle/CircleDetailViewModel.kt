package org.krug.app.feature.circle

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.krug.app.R
import org.krug.app.core.auth.AuthRepository
import org.krug.app.core.circle.CircleModel
import org.krug.app.core.circle.CircleRepository
import org.krug.app.core.circle.InviteRepository
import org.krug.app.core.user.UserModel
import org.krug.app.core.util.capitalizeFirstLetter
import timber.log.Timber

data class CircleDetailMember(
    val uid: String,
    val displayName: String,
    val isOwner: Boolean,
    val isSelf: Boolean,
    val isChild: Boolean,
)

data class CircleDetailUiState(
    val loading: Boolean = true,
    val circleId: String = "",
    val circleName: String = "",
    val colorHex: String = "#4F46E5",
    val iconKey: String = "family",
    val isOwner: Boolean = false,
    val members: List<CircleDetailMember> = emptyList(),
    val placesCount: Int = 0,
    val pendingInviteCode: String? = null,
    val generatingInvite: Boolean = false,
    val leaving: Boolean = false,
    val deleting: Boolean = false,
    val leftOrDeleted: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class CircleDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val circleRepository: CircleRepository,
    private val inviteRepository: InviteRepository,
    private val placeRepository: org.krug.app.core.places.PlaceRepository,
    private val firestore: FirebaseFirestore,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val circleId: String = savedState.get<String>("circleId")
        ?: throw IllegalStateException("circleId arg missing")

    private val _state = MutableStateFlow(CircleDetailUiState(circleId = circleId))
    val state: StateFlow<CircleDetailUiState> = _state.asStateFlow()

    init {
        val selfUid = authRepository.currentUser?.uid
        var hadCircle = false
        combine(
            observeCircle(circleId),
            circleRepository.observeMembersChildMap(circleId),
            authRepository.observeAuthState(),
        ) { circle, memberDocs, user ->
            Triple(circle, memberDocs, user?.uid)
        }.onEach { (circle, memberDocs, uid) ->
            if (circle == null) {
                // Krug obrisan dok je user gledao detail — auto-back.
                if (hadCircle) {
                    _state.value = _state.value.copy(loading = false, leftOrDeleted = true)
                } else {
                    _state.value = _state.value.copy(loading = false)
                }
                return@onEach
            }
            hadCircle = true
            // Load member profiles in parallel.
            viewModelScope.launch {
                val memberProfiles = circle.memberIds.map { memberUid ->
                    runCatching { fetchUser(memberUid) }.getOrNull() ?: UserModel(uid = memberUid)
                }
                _state.value = _state.value.copy(
                    loading = false,
                    circleName = circle.name,
                    colorHex = circle.colorHex,
                    iconKey = circle.iconKey,
                    isOwner = (uid != null && uid == circle.ownerId),
                    members = memberProfiles.map { profile ->
                        val name = profile.displayName
                            .ifBlank { profile.email.substringBefore('@') }
                            .ifBlank { profile.deviceModel }
                        CircleDetailMember(
                            uid = profile.uid,
                            displayName = name,
                            isOwner = profile.uid == circle.ownerId,
                            isSelf = profile.uid == uid,
                            isChild = memberDocs[profile.uid] == true,
                        )
                    },
                )
            }
        }.launchIn(viewModelScope)
        // Snapshot init so empty UID doesn't break.
        if (selfUid == null) _state.value = _state.value.copy(loading = false)

        // Observe places count za dugme "Mesta (N)".
        placeRepository.observePlaces(circleId)
            .onEach { places ->
                _state.value = _state.value.copy(placesCount = places.size)
            }
            .launchIn(viewModelScope)
    }

    fun toggleChildStatus(memberUid: String, makeChild: Boolean) {
        if (!_state.value.isOwner) return
        viewModelScope.launch {
            runCatching { circleRepository.setChildStatus(circleId, memberUid, makeChild) }
                .onFailure { Timber.w(it, "setChildStatus failed for $circleId/$memberUid") }
        }
    }

    /** Owner-only: izbaci člana iz kruga. */
    fun removeMember(memberUid: String) {
        if (!_state.value.isOwner) return
        viewModelScope.launch {
            runCatching { circleRepository.removeMember(circleId, memberUid) }
                .onFailure { Timber.w(it, "removeMember failed for $circleId/$memberUid") }
        }
    }

    /** Owner-only edit. Vraća true ako je uspešno; false ako je duplikat ili greška. */
    suspend fun updateDetails(name: String, colorHex: String, iconKey: String): Boolean {
        val s = _state.value
        if (!s.isOwner) return false
        val trimmed = name.trim().capitalizeFirstLetter()
        if (trimmed.isEmpty()) return false
        val uid = authRepository.currentUser?.uid ?: return false
        val isDuplicate = runCatching {
            circleRepository.hasOwnedCircleNamed(uid, trimmed, excludeCircleId = circleId)
        }.getOrDefault(false)
        if (isDuplicate) return false
        return runCatching {
            circleRepository.updateCircleDetails(circleId, trimmed, colorHex, iconKey)
        }.onFailure { Timber.w(it, "updateCircleDetails failed for $circleId") }.isSuccess
    }

    fun generateInvite(forChild: Boolean = false) {
        val uid = authRepository.currentUser?.uid ?: return
        if (_state.value.generatingInvite) return
        _state.value = _state.value.copy(generatingInvite = true)
        viewModelScope.launch {
            val code = runCatching {
                inviteRepository.createInvite(circleId, uid, prefillIsChild = forChild)
            }
            _state.value = _state.value.copy(
                generatingInvite = false,
                pendingInviteCode = code.getOrNull(),
                errorMessage = code.exceptionOrNull()?.let { appContext.getString(R.string.circle_detail_invite_error) },
            )
        }
    }

    fun consumeInviteCode() {
        _state.value = _state.value.copy(pendingInviteCode = null)
    }

    fun leave() {
        val uid = authRepository.currentUser?.uid ?: return
        if (_state.value.leaving) return
        _state.value = _state.value.copy(leaving = true)
        viewModelScope.launch {
            runCatching { circleRepository.leaveCircle(circleId, uid) }
                .onFailure { Timber.w(it, "Failed to leave circle") }
            _state.value = _state.value.copy(leaving = false, leftOrDeleted = true)
        }
    }

    fun delete() {
        if (_state.value.deleting) return
        _state.value = _state.value.copy(deleting = true)
        viewModelScope.launch {
            runCatching { circleRepository.deleteCircle(circleId) }
                .onFailure { Timber.w(it, "Failed to delete circle") }
            _state.value = _state.value.copy(deleting = false, leftOrDeleted = true)
        }
    }

    private fun observeCircle(cid: String): Flow<CircleModel?> = callbackFlow {
        val reg = firestore.collection("circles").document(cid)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Timber.w(error, "observeCircle error for $cid")
                    trySend(null)
                    return@addSnapshotListener
                }
                val model = snap?.toObject(CircleModel::class.java)?.copy(id = snap.id)
                trySend(model)
            }
        awaitClose { reg.remove() }
    }

    private suspend fun fetchUser(uid: String): UserModel? {
        val snap = firestore.collection("users").document(uid).get().await()
        return snap.toObject(UserModel::class.java)
    }
}
