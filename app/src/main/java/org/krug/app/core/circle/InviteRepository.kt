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

        // Sve provere + write-ovi idu u JEDNU Firestore transakciju da spreči race:
        // 1) check-then-act na maxUses (dva user-a sa istim kodom mogu da prođu ako se
        //    invite usedBy update-uje van transakcije)
        // 2) AlreadyMember check je informativan ali bez transakcije ne sprečava double-join
        //    ako se memberIds update odvija negde drugde
        // 3) joinCircle + invite usedBy update bili razdvojeni — ako app crashne između,
        //    user upisан u krug ali invite slobodan
        // Sva tri scenarija sada eliminisana — transaction retries automatski na konflikt.
        var failureReason: JoinResult.Failure? = null
        var resolvedCircleId: String? = null
        var resolvedCircleName: String = ""
        try {
            firestore.runTransaction { tx ->
                val inviteRef = invites().document(normalized)
                val inviteSnap = tx.get(inviteRef)
                if (!inviteSnap.exists()) {
                    failureReason = JoinResult.Failure.Invalid
                    return@runTransaction
                }
                val invite = inviteSnap.toObject(InviteModel::class.java) ?: run {
                    failureReason = JoinResult.Failure.Invalid
                    return@runTransaction
                }
                if (invite.expiresAt != null && invite.expiresAt.before(Date())) {
                    failureReason = JoinResult.Failure.Expired
                    return@runTransaction
                }
                if (invite.usedBy.size >= invite.maxUses) {
                    failureReason = JoinResult.Failure.Expired
                    return@runTransaction
                }
                if (uid in invite.usedBy) {
                    // Korisnik je već koristio ovaj invite. Mapiramo na AlreadyMember
                    // (semantički bliže od Invalid/Expired).
                    failureReason = JoinResult.Failure.AlreadyMember
                    return@runTransaction
                }

                val circleRef = firestore.collection("circles").document(invite.circleId)
                val circleSnap = tx.get(circleRef)
                if (!circleSnap.exists()) {
                    failureReason = JoinResult.Failure.Invalid
                    return@runTransaction
                }
                @Suppress("UNCHECKED_CAST")
                val memberIds = (circleSnap.get("memberIds") as? List<String>).orEmpty()
                if (uid in memberIds) {
                    failureReason = JoinResult.Failure.AlreadyMember
                    return@runTransaction
                }
                resolvedCircleId = invite.circleId
                resolvedCircleName = circleSnap.getString("name").orEmpty()

                // Atomic write: 3 dokumenta odjednom — circle.memberIds, member subdoc,
                // invite.usedBy. Firestore tx commit je all-or-nothing.
                tx.update(circleRef, "memberIds", FieldValue.arrayUnion(uid))
                tx.set(
                    circleRef.collection("members").document(uid),
                    mapOf(
                        "role" to MemberModel.ROLE_MEMBER,
                        "shareLocation" to true,
                        "isChild" to invite.prefillIsChild,
                        "joinedAt" to FieldValue.serverTimestamp(),
                    ),
                )
                tx.update(inviteRef, "usedBy", FieldValue.arrayUnion(uid))
            }.await()
        } catch (e: Exception) {
            Timber.w(e, "Failed to accept invite (transaction)")
            return JoinResult.Failure.Network
        }
        failureReason?.let {
            Timber.i("Invite accept rejected code=%s uid=%s reason=%s", normalized, uid, it.javaClass.simpleName)
            return it
        }
        val cid = resolvedCircleId ?: return JoinResult.Failure.Network
        Timber.i("Invite accepted code=%s uid=%s circleId=%s", normalized, uid, cid)
        return JoinResult.Success(cid, resolvedCircleName)
    }

    private fun generate6Digit(): String =
        Random.nextInt(0, 1_000_000).toString().padStart(CODE_LENGTH, '0')

    companion object {
        private const val CODE_LENGTH = 6
        private const val INVITE_TTL_MILLIS = 24L * 60 * 60 * 1000
        private const val MAX_GEN_ATTEMPTS = 5
    }
}
