package org.krug.app.core.util

import android.content.Context
import org.krug.app.R

/**
 * Kratak relativni timestamp — "sad/now", "5min", "2h", "1d+".
 * Koristi se na map chip-ovima i sličnim mestima gde je važna kompaktnost.
 * Lokalizovan kroz string resources (vidi values/strings.xml + values-sr/strings.xml).
 */
fun compactLastSeen(
    context: Context,
    updatedAt: Long?,
    now: Long = System.currentTimeMillis(),
): String {
    if (updatedAt == null || updatedAt == 0L) return context.getString(R.string.time_dash)
    val diffMs = now - updatedAt
    val mins = diffMs / 60_000
    return when {
        mins < 1 -> context.getString(R.string.time_just_now_short)
        mins < 60 -> context.getString(R.string.time_minutes_short, mins)
        mins < 60 * 24 -> context.getString(R.string.time_hours_short, mins / 60)
        else -> context.getString(R.string.time_day_plus)
    }
}

/**
 * Verbose relativni timestamp za SOS banner i offline body — "upravo sada/just now",
 * "pre 5 min / 5 min ago". Razlikuje se od compactLastSeen-a po dužini — više prostora
 * + jasno "pre" / "ago" prefiks/sufiks.
 */
fun sosRelativeTime(
    context: Context,
    triggeredAt: Long,
    now: Long = System.currentTimeMillis(),
): String {
    if (triggeredAt <= 0L) return ""
    val diffMin = (now - triggeredAt) / 60_000L
    return when {
        diffMin < 1 -> context.getString(R.string.time_just_now_long)
        diffMin < 60 -> context.getString(R.string.time_minutes_ago, diffMin)
        diffMin < 60 * 24 -> context.getString(R.string.time_hours_ago, diffMin / 60)
        else -> context.getString(R.string.time_days_ago, diffMin / (60 * 24))
    }
}
