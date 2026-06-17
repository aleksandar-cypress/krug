package org.krug.app.core.user

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

// Firestore POJO for users/{uid}.
data class UserModel(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val fcmToken: String? = null,
    val deviceModel: String = "",
    val onboardingCompleted: Boolean = false,
    @ServerTimestamp val createdAt: Date? = null,
    @ServerTimestamp val lastSeenAt: Date? = null,
)
