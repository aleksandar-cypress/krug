package org.krug.app.core.circle

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
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
        Timber.i("Circle created id=%s name=%s ownerUid=%s", doc.id, name, ownerUid)
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

    /** Owner-only update — ime, boja, ikona postojećeg kruga. Rules enforce-uju ownership. */
    suspend fun updateCircleDetails(circleId: String, name: String, colorHex: String, iconKey: String) {
        circle(circleId).update(
            mapOf(
                "name" to name.take(20),
                "colorHex" to colorHex,
                "iconKey" to iconKey,
            ),
        ).await()
    }

    /**
     * True ako user već poseduje krug sa tim imenom (case-insensitive, trim).
     * `excludeCircleId` se koristi pri edit-u — preskoči sam taj krug u check-u.
     */
    suspend fun hasOwnedCircleNamed(
        ownerUid: String,
        name: String,
        excludeCircleId: String? = null,
    ): Boolean {
        val target = name.trim().lowercase()
        if (target.isEmpty()) return false
        val snap = runCatching {
            circles().whereEqualTo("ownerId", ownerUid).get().await()
        }.getOrNull() ?: return false
        return snap.documents.any { doc ->
            if (doc.id == excludeCircleId) return@any false
            val docName = doc.getString("name")?.trim()?.lowercase()
            docName == target
        }
    }

    /** Add user to circle.memberIds + create members subdoc. Used by InviteRepository on accept. */
    suspend fun joinCircle(circleId: String, uid: String, asChild: Boolean = false) {
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
                    "isChild" to asChild,
                    "joinedAt" to FieldValue.serverTimestamp(),
                ),
            )
        }.await()
    }

    /** Remove self from circle. Rules zabranjuju vlasniku da napusti svoj krug. */
    suspend fun leaveCircle(circleId: String, uid: String) {
        circle(circleId).update("memberIds", FieldValue.arrayRemove(uid)).await()
        runCatching { members(circleId).document(uid).delete().await() }
        Timber.i("Circle left id=%s uid=%s", circleId, uid)
    }

    /**
     * Označi člana kao dete (ili ukloni oznaku). Owner-only operation — rules
     * dozvoljavaju ovom uid-u da update-uje samo `isChild` field na members docu.
     */
    suspend fun setChildStatus(circleId: String, memberUid: String, isChild: Boolean) {
        members(circleId).document(memberUid)
            .update("isChild", isChild)
            .await()
    }

    /** Observe member docs za uid kroz sve krugove. True ako je isChild u BAR JEDNOM. */
    fun observeUserIsChildAnywhere(uid: String): Flow<Boolean> =
        observeMyCircleIds(uid).flatMapLatest { circleIds ->
            if (circleIds.isEmpty()) flowOf(false)
            else combine(circleIds.map { cid -> observeMemberIsChild(cid, uid) }) { arr ->
                arr.any { it }
            }
        }

    /** Mapa uid → isChild za sve članove ovog kruga. Live snapshot. */
    fun observeMembersChildMap(circleId: String): Flow<Map<String, Boolean>> = callbackFlow {
        val reg = members(circleId).addSnapshotListener { snap, error ->
            if (error != null) {
                Timber.w(error, "observeMembersChildMap error for $circleId")
                trySend(emptyMap())
                return@addSnapshotListener
            }
            val map = snap?.documents.orEmpty().associate { d ->
                d.id to (d.getBoolean("isChild") == true)
            }
            trySend(map)
        }
        awaitClose { reg.remove() }
    }

    private fun observeMyCircleIds(uid: String): Flow<List<String>> = callbackFlow {
        val reg = circles()
            .whereArrayContains("memberIds", uid)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Timber.w(error, "observeMyCircleIds error")
                    trySend(emptyList<String>())
                    return@addSnapshotListener
                }
                trySend(snap?.documents.orEmpty().map { it.id })
            }
        awaitClose { reg.remove() }
    }

    private fun observeMemberIsChild(circleId: String, uid: String): Flow<Boolean> = callbackFlow {
        val reg = members(circleId).document(uid).addSnapshotListener { snap, error ->
            if (error != null) {
                Timber.w(error, "observeMemberIsChild error for $circleId/$uid")
                trySend(false)
                return@addSnapshotListener
            }
            trySend(snap?.getBoolean("isChild") == true)
        }
        awaitClose { reg.remove() }
    }

    /** Obriši ceo krug. Samo vlasnik ima permission preko rules. */
    suspend fun deleteCircle(circleId: String) {
        val membersSnap = members(circleId).get().await()
        membersSnap.documents.forEach { runCatching { it.reference.delete().await() } }
        circle(circleId).delete().await()
        Timber.i("Circle deleted id=%s", circleId)
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
