package org.krug.app.core.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TimeTest {

    private val now = 1_700_000_000_000L  // fiksni epoch za reproducibilnost

    @Test fun `compactLastSeen null returns dash`() {
        assertThat(compactLastSeen(null, now)).isEqualTo("-")
    }

    @Test fun `compactLastSeen zero returns dash`() {
        assertThat(compactLastSeen(0L, now)).isEqualTo("-")
    }

    @Test fun `compactLastSeen under one minute returns sad`() {
        assertThat(compactLastSeen(now - 30_000L, now)).isEqualTo("sad")
    }

    @Test fun `compactLastSeen minutes`() {
        assertThat(compactLastSeen(now - 5 * 60_000L, now)).isEqualTo("5min")
        assertThat(compactLastSeen(now - 59 * 60_000L, now)).isEqualTo("59min")
    }

    @Test fun `compactLastSeen hours`() {
        assertThat(compactLastSeen(now - 60 * 60_000L, now)).isEqualTo("1h")
        assertThat(compactLastSeen(now - 23 * 60 * 60_000L, now)).isEqualTo("23h")
    }

    @Test fun `compactLastSeen over a day`() {
        assertThat(compactLastSeen(now - 25L * 60 * 60_000L, now)).isEqualTo("1d+")
        assertThat(compactLastSeen(now - 100L * 60 * 60_000L, now)).isEqualTo("1d+")
    }

    @Test fun `sosRelativeTime invalid returns empty`() {
        assertThat(sosRelativeTime(0L, now)).isEqualTo("")
        assertThat(sosRelativeTime(-1L, now)).isEqualTo("")
    }

    @Test fun `sosRelativeTime under one minute returns upravo sada`() {
        assertThat(sosRelativeTime(now - 30_000L, now)).isEqualTo("upravo sada")
    }

    @Test fun `sosRelativeTime minutes`() {
        assertThat(sosRelativeTime(now - 2 * 60_000L, now)).isEqualTo("pre 2 min")
        assertThat(sosRelativeTime(now - 59 * 60_000L, now)).isEqualTo("pre 59 min")
    }

    @Test fun `sosRelativeTime hours`() {
        assertThat(sosRelativeTime(now - 60 * 60_000L, now)).isEqualTo("pre 1 h")
        assertThat(sosRelativeTime(now - 5 * 60 * 60_000L, now)).isEqualTo("pre 5 h")
    }

    @Test fun `sosRelativeTime days`() {
        assertThat(sosRelativeTime(now - 25L * 60 * 60_000L, now)).isEqualTo("pre 1 d")
        assertThat(sosRelativeTime(now - 73L * 60 * 60_000L, now)).isEqualTo("pre 3 d")
    }
}
