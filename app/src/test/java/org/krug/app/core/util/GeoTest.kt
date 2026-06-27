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

    @Test fun `bucketDistance under 50m is Nearby`() {
        assertThat(bucketDistance(0.0)).isEqualTo(DistanceBucket.Nearby)
        assertThat(bucketDistance(49.9)).isEqualTo(DistanceBucket.Nearby)
    }

    @Test fun `bucketDistance at 50m is Meters`() {
        assertThat(bucketDistance(50.0)).isEqualTo(DistanceBucket.Meters(50))
    }

    @Test fun `bucketDistance under 1km is Meters`() {
        assertThat(bucketDistance(523.4)).isEqualTo(DistanceBucket.Meters(523))
        assertThat(bucketDistance(999.9)).isEqualTo(DistanceBucket.Meters(999))
    }

    @Test fun `bucketDistance at 1km is KmDecimal`() {
        assertThat(bucketDistance(1000.0)).isEqualTo(DistanceBucket.KmDecimal(1.0))
    }

    @Test fun `bucketDistance under 10km is KmDecimal`() {
        assertThat(bucketDistance(2500.0)).isEqualTo(DistanceBucket.KmDecimal(2.5))
        val b = bucketDistance(9999.0) as DistanceBucket.KmDecimal
        assertThat(b.km).isWithin(0.001).of(9.999)
    }

    @Test fun `bucketDistance at 10km is KmInt`() {
        assertThat(bucketDistance(10_000.0)).isEqualTo(DistanceBucket.KmInt(10))
    }

    @Test fun `bucketDistance over 10km is KmInt`() {
        assertThat(bucketDistance(71_500.0)).isEqualTo(DistanceBucket.KmInt(71))
    }
}
