package org.krug.app.core.util

/**
 * Generic proximity clustering. Greedy single-link algoritam — za svaki item, traži
 * postojeći klaster gde je barem jedan član unutar `thresholdMeters`; ako nađe,
 * pridruži; ako ne, novi klaster.
 *
 * Zbog single-link semantike, dugačak lanac razdvojenih tačaka može biti spojen u
 * jedan klaster iako su terminalne tačke daleko. Za <20 items sa realistic distances
 * (npr. Krug member locations), ovo je acceptable trade-off za simpler code.
 *
 * Items sa `null` koordinatama se preskaču (izostavljaju iz output-a).
 *
 * @param items input list
 * @param latLng function koja vraca (lat, lng) pair za item, ili null ako nema lokaciju
 * @param thresholdMeters distance threshold (koristi haversine)
 * @return lista klaster-lista (svaki klaster ima 1+ item-a)
 */
fun <T> clusterByProximity(
    items: List<T>,
    latLng: (T) -> Pair<Double, Double>?,
    thresholdMeters: Double,
): List<List<T>> {
    val groups = mutableListOf<MutableList<Pair<T, Pair<Double, Double>>>>()
    for (item in items) {
        val ll = latLng(item) ?: continue
        var placed = false
        for (group in groups) {
            val near = group.any { (_, existingLL) ->
                haversineMeters(ll.first, ll.second, existingLL.first, existingLL.second) < thresholdMeters
            }
            if (near) {
                group.add(item to ll)
                placed = true
                break
            }
        }
        if (!placed) groups.add(mutableListOf(item to ll))
    }
    return groups.map { g -> g.map { it.first } }
}
