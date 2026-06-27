package org.krug.app.core.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TimeBucketTest {

    private val now = 1_700_000_000_000L

    // --- bucketCompactLastSeen ---

    @Test fun `compact null returns Dash`() {
        assertThat(bucketCompactLastSeen(null, now)).isEqualTo(CompactTimeBucket.Dash)
    }

    @Test fun `compact zero returns Dash`() {
        assertThat(bucketCompactLastSeen(0L, now)).isEqualTo(CompactTimeBucket.Dash)
    }

    @Test fun `compact under 1 min returns JustNow`() {
        assertThat(bucketCompactLastSeen(now, now)).isEqualTo(CompactTimeBucket.JustNow)
        assertThat(bucketCompactLastSeen(now - 59_999L, now)).isEqualTo(CompactTimeBucket.JustNow)
    }

    @Test fun `compact at 1 min returns Minutes`() {
        assertThat(bucketCompactLastSeen(now - 60_000L, now))
            .isEqualTo(CompactTimeBucket.Minutes(1))
    }

    @Test fun `compact under 60 min returns Minutes`() {
        assertThat(bucketCompactLastSeen(now - 5 * 60_000L, now))
            .isEqualTo(CompactTimeBucket.Minutes(5))
        assertThat(bucketCompactLastSeen(now - 59 * 60_000L, now))
            .isEqualTo(CompactTimeBucket.Minutes(59))
    }

    @Test fun `compact at 60 min returns Hours`() {
        assertThat(bucketCompactLastSeen(now - 60 * 60_000L, now))
            .isEqualTo(CompactTimeBucket.Hours(1))
    }

    @Test fun `compact under 24h returns Hours`() {
        assertThat(bucketCompactLastSeen(now - 2 * 60 * 60_000L, now))
            .isEqualTo(CompactTimeBucket.Hours(2))
        assertThat(bucketCompactLastSeen(now - 23 * 60 * 60_000L, now))
            .isEqualTo(CompactTimeBucket.Hours(23))
    }

    @Test fun `compact at 24h returns DayPlus`() {
        assertThat(bucketCompactLastSeen(now - 24 * 60 * 60_000L, now))
            .isEqualTo(CompactTimeBucket.DayPlus)
    }

    @Test fun `compact days ago returns DayPlus`() {
        assertThat(bucketCompactLastSeen(now - 7 * 24 * 60 * 60_000L, now))
            .isEqualTo(CompactTimeBucket.DayPlus)
    }

    // --- bucketSosRelative ---

    @Test fun `sos zero returns Empty`() {
        assertThat(bucketSosRelative(0L, now)).isEqualTo(RelativeTimeBucket.Empty)
    }

    @Test fun `sos negative returns Empty`() {
        assertThat(bucketSosRelative(-1L, now)).isEqualTo(RelativeTimeBucket.Empty)
    }

    @Test fun `sos under 1 min returns JustNow`() {
        assertThat(bucketSosRelative(now, now)).isEqualTo(RelativeTimeBucket.JustNow)
        assertThat(bucketSosRelative(now - 30_000L, now)).isEqualTo(RelativeTimeBucket.JustNow)
    }

    @Test fun `sos at 1 min returns Minutes`() {
        assertThat(bucketSosRelative(now - 60_000L, now))
            .isEqualTo(RelativeTimeBucket.Minutes(1))
    }

    @Test fun `sos under 60 min returns Minutes`() {
        assertThat(bucketSosRelative(now - 45 * 60_000L, now))
            .isEqualTo(RelativeTimeBucket.Minutes(45))
        assertThat(bucketSosRelative(now - 59 * 60_000L, now))
            .isEqualTo(RelativeTimeBucket.Minutes(59))
    }

    @Test fun `sos at 60 min returns Hours`() {
        assertThat(bucketSosRelative(now - 60 * 60_000L, now))
            .isEqualTo(RelativeTimeBucket.Hours(1))
    }

    @Test fun `sos under 24h returns Hours`() {
        assertThat(bucketSosRelative(now - 5 * 60 * 60_000L, now))
            .isEqualTo(RelativeTimeBucket.Hours(5))
        assertThat(bucketSosRelative(now - 23 * 60 * 60_000L, now))
            .isEqualTo(RelativeTimeBucket.Hours(23))
    }

    @Test fun `sos at 24h returns Days`() {
        assertThat(bucketSosRelative(now - 24 * 60 * 60_000L, now))
            .isEqualTo(RelativeTimeBucket.Days(1))
    }

    @Test fun `sos days ago returns Days`() {
        assertThat(bucketSosRelative(now - 7 * 24 * 60 * 60_000L, now))
            .isEqualTo(RelativeTimeBucket.Days(7))
    }
}
