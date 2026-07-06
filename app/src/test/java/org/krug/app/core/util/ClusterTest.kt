package org.krug.app.core.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ClusterTest {

    // Test items — string id + lat/lng.
    private data class TestPoint(val id: String, val lat: Double, val lng: Double)

    private val toLatLng: (TestPoint) -> Pair<Double, Double>? = { p -> p.lat to p.lng }

    @Test fun `empty input returns empty output`() {
        val out = clusterByProximity<TestPoint>(emptyList(), toLatLng, 100.0)
        assertThat(out).isEmpty()
    }

    @Test fun `single item forms single cluster`() {
        val out = clusterByProximity(
            listOf(TestPoint("a", 44.7866, 20.4489)),
            toLatLng, 100.0,
        )
        assertThat(out).hasSize(1)
        assertThat(out[0]).hasSize(1)
        assertThat(out[0][0].id).isEqualTo("a")
    }

    @Test fun `two nearby items merge into one cluster`() {
        // 44.7866 vs 44.7867 lat diff ~11m. Threshold 100m obuhvata.
        val out = clusterByProximity(
            listOf(
                TestPoint("a", 44.7866, 20.4489),
                TestPoint("b", 44.7867, 20.4489),
            ),
            toLatLng, 100.0,
        )
        assertThat(out).hasSize(1)
        assertThat(out[0].map { it.id }).containsExactly("a", "b").inOrder()
    }

    @Test fun `two far items form two clusters`() {
        // Beograd vs Novi Sad ~71km — daleko iznad 100m threshold-a.
        val out = clusterByProximity(
            listOf(
                TestPoint("beograd", 44.7866, 20.4489),
                TestPoint("novi_sad", 45.2671, 19.8335),
            ),
            toLatLng, 100.0,
        )
        assertThat(out).hasSize(2)
        assertThat(out[0].map { it.id }).containsExactly("beograd")
        assertThat(out[1].map { it.id }).containsExactly("novi_sad")
    }

    @Test fun `null latLng items are skipped`() {
        val out = clusterByProximity(
            listOf(
                TestPoint("a", 44.7866, 20.4489),
                TestPoint("no-loc", 0.0, 0.0),
                TestPoint("b", 44.7867, 20.4489),
            ),
            { p -> if (p.id == "no-loc") null else p.lat to p.lng },
            100.0,
        )
        // Trebalo bi 1 cluster sa a + b, no-loc skipped.
        assertThat(out).hasSize(1)
        assertThat(out[0].map { it.id }).containsExactly("a", "b").inOrder()
    }

    @Test fun `three items — two near, one far`() {
        val out = clusterByProximity(
            listOf(
                TestPoint("a", 44.7866, 20.4489),
                TestPoint("b", 44.7867, 20.4489), // ~11m od a
                TestPoint("c", 45.2671, 19.8335), // ~71km od a/b
            ),
            toLatLng, 100.0,
        )
        assertThat(out).hasSize(2)
        assertThat(out[0].map { it.id }).containsExactly("a", "b").inOrder()
        assertThat(out[1].map { it.id }).containsExactly("c")
    }

    @Test fun `threshold boundary — items at exactly threshold do NOT merge`() {
        // Bez precizne razdaljine, ali 100m se moze potvrditi haversine-om — koristimo
        // dovoljno veliku razliku da bude iznad 100m. Vec ~0.001° lat = 111m.
        val out = clusterByProximity(
            listOf(
                TestPoint("a", 44.7866, 20.4489),
                TestPoint("b", 44.7877, 20.4489), // ~122m od a
            ),
            toLatLng, 100.0,
        )
        assertThat(out).hasSize(2)
    }

    @Test fun `single-link chain merges through transitivity`() {
        // Chain: a—b su blizu, b—c su blizu, a—c nisu blizu direktno. Sve tri idu u
        // jedan cluster jer greedy vidi da je b blizu (a,c) → jedna grupa.
        val out = clusterByProximity(
            listOf(
                TestPoint("a", 44.7866, 20.4489),
                TestPoint("b", 44.7876, 20.4489), // ~111m od a
                TestPoint("c", 44.7886, 20.4489), // ~111m od b, ~222m od a
            ),
            toLatLng, 150.0,
        )
        // Sa threshold 150m: a-b close, b-c close, a-c NOT close direct — ali greedy sa
        // grupom [a, b] test-ira b protiv c → close, pa c ide u istu grupu.
        assertThat(out).hasSize(1)
        assertThat(out[0].map { it.id }).containsExactly("a", "b", "c").inOrder()
    }
}
