package org.krug.app.navigation

import kotlinx.serialization.Serializable

// Top-level graph entries.
@Serializable object Splash
@Serializable object Auth
@Serializable object Onboarding
@Serializable object Main

// Main destinations.
@Serializable object Map
@Serializable object CircleList
@Serializable object CreateCircle
@Serializable data class ShowInvite(val circleId: String, val circleName: String, val code: String)
@Serializable data class EnterCode(val prefilledCode: String? = null)
@Serializable data class CircleDetail(val circleId: String)
@Serializable data class MemberDetail(val circleId: String, val memberId: String)

// Settings sub-tree.
@Serializable object Settings
@Serializable object Account
@Serializable object Privacy
@Serializable object BatteryMode
@Serializable object NotificationsSettings
@Serializable object About
@Serializable object DeleteAccount
