package org.krug.app.core.util

import android.content.Context
import org.krug.app.R

/**
 * Pure bucket za kompaktan "last seen" prikaz na map chip-ovima.
 * Testabilan bez Context-a (vidi TimeBucketTest).
 */
sealed class CompactTimeBucket {
    object Dash : CompactTimeBucket()
    object JustNow : CompactTimeBucket()
    data class Minutes(val n: Long) : CompactTimeBucket()
    data class Hours(val n: Long) : CompactTimeBucket()
    object DayPlus : CompactTimeBucket()
}

fun bucketCompactLastSeen(
    updatedAt: Long?,
    now: Long = System.currentTimeMillis(),
): CompactTimeBucket {
    if (updatedAt == null || updatedAt == 0L) return CompactTimeBucket.Dash
    val mins = (now - updatedAt) / 60_000
    return when {
        mins < 1 -> CompactTimeBucket.JustNow
        mins < 60 -> CompactTimeBucket.Minutes(mins)
        mins < 60 * 24 -> CompactTimeBucket.Hours(mins / 60)
        else -> CompactTimeBucket.DayPlus
    }
}

/**
 * Kratak relativni timestamp — "sad/now", "5min", "2h", "1d+".
 * Lokalizovan kroz string resources (values/strings.xml + values-sr/strings.xml).
 */
fun compactLastSeen(
    context: Context,
    updatedAt: Long?,
    now: Long = System.currentTimeMillis(),
): String = when (val b = bucketCompactLastSeen(updatedAt, now)) {
    CompactTimeBucket.Dash -> context.getString(R.string.time_dash)
    CompactTimeBucket.JustNow -> context.getString(R.string.time_just_now_short)
    is CompactTimeBucket.Minutes -> context.getString(R.string.time_minutes_short, b.n)
    is CompactTimeBucket.Hours -> context.getString(R.string.time_hours_short, b.n)
    CompactTimeBucket.DayPlus -> context.getString(R.string.time_day_plus)
}

/**
 * Pure bucket za verbose "ago" prikaz (SOS banner, offline body).
 */
sealed class RelativeTimeBucket {
    object Empty : RelativeTimeBucket()
    object JustNow : RelativeTimeBucket()
    data class Minutes(val n: Long) : RelativeTimeBucket()
    data class Hours(val n: Long) : RelativeTimeBucket()
    data class Days(val n: Long) : RelativeTimeBucket()
}

fun bucketSosRelative(
    triggeredAt: Long,
    now: Long = System.currentTimeMillis(),
): RelativeTimeBucket {
    if (triggeredAt <= 0L) return RelativeTimeBucket.Empty
    val diffMin = (now - triggeredAt) / 60_000L
    return when {
        diffMin < 1 -> RelativeTimeBucket.JustNow
        diffMin < 60 -> RelativeTimeBucket.Minutes(diffMin)
        diffMin < 60 * 24 -> RelativeTimeBucket.Hours(diffMin / 60)
        else -> RelativeTimeBucket.Days(diffMin / (60 * 24))
    }
}

/**
 * Verbose relativni timestamp za SOS banner i offline body — "upravo sada/just now",
 * "pre 5 min / 5 min ago".
 */
fun sosRelativeTime(
    context: Context,
    triggeredAt: Long,
    now: Long = System.currentTimeMillis(),
): String = when (val b = bucketSosRelative(triggeredAt, now)) {
    RelativeTimeBucket.Empty -> ""
    RelativeTimeBucket.JustNow -> context.getString(R.string.time_just_now_long)
    is RelativeTimeBucket.Minutes -> context.getString(R.string.time_minutes_ago, b.n)
    is RelativeTimeBucket.Hours -> context.getString(R.string.time_hours_ago, b.n)
    is RelativeTimeBucket.Days -> context.getString(R.string.time_days_ago, b.n)
}
