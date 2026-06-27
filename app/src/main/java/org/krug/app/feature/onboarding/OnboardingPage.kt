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

    /**
     * True ako permission za ovu stranicu već granted-uvana van app-a (npr. iz sistemskih
     * Settings-a). U tom slučaju stranica je nepotrebna i preskače se. INTRO se nikad ne
     * preskače — to je welcome ekran.
     */
    fun isAlreadyGranted(context: Context): Boolean = when (this) {
        INTRO -> false
        LOCATION -> PermissionUtils.hasForegroundLocation(context) &&
            PermissionUtils.hasBackgroundLocation(context)
        NOTIFICATIONS -> PermissionUtils.hasNotifications(context)
    }
}

fun buildOnboardingPages(context: Context, skipIntro: Boolean = false): List<OnboardingPage> =
    OnboardingPage.entries.filter {
        it.isApplicable(context) &&
            !it.isAlreadyGranted(context) &&
            !(skipIntro && it == OnboardingPage.INTRO)
    }
