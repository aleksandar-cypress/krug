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
    @ServerTimestamp val createdAt: Date? = null,
    val expiresAt: Date? = null,
)

object CirclePresets {
    val colors: List<String> = listOf(
        "#4F46E5", // indigo
        "#10B981", // emerald
        "#F59E0B", // amber
        "#EF4444", // red
        "#EC4899", // pink
        "#8B5CF6", // violet
        "#06B6D4", // cyan
        "#F97316", // orange
    )

    val icons: List<String> = listOf("family", "friends", "work", "school")
}
