package org.krug.app.core.speeding

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.krug.app.core.circle.CircleRepository
import timber.log.Timber

/**
 * State machine za detekciju prekoračenja brzine iz stream-a GPS fixes-a.
 *
 * NOT_SPEEDING → SPEEDING: `MIN_HITS` uzastopnih fix-a sa speed >= threshold.
 * SPEEDING → NOT_SPEEDING: fix sa speed < threshold ILI reset window istekao.
 * Kada SPEEDING traje >= `MIN_DURATION_MS`, emit event u sve krugove korisnika.
 *
 * Dedup: `DEDUP_WINDOW_MS` posle emit-a — user koji vozi 30min sa 130km/h dobija
 * jedan event, ne 30. Ako spusti brzinu <threshold pa opet pređe, treba `DEDUP_WINDOW_MS`
 * da prođe da bi se emitovao novi event.
 *
 * State je in-memory (kao TripDetector) — process death briše, ali speeding event-i
 * su „ovog trenutka" signali, ne persistuju se između sesija.
 */
@Singleton
class SpeedingDetector @Inject constructor(
    private val speedingRepository: SpeedingRepository,
    private val circleRepository: CircleRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var state: State = State.NotSpeeding
    private val lastEmitAtByUid = ConcurrentHashMap<String, Long>()

    private sealed class State {
        object NotSpeeding : State()
        data class Speeding(
            val startAt: Long,
            var maxSpeedKmh: Int,
            var lastLat: Double,
            var lastLng: Double,
            var emitted: Boolean,
        ) : State()
    }

    /**
     * Ubaci nov GPS fix. Ako `enabled=false` ili `thresholdKmh<=0`, resetuje state
     * (equivalent grazioznog off).
     *
     * `accuracyM` je GPS horizontal accuracy iz `Location.accuracy`. Fix-evi sa lošom
     * preciznošću (>50m) često imaju divlje speed vrednosti (recomputed iz jitter-a),
     * skipujemo ih da izbegnemo phantom speeding event dok user stoji na parkingu i
     * GPS drift-uje. Ne resetujemo state — pravi movement se odvija kroz kombinaciju
     * dobrih i loših fix-eva.
     */
    fun onFix(
        uid: String,
        userName: String,
        lat: Double,
        lng: Double,
        speedMps: Float,
        accuracyM: Float,
        nowMs: Long,
        thresholdKmh: Int,
        enabled: Boolean,
    ) {
        if (!enabled || thresholdKmh <= 0) {
            state = State.NotSpeeding
            return
        }
        if (accuracyM > MAX_ACCURACY_M) {
            // Unreliable fix — ignorišemo ga (ne advanciramo state machine).
            return
        }
        val speedKmh = (speedMps * 3.6f).toInt()
        val overThreshold = speedKmh >= thresholdKmh
        when (val s = state) {
            is State.NotSpeeding -> {
                if (overThreshold) {
                    state = State.Speeding(
                        startAt = nowMs,
                        maxSpeedKmh = speedKmh,
                        lastLat = lat,
                        lastLng = lng,
                        emitted = false,
                    )
                }
            }
            is State.Speeding -> {
                if (overThreshold) {
                    if (speedKmh > s.maxSpeedKmh) s.maxSpeedKmh = speedKmh
                    s.lastLat = lat
                    s.lastLng = lng
                    val duration = nowMs - s.startAt
                    if (!s.emitted && duration >= MIN_DURATION_MS) {
                        // Dedup check pre emit-a.
                        val lastEmit = lastEmitAtByUid[uid] ?: 0L
                        if (nowMs - lastEmit >= DEDUP_WINDOW_MS) {
                            s.emitted = true
                            lastEmitAtByUid[uid] = nowMs
                            emit(
                                uid = uid,
                                userName = userName,
                                maxSpeedKmh = s.maxSpeedKmh,
                                thresholdKmh = thresholdKmh,
                                durationSec = (duration / 1000L).toInt(),
                                lat = s.lastLat,
                                lng = s.lastLng,
                            )
                        }
                    }
                } else {
                    // Ispod threshold-a → reset state. Ako je već emit-ovan, ostaje dedup-ovan
                    // dok DEDUP_WINDOW_MS ne prođe (lastEmitAtByUid).
                    state = State.NotSpeeding
                }
            }
        }
    }

    private fun emit(
        uid: String,
        userName: String,
        maxSpeedKmh: Int,
        thresholdKmh: Int,
        durationSec: Int,
        lat: Double,
        lng: Double,
    ) {
        scope.launch {
            runCatching {
                val circleIds = circleRepository.observeMyCircles(uid).first().map { it.id }
                speedingRepository.logEvent(
                    circleIds = circleIds,
                    userId = uid,
                    userName = userName,
                    maxSpeedKmh = maxSpeedKmh,
                    thresholdKmh = thresholdKmh,
                    durationSec = durationSec,
                    lat = lat,
                    lng = lng,
                )
            }.onFailure { Timber.w(it, "SpeedingDetector: emit failed") }
        }
        Timber.i(
            "SpeedingDetector: emit uid=%s max=%d thr=%d dur=%ds",
            uid, maxSpeedKmh, thresholdKmh, durationSec,
        )
    }

    companion object {
        /** Uzastopno iznad threshold-a duže od ovog → emit. Filter kratke špic-jitter-e. */
        const val MIN_DURATION_MS = 5_000L

        /** Ne emit-uj isti user-ov speeding event češće od ovog. */
        const val DEDUP_WINDOW_MS = 10 * 60_000L

        /**
         * Max GPS accuracy (u metrima) da bismo fix uzeli u obzir za speed detekciju.
         * Iznad ovog, `speed` je uglavnom recomputed iz jitter-a i nepouzdan. 50m je
         * konzistentan sa TRIGGERING_LOC_MAX_ACCURACY_M u GeofenceBroadcastReceiver-u.
         */
        const val MAX_ACCURACY_M = 50f
    }
}
