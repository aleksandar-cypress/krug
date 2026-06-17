package org.krug.app.core.logging

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber

/**
 * Timber tree koji forward-uje WARN/ERROR/ASSERT logove + bilo koji throwable
 * u Crashlytics. INFO/DEBUG/VERBOSE se ignorišu (smeju da budu samo lokalni).
 * Sa Timber.tag()-om dobijemo komponentu pa stack u Crashlytics ima kontekst.
 */
class CrashlyticsTree : Timber.Tree() {

    private val crashlytics = FirebaseCrashlytics.getInstance()

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority < Log.WARN) return
        val composed = if (tag.isNullOrBlank()) message else "$tag: $message"
        crashlytics.log(composed)
        if (t != null) {
            crashlytics.recordException(t)
        } else if (priority >= Log.ERROR) {
            // Logovan error bez throwable-a — i dalje vredan, snimi kao non-fatal.
            crashlytics.recordException(RuntimeException(composed))
        }
    }
}
