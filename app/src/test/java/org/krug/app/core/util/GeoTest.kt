package org.krug.app.core.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GeoTest {

    @Test fun `haversine same point is zero`() {
        val d = haversineMeters(44.7866, 20.4489, 44.7866, 20.4489)
        assertThat(d).isWithin(0.001).of(0.0)
    }

    @Test fun `haversine belgrade to novi sad is approx 70km`() {
        // Beograd (44.7866, 20.4489) → Novi Sad (45.2671, 19.8335).
        // Stvarna vazdušna distance ≈ 71 km.
        val d = haversineMeters(44.7866, 20.4489, 45.2671, 19.8335)
        assertThat(d / 1000.0).isWithin(2.0).of(71.0)
    }

    @Test fun `haversine belgrade to cacak is approx 120km`() {
        // Beograd → Čačak (43.8914, 20.3497) ≈ 100 km vazdušnom linijom.
        val d = haversineMeters(44.7866, 20.4489, 43.8914, 20.3497)
        assertThat(d / 1000.0).isWithin(3.0).of(100.0)
    }

    @Test fun `haversine symmetric`() {
        val a = haversineMeters(44.7866, 20.4489, 45.2671, 19.8335)
        val b = haversineMeters(45.2671, 19.8335, 44.7866, 20.4489)
        assertThat(a).isWithin(0.001).of(b)
    }

    @Test fun `formatDistance under 50m returns blizu`() {
        assertThat(formatDistance(0.0)).isEqualTo("blizu")
        assertThat(formatDistance(49.9)).isEqualTo("blizu")
    }

    @Test fun `formatDistance under 1km returns meters`() {
        assertThat(formatDistance(50.0)).isEqualTo("50 m")
        assertThat(formatDistance(523.4)).isEqualTo("523 m")
        assertThat(formatDistance(999.9)).isEqualTo("999 m")
    }

    @Test fun `formatDistance under 10km returns one decimal km`() {
        assertThat(formatDistance(1000.0)).isEqualTo("1.0 km")
        assertThat(formatDistance(2500.0)).isEqualTo("2.5 km")
        assertThat(formatDistance(9999.0)).isEqualTo("10.0 km")
    }

    @Test fun `formatDistance over 10km returns integer km`() {
        assertThat(formatDistance(10_000.0)).isEqualTo("10 km")
        assertThat(formatDistance(71_500.0)).isEqualTo("71 km")
    }
}
