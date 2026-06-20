package org.krug.app.core.circle

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class CircleModel(
    val id: String = "",
    val name: String = "",
    val colorHex: String = "#4F46E5",
    val iconKey: String = "family",
    val ownerId: String = "",
    val memberIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Date? = null,
)

data class MemberModel(
    val uid: String = "",
    val role: String = ROLE_MEMBER,
    val nickname: String? = null,
    val shareLocation: Boolean = true,
    /**
     * Roditeljska kontrola — vlasnik kruga može označiti člana kao "dete".
     * Klijent enforce: skriva pause-share toggle, hide leave/delete-account dugmad.
     * Server enforce nije implementiran u v1 — može se zaobići dekompajliranjem.
     */
    val isChild: Boolean = false,
    @ServerTimestamp val joinedAt: Date? = null,
) {
    companion object {
        const val ROLE_OWNER = "OWNER"
        const val ROLE_MEMBER = "MEMBER"
    }
}

data class InviteModel(
    val code: String = "",
    val circleId: String = "",
    val inviterUid: String = "",
    val maxUses: Int = 1,
    val usedBy: List<String> = emptyList(),
    /** Ako je true, novi član se odmah upisuje sa isChild=true (roditeljska kontrola). */
    val prefillIsChild: Boolean = false,
    @ServerTimestamp val createdAt: Date? = null,
    val expiresAt: Date? = null,
)

object CirclePresets {
    /** 6 boja koje stanu u jedan red u Color picker-u (FlowRow 40dp + 12dp gap ≈ 300dp). */
    val colors: List<String> = listOf(
        "#4F46E5", // indigo
        "#10B981", // emerald
        "#F59E0B", // amber
        "#EF4444", // red
        "#EC4899", // pink
        "#8B5CF6", // violet
    )

    /**
     * Ključevi ikona — render mapping je u CircleIconAssets (feature/circle UI).
     * Držimo na 4 najuniverzalnija — staju u jedan red sa većim krugovima.
     */
    val icons: List<String> = listOf(
        "family",
        "friends",
        "travel",
        "event",
    )
}
