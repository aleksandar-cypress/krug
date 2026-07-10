package org.krug.app.core.logging

import android.util.Log
import timber.log.Timber

/**
 * Timber tree koji forwarduje sve INFO+ logove u [LogRingBuffer]. Instalira se
 * pored `DebugTree` (u debug build-u) i `CrashlyticsTree` (u release) u
 * `KrugApplication.onCreate`.
 *
 * Priority mapping u kratkim slovima (za kompaktan email body): V/D/I/W/E/A.
 * DEBUG i VERBOSE se DROP-uju u release da bafer ne bude spamovan chatty log-ovima
 * (Timber.d/v pozivi u vrelim putevima kao FGS heartbeat, publisher, itd.). U debug
 * puštamo D i V da developer vidi ceo tok.
 *
 * Throwable stack trace se ne uključuje u linijski format (bio bi predugačak); umesto
 * toga logujemo klasu i poruku throwable-a. Ako treba full stack, `getStackTraceString`
 * može se dodati later.
 */
class RingBufferTree(private val includeDebug: Boolean) : Timber.Tree() {

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (!includeDebug && priority < Log.INFO) return
        val short = when (priority) {
            Log.VERBOSE -> 'V'
            Log.DEBUG -> 'D'
            Log.INFO -> 'I'
            Log.WARN -> 'W'
            Log.ERROR -> 'E'
            Log.ASSERT -> 'A'
            else -> '?'
        }
        val composed = if (t != null) {
            "$message | ${t.javaClass.simpleName}: ${t.message.orEmpty()}"
        } else {
            message
        }
        LogRingBuffer.append(short, tag, composed)
    }
}
