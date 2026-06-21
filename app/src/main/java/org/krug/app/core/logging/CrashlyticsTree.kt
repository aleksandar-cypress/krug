package org.krug.app.core.logging

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber

/**
 * Timber tree koji forward-uje logove u Crashlytics.
 *
 * - INFO: dodaje se kao breadcrumb (`crashlytics.log()`) bez non-fatal report-a.
 *   Koristi se za user actions (SOS, circle switch, sign-in) i lifecycle (FGS start).
 *   Crashlytics drži poslednjih ~64KB log-ova per crash — INFO breadcrumbi daju
 *   kontekst stack-u koji bi inače bio go.
 * - WARN+: breadcrumb + non-fatal exception (RuntimeException ako nema throwable-a).
 * - DEBUG/VERBOSE: drop (samo logcat).
 *
 * Sa Timber.tag()-om dobijemo komponentu pa stack u Crashlytics ima kontekst.
 */
class CrashlyticsTree : Timber.Tree() {

    private val crashlytics = FirebaseCrashlytics.getInstance()

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority < Log.INFO) return
        val composed = if (tag.isNullOrBlank()) message else "$tag: $message"
        crashlytics.log(composed)
        if (t != null) {
            crashlytics.recordException(t)
        } else if (priority >= Log.ERROR) {
            // Logovan error bez throwable-a — i dalje vredan, snimi kao non-fatal.
            crashlytics.recordException(RuntimeException(composed))
        }
        // INFO/WARN bez throwable-a: ostaje samo kao breadcrumb (nije non-fatal).
    }
}
