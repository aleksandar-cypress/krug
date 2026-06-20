package org.krug.app.feature.onboarding

import android.content.Context
import org.krug.app.core.permissions.PermissionUtils

enum class OnboardingPage {
    INTRO,
    LOCATION,
    BACKGROUND_LOCATION,
    NOTIFICATIONS,
    BATTERY,
    DONE,
    ;

    fun isApplicable(context: Context): Boolean = when (this) {
        BACKGROUND_LOCATION -> PermissionUtils.needsBackgroundLocationPermission
        NOTIFICATIONS -> PermissionUtils.needsNotificationsPermission
        else -> true
    }
}

fun buildOnboardingPages(context: Context): List<OnboardingPage> =
    OnboardingPage.entries.filter { it.isApplicable(context) }
