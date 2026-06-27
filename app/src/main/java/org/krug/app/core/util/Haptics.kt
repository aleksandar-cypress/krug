package org.krug.app.core.util

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Brand-friendly haptics za key actions. API constraints:
 * - minSdk 26 ima samo LONG_PRESS / VIRTUAL_KEY / KEYBOARD_TAP
 * - API 30+ dodaje CONFIRM / REJECT / GESTURE_END (semantički tačniji za success vs error)
 *
 * Pozivaj iz Composable-a kroz `LocalView.current.confirmHaptic()` posle key akcija
 * koje korisnik treba da OSETI (SOS confirm, circle create success, join success).
 */

/** Success / acceptance — laki dvostruki tap-feedback na API 30+, fallback LONG_PRESS niže. */
fun View.confirmHaptic() {
    val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.CONFIRM
    } else {
        HapticFeedbackConstants.LONG_PRESS
    }
    performHapticFeedback(constant)
}

/** Rejection / heavy — koristi se za SOS trigger (irreversible, severe). API 30+ jasniji. */
fun View.rejectHaptic() {
    val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.REJECT
    } else {
        HapticFeedbackConstants.LONG_PRESS
    }
    performHapticFeedback(constant)
}

/** Lagani click — marker tap, picker selection. */
fun View.clickHaptic() {
    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
}
