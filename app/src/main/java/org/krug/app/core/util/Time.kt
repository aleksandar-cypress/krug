package org.krug.app.core.util

/**
 * Kratak relativni timestamp — "sad", "5min", "2h", "1d+".
 * Koristi se na map chip-ovima i sličnim mestima gde je važna kompaktnost.
 * Sufiks "min" umesto "m" jer je "m" dvosmisleno (metri vs minute) u kontekstu
 * gde u istom chip row-u stoji distance ("5m" = 5 metara, "5min" = 5 minuta).
 */
fun compactLastSeen(updatedAt: Long?, now: Long = System.currentTimeMillis()): String {
    if (updatedAt == null || updatedAt == 0L) return "-"
    val diffMs = now - updatedAt
    val mins = diffMs / 60_000
    return when {
        mins < 1 -> "sad"
        mins < 60 -> "${mins}min"
        mins < 60 * 24 -> "${mins / 60}h"
        else -> "1d+"
    }
}

/**
 * Verbose relativni timestamp za SOS banner — "upravo sada", "pre 5 min", "pre 2 h".
 * Razlikuje se od compactLastSeen-a po dužini — SOS banner ima više prostora i benefit-uje
 * od jasnog "pre" prefiksa (kompaktni "5m" bi mogao da znači raspon, ne tačku).
 */
fun sosRelativeTime(triggeredAt: Long, now: Long = System.currentTimeMillis()): String {
    if (triggeredAt <= 0L) return ""
    val diffMin = (now - triggeredAt) / 60_000L
    return when {
        diffMin < 1 -> "upravo sada"
        diffMin < 60 -> "pre $diffMin min"
        diffMin < 60 * 24 -> "pre ${diffMin / 60} h"
        else -> "pre ${diffMin / (60 * 24)} d"
    }
}
