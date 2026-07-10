package org.krug.app.navigation

import kotlinx.serialization.Serializable

// Top-level graph entries.
@Serializable object Splash
@Serializable object Auth
@Serializable data class Onboarding(val skipIntro: Boolean = false)
@Serializable object Main

// Main destinations.
@Serializable object Map
@Serializable object CircleList
@Serializable object CreateCircle
@Serializable data class ShowInvite(val circleId: String, val circleName: String, val code: String)
@Serializable data class EnterCode(val prefilledCode: String? = null)
@Serializable data class CircleDetail(val circleId: String)
@Serializable data class Places(val circleId: String)
@Serializable data class AddPlace(
    val circleId: String,
    // Optional prefill iz Place Suggestion-a (auto-detektovan hotspot iz history-ja).
    // Ako je non-null, AddPlaceScreen: (1) fly-to na (lat,lng) umesto current location,
    // (2) prefill name text field.
    val prefillLat: Double? = null,
    val prefillLng: Double? = null,
    val prefillName: String? = null,
)
@Serializable data class History(val uid: String, val displayName: String)
@Serializable data class DrivingReports(val uid: String, val displayName: String)

// Settings sub-tree.
@Serializable object Settings
@Serializable object Account
@Serializable object Privacy
@Serializable object BatteryMode
@Serializable object MapStyle
@Serializable object Reliability
@Serializable object NotificationsSettings
@Serializable object About
@Serializable object DeleteAccount
@Serializable object Diagnostics
