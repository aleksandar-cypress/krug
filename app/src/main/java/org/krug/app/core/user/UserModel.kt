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
    /**
     * Premium entitlement flag. Postavlja se server-side kroz Cloud Function iz
     * Play Billing receipt validation-a (planirano v1.2+). Klijent NE sme da self-update
     * ovo polje (firestore.rules odbija promenu). Trenutno je false za sve; admin
     * može ručno da toggle-uje u Firestore konzoli za privatno testiranje.
     */
    val isPremium: Boolean = false,
    /**
     * Kad ističe premium subscription (upisuje Cloud Function iz receipt validation-a).
     * Null kad user nije premium ili je lifetime. Klijent koristi za "grace period"
     * UX (npr. warning 3 dana pre expiry-ja). NE koristi @ServerTimestamp — vrednost
     * je business logic, ne server clock.
     */
    val premiumUntil: Date? = null,
    @ServerTimestamp val createdAt: Date? = null,
    @ServerTimestamp val lastSeenAt: Date? = null,
)
