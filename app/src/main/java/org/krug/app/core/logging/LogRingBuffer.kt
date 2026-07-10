package org.krug.app.core.logging

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * In-memory bounded ring buffer za poslednjih [CAPACITY] log linija. Koristi ga
 * [RingBufferTree] da hvata sve što ide kroz Timber, a `DiagnosticsScreen` čita
 * `dump()` da priloži u „Send report" email.
 *
 * Zašto in-memory (bez file persistence-a):
 *  - Ako app crash-uje, tester ionako gubi ove logove — testeri prijavljuju bugove
 *    *tokom* iste sesije (dok se app još izvršava), a ne posle restart-a.
 *  - File I/O na svaki log poziv usporio bi FGS/coroutine performanse.
 *  - Retention se ionako gasi na `System.gc()` / process kill — za odmor test to je OK.
 *
 * Format linije: `HH:mm:ss.SSS P TAG: message` — kompaktno za email body (mailto:
 * URI ima realan limit ~64KB pre nego što ga Gmail truncate-uje).
 *
 * Thread-safety: synchronized(lock) — Timber loguje iz mnogo thread-ova (Main, FGS,
 * coroutine dispatcher-i, WorkManager). Kontencija je retka pa `synchronized` je
 * dovoljna; nema potrebe za lock-free data strukture.
 *
 * Kapacitet 500 linija * ~150 char = ~75KB — fits u proces memoriju bez uticaja.
 */
object LogRingBuffer {

    const val CAPACITY = 500

    private val lock = Any()
    private val buffer = ArrayDeque<String>(CAPACITY)
    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun append(priority: Char, tag: String?, message: String) {
        val time = timeFmt.format(Date())
        val line = if (tag.isNullOrBlank()) {
            "$time $priority: $message"
        } else {
            "$time $priority $tag: $message"
        }
        synchronized(lock) {
            if (buffer.size >= CAPACITY) buffer.removeFirst()
            buffer.addLast(line)
        }
    }

    /** Snapshot poslednjih [lastN] linija. Ako `lastN >= CAPACITY`, vraća sve. */
    fun dump(lastN: Int = CAPACITY): List<String> {
        return synchronized(lock) {
            if (lastN >= buffer.size) buffer.toList()
            else buffer.toList().takeLast(lastN)
        }
    }

    /** Za testove i „Fresh start" scenario u Diagnostics-u. */
    fun clear() {
        synchronized(lock) { buffer.clear() }
    }
}
