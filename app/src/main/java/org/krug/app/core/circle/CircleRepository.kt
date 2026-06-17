package org.krug.app.core.circle

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import timber.log.Timber

@Singleton
class CircleRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    private fun circles() = firestore.collection("circles")
    private fun circle(id: String) = circles().document(id)
    private fun members(id: String) = circle(id).collection("members")

    suspend fun createCircle(
        ownerUid: String,
        name: String,
        colorHex: String,
        iconKey: String,
    ): String {
        val doc = circles().document()
        val data = mapOf(
            "name" to name,
            "colorHex" to colorHex,
            "iconKey" to iconKey,
            "ownerId" to ownerUid,
            "memberIds" to listOf(ownerUid),
            "createdAt" to FieldValue.serverTimestamp(),
        )
        doc.set(data).await()
        members(doc.id).document(ownerUid).set(
            mapOf(
                "role" to MemberModel.ROLE_OWNER,
                "shareLocation" to true,
                "joinedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        return doc.id
    }

    fun observeMyCircles(uid: String): Flow<List<CircleModel>> = callbackFlow {
        val reg = circles()
            .whereArrayContains("memberIds", uid)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Timber.w(error, "observeMyCircles error")
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snap?.documents.orEmpty().mapNotNull { d ->
                    d.toObject(CircleModel::class.java)?.copy(id = d.id)
                }
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun getCircle(id: String): CircleModel? {
        val snap = circle(id).get().await()
        return snap.toObject(CircleModel::class.java)?.copy(id = snap.id)
    }

    suspend fun isMember(circleId: String, uid: String): Boolean {
        val circle = getCircle(circleId) ?: return false
        return uid in circle.memberIds
    }

    /** Add user to circle.memberIds + create members subdoc. Used by InviteRepository on accept. */
    suspend fun joinCircle(circleId: String, uid: String) {
        firestore.runTransaction { tx ->
            val cref = circle(circleId)
            val snap = tx.get(cref)
            check(snap.exists()) { "Circle does not exist" }
            tx.update(cref, "memberIds", FieldValue.arrayUnion(uid))
            tx.set(
                members(circleId).document(uid),
                mapOf(
                    "role" to MemberModel.ROLE_MEMBER,
                    "shareLocation" to true,
                    "joinedAt" to FieldValue.serverTimestamp(),
                ),
            )
        }.await()
    }

    /** Remove self from circle. Rules zabranjuju vlasniku da napusti svoj krug. */
    suspend fun leaveCircle(circleId: String, uid: String) {
        circle(circleId).update("memberIds", FieldValue.arrayRemove(uid)).await()
        runCatching { members(circleId).document(uid).delete().await() }
    }

    /** Obriši ceo krug. Samo vlasnik ima permission preko rules. */
    suspend fun deleteCircle(circleId: String) {
        val membersSnap = members(circleId).get().await()
        membersSnap.documents.forEach { runCatching { it.reference.delete().await() } }
        circle(circleId).delete().await()
    }

    /**
     * GDPR — za brisanje naloga. Prolazimo kroz sve krugove user-a:
     *  - krugovi koje je on vlasnik → obriši ceo krug (svi članovi gube krug)
     *  - krugovi gde je samo član → ukloni se iz memberIds + members subdoc
     */
    suspend fun cleanupForDeletedUser(uid: String) {
        val mySnap = runCatching {
            circles().whereArrayContains("memberIds", uid).get().await()
        }.getOrNull() ?: return
        mySnap.documents.forEach { doc ->
            val ownerId = doc.getString("ownerId")
            runCatching {
                if (ownerId == uid) {
                    deleteCircle(doc.id)
                } else {
                    leaveCircle(doc.id, uid)
                }
            }.onFailure { Timber.w(it, "cleanupForDeletedUser failed for circle ${doc.id}") }
        }
    }
}
