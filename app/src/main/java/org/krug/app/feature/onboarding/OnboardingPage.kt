package org.krug.app.feature.onboarding

import android.content.Context
import org.krug.app.core.permissions.PermissionUtils

enum class OnboardingPage {
    INTRO,
    LOCATION, // foreground + background combinovano (state machine u istom ekranu)
    NOTIFICATIONS,
    ;

    fun isApplicable(context: Context): Boolean = when (this) {
        NOTIFICATIONS -> PermissionUtils.needsNotificationsPermission
        else -> true
    }
}

fun buildOnboardingPages(context: Context, skipIntro: Boolean = false): List<OnboardingPage> =
    OnboardingPage.entries.filter {
        it.isApplicable(context) && !(skipIntro && it == OnboardingPage.INTRO)
    }
