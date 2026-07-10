package org.krug.app.core.circle

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    /**
     * Posljednja greška iz observeMyCircles snapshot listener-a. ViewModels combine-uju
     * sa observeMyCircles flow-om da razlikuju "user nema krugove" (empty list, no error)
     * od "Firestore down" (empty list + error != null) — UI tada može da prikaže retry
     * banner umesto "Napravi prvi krug" CTA.
     */
    private val _lastSnapshotError = MutableStateFlow<Throwable?>(null)
    val lastSnapshotError: StateFlow<Throwable?> = _lastSnapshotError.asStateFlow()

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
                    _lastSnapshotError.value = error
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                _lastSnapshotError.value = null
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
        Timber.i("Circle details updated id=%s", circleId)
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
        Timber.i("Circle joined id=%s uid=%s asChild=%s", circleId, uid, asChild)
    }

    /** Remove self from circle. Rules zabranjuju vlasniku da napusti svoj krug. */
    suspend fun leaveCircle(circleId: String, uid: String) {
        circle(circleId).update("memberIds", FieldValue.arrayRemove(uid)).await()
        runCatching { members(circleId).document(uid).delete().await() }
        Timber.i("Circle left id=%s uid=%s", circleId, uid)
    }

    /**
     * Owner izbacuje člana iz kruga. Firebase rules dozvoljavaju samo vlasniku ove
     * operacije (`ownerId == request.auth.uid` na circle doc update + members doc delete).
     * Owner ne može da izbaci samog sebe — rules bi to i sprečile (owner mora prvo da
     * obriše krug ili prebaci ownership).
     *
     * Sekvenca: prvo update memberIds (arrayRemove), pa delete members subdoc. Ako
     * prvi succeed ali drugi failuje, member je van kruga ali doc ostaje "ghost" —
     * next time member ne može učitati svoj doc jer nije više u memberIds pa rules
     * zabranjuju read. Acceptable state.
     */
    suspend fun removeMember(circleId: String, memberUid: String) {
        circle(circleId).update("memberIds", FieldValue.arrayRemove(memberUid)).await()
        runCatching { members(circleId).document(memberUid).delete().await() }
        Timber.i("Member removed from circle id=%s uid=%s", circleId, memberUid)
    }

    /**
     * Prebaci vlasništvo kruga na drugog člana. Trenutni owner mora da bude signed-in
     * user (rules enforce-uju). newOwnerUid mora da bude u `memberIds` (verifikacija u
     * transakciji — ako se u međuvremenu izbačen, transferOwnership fail-uje).
     *
     * Transakcija:
     *  1. Update circle.ownerId = newOwnerUid
     *  2. Update members/{newOwnerUid}.role = "owner"
     *  3. Update members/{currentOwnerUid}.role = "member"
     *
     * Bez transakcije, race scenario: owner A prebacuje na B, istovremeno neko drugi
     * izbaci B iz kruga → circle.ownerId = B ali B nije više član = zombie state.
     */
    suspend fun transferOwnership(circleId: String, currentOwnerUid: String, newOwnerUid: String) {
        firestore.runTransaction { tx ->
            val cref = circle(circleId)
            val snap = tx.get(cref)
            check(snap.exists()) { "Circle does not exist" }
            @Suppress("UNCHECKED_CAST")
            val memberIds = (snap.get("memberIds") as? List<String>).orEmpty()
            check(newOwnerUid in memberIds) { "New owner is not a member" }
            check(snap.getString("ownerId") == currentOwnerUid) { "Not current owner" }
            tx.update(cref, "ownerId", newOwnerUid)
            tx.update(members(circleId).document(newOwnerUid), "role", MemberModel.ROLE_OWNER)
            tx.update(members(circleId).document(currentOwnerUid), "role", MemberModel.ROLE_MEMBER)
        }.await()
        Timber.i("Ownership transferred circle=%s from=%s to=%s", circleId, currentOwnerUid, newOwnerUid)
    }

    /**
     * Označi člana kao dete (ili ukloni oznaku). Owner-only operation — rules
     * dozvoljavaju ovom uid-u da update-uje samo `isChild` field na members docu.
     */
    suspend fun setChildStatus(circleId: String, memberUid: String, isChild: Boolean) {
        members(circleId).document(memberUid)
            .update("isChild", isChild)
            .await()
        Timber.i("Child status set id=%s uid=%s isChild=%s", circleId, memberUid, isChild)
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
    /** Vraća memberIds za specifičan krug, live. Prazna lista ako krug ne postoji. */
    fun observeMembersUids(circleId: String): Flow<List<String>> = callbackFlow {
        val reg = circle(circleId).addSnapshotListener { snap, error ->
            if (error != null) {
                Timber.w(error, "observeMembersUids error for $circleId")
                trySend(emptyList())
                return@addSnapshotListener
            }
            @Suppress("UNCHECKED_CAST")
            val ids = (snap?.get("memberIds") as? List<String>).orEmpty()
            trySend(ids)
        }
        awaitClose { reg.remove() }
    }

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
        // Bez Cloud Functions, subcollection-i ne cascade — moramo eksplicitno pre nego
        // što obrišemo parent doc (posle brisanja parent-a, rules ne dozvoljavaju
        // read/write jer circleData(cid) vraća null). Cleanup obuhvata: members,
        // places, placeEvents, etaShares, checkIns. Bez etaShares/checkIns cleanup-a,
        // orphaned docs ostaju u Firestore storage-u (nedostupni preko rules, ali
        // storage cost + GDPR incomplete).
        listOf("members", "places", "placeEvents", "etaShares", "checkIns", "speedingEvents").forEach { name ->
            runCatching {
                val snap = circle(circleId).collection(name).get().await()
                snap.documents.forEach { runCatching { it.reference.delete().await() } }
            }.onFailure { Timber.w(it, "deleteCircle: subcollection %s cleanup failed", name) }
        }
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
