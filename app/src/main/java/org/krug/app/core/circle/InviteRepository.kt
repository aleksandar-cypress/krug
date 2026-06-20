package org.krug.app.core.circle

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.tasks.await
import timber.log.Timber

sealed interface JoinResult {
    data class Success(val circleId: String, val circleName: String) : JoinResult
    enum class Failure : JoinResult { Invalid, Expired, AlreadyMember, Network }
}

@Singleton
class InviteRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val circleRepository: CircleRepository,
) {
    private fun invites() = firestore.collection("invites")

    suspend fun createInvite(
        circleId: String,
        inviterUid: String,
        prefillIsChild: Boolean = false,
    ): String {
        val expiry = Date(System.currentTimeMillis() + INVITE_TTL_MILLIS)
        repeat(MAX_GEN_ATTEMPTS) {
            val code = generate6Digit()
            try {
                firestore.runTransaction { tx ->
                    val ref = invites().document(code)
                    val snap = tx.get(ref)
                    if (snap.exists()) {
                        throw FirebaseFirestoreException(
                            "collision",
                            FirebaseFirestoreException.Code.ABORTED,
                        )
                    }
                    tx.set(
                        ref,
                        mapOf(
                            "circleId" to circleId,
                            "inviterUid" to inviterUid,
                            "maxUses" to 1,
                            "usedBy" to emptyList<String>(),
                            "prefillIsChild" to prefillIsChild,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "expiresAt" to Timestamp(expiry),
                        ),
                    )
                }.await()
                return code
            } catch (e: FirebaseFirestoreException) {
                if (e.code != FirebaseFirestoreException.Code.ABORTED) throw e
                Timber.d("Invite code collision, retrying")
            }
        }
        error("Failed to generate unique invite code after $MAX_GEN_ATTEMPTS attempts")
    }

    suspend fun acceptInvite(code: String, uid: String): JoinResult {
        val normalized = code.filter { it.isDigit() }
        if (normalized.length != CODE_LENGTH) return JoinResult.Failure.Invalid

        val snap = try {
            invites().document(normalized).get().await()
        } catch (e: Exception) {
            Timber.w(e, "Failed to read invite $normalized")
            return JoinResult.Failure.Network
        }
        if (!snap.exists()) return JoinResult.Failure.Invalid

        val invite = snap.toObject(InviteModel::class.java) ?: return JoinResult.Failure.Invalid
        val now = Date()
        if (invite.expiresAt != null && invite.expiresAt.before(now)) {
            return JoinResult.Failure.Expired
        }
        if (invite.usedBy.size >= invite.maxUses) return JoinResult.Failure.Expired

        val circle = try {
            circleRepository.getCircle(invite.circleId)
        } catch (e: Exception) {
            Timber.w(e, "Failed to read circle ${invite.circleId}")
            return JoinResult.Failure.Network
        } ?: return JoinResult.Failure.Invalid
        if (uid in circle.memberIds) return JoinResult.Failure.AlreadyMember

        return try {
            circleRepository.joinCircle(invite.circleId, uid, asChild = invite.prefillIsChild)
            invites().document(normalized).update("usedBy", FieldValue.arrayUnion(uid)).await()
            JoinResult.Success(invite.circleId, circle.name)
        } catch (e: Exception) {
            Timber.w(e, "Failed to accept invite")
            JoinResult.Failure.Network
        }
    }

    private fun generate6Digit(): String =
        Random.nextInt(0, 1_000_000).toString().padStart(CODE_LENGTH, '0')

    companion object {
        private const val CODE_LENGTH = 6
        private const val INVITE_TTL_MILLIS = 24L * 60 * 60 * 1000
        private const val MAX_GEN_ATTEMPTS = 5
    }
}
