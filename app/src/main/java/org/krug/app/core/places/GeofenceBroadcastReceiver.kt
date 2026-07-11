package org.krug.app.core.places

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Process
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.krug.app.core.prefs.LocalPrefs
import timber.log.Timber

/**
 * Prima geofence enter/exit event od Google Play Services.
 *
 * Request ID format: `{circleId}:{placeId}`. Iz njega izvlačimo circle context,
 * čitamo placeName iz Firestore-a (za notif payload), i pišemo PlaceEvent u
 * `/circles/{circleId}/placeEvents/`. Ostali članovi kruga listen-uju subcollection
 * i pokazuju lokalnu notif (nema Cloud Functions u v1.1).
 *
 * Sopstveni event ne triggeruje sopstvenu notif (klijent filter u listener-u).
 */
@AndroidEntryPoint
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    @Inject lateinit var firestore: FirebaseFirestore
    @Inject lateinit var auth: FirebaseAuth
    @Inject lateinit var placeRepository: PlaceRepository
    @Inject lateinit var localPrefs: LocalPrefs

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        /**
         * In-memory dedup: (placeId + type) → last event time. Defence layer preko fixera
         * u GeofenceManager (INITIAL_TRIGGER = 0) i accuracy/age filtera. Cooldown 5min
         * (bilo 60s) da pokrijemo Google Play Services reconciliation storm-ove — kad
         * GPS accuracy varira, Play može da fire-uje istu tranziciju nekoliko puta unutar
         * par minuta. 5min je bezbedan za realne use case-e (user ne ulazi/izlazi iz istog
         * mesta 5+ puta u 5 min).
         */
        private const val DEDUP_WINDOW_MS = 5L * 60_000L

        /**
         * Cross-type dedup: kad za isti place stigne obrnuta tranzicija unutar ovog
         * prozora (npr. LEFT pa ARRIVED, ili ENTER pa EXIT), drugi event je 99%
         * phantom (GPS jitter na granici, Doze wake reconciliation, screen-wake
         * fizika). Legitiman scenario „user zaboravio nesto pa se vratio unutar
         * minute" je redak i bezopasan da se skip-uje. Bug (Jul 2026, screen-wake):
         * user samo probudio ekran, drugi je dobio dve poruke LEFT + ARRIVED u
         * istom trenutku iako se fizicki niko nije pomerio.
         */
        private const val OPPOSITE_DEDUP_WINDOW_MS = 90_000L

        /**
         * Timeout za fresh GPS fix pri verifikaciji. BroadcastReceiver.goAsync() ima
         * **10s Android limit** (ne 30s kako je bilo napisano — API dokumentacija). Za
         * budžet: 10s ukupno, minus ~1-2s Firestore fetch (placeInfo + logEvent),
         * minus ~500ms overhead → GPS timeout ne sme preći ~7s. 8s je bezbedan
         * gornji limit; ako nema fresh fix-a, fallback na stale triggeringLocation
         * (FALLBACK_LOC_* konstante) pa ako i to ne uspe → Inconclusive verify.
         * Bug (Jul 2026): stariji 15s timeout je mogao dovesti do system kill BR-a
         * bez `pendingResult.finish()` — event izgubljen, listener state nekonzistentan.
         */
        private const val GPS_VERIFY_TIMEOUT_MS = 8_000L

        /**
         * Tolerancija oko granice geofence-a pri verifikaciji. Play Services može
         * fire-ovati EXIT dok je user 30-80m unutar zone (GPS jitter), ili ENTER
         * pre nego što stvarno pređe granicu. Zato prihvatamo event samo ako je
         * fresh GPS potvrdi da je user JASNO s druge strane (radius +/- 100m).
         *  - EXIT prihvatamo ako je distance > radius + 100m
         *  - ENTER prihvatamo ako je distance <= radius + 100m (dovoljno blizu / unutra)
         *
         * Bump 50→100 (Jul 2026): user prijavljuje duple ENTER notif-e sa 1-2h razmakom
         * dok je fizički bio kod kuće. Root cause: phantom EXIT je prošao verify jer je
         * GPS drift bio dovoljan (ili verify fallback vratio null i propustio event bez
         * distance provere). 100m prag traži drift ≥ radius+100m da bi se EXIT priznao —
         * realno samo pri fizičkom izlasku iz zone. Dodatno je uveden semantički guard
         * (persist-uje se u `LocalPrefs.loadPlaceTransitionTypes()`) koji blokira
         * ENTER→ENTER bez EXIT-a između.
         */
        private const val PHANTOM_THRESHOLD_M = 100

        /**
         * "Jeftin verify" kvalifikacija — ako `event.triggeringLocation` ispunjava
         * ova dva uslova, koristimo ga direktno umesto poziva `getCurrentLocation`.
         * Time izbegavamo dodatnih 1-15s HIGH_ACCURACY GPS-a po transition-u
         * (baterija). Kad user fizički prelazi granicu, triggeringLocation je
         * tipično <20m accuracy i <5s star, pa jeftin path pokriva 80-90% slučajeva.
         *
         * Fallback na fresh fix se koristi kad je triggeringLocation odsutan,
         * neprecizan (npr. Play je fire-ovala EXIT baziran na WiFi/cell netovima),
         * ili star (post-Doze reconciliation).
         */
        private const val TRIGGERING_LOC_MAX_ACCURACY_M = 50f
        private const val TRIGGERING_LOC_MAX_AGE_MS = 30_000L

        /**
         * "Poslednji resort" — kad fresh GPS fetch fail-uje (Doze restrictions, no fix),
         * pre nego što padnemo na Inconclusive, prihvatamo stale triggeringLocation
         * sa širim tolerancijama. Bolje verifikovati sa 5min starim, 150m accuracy fix-om
         * nego bez ičega. Bug (Jul 2026, screen-wake): telefon zaključan, Play fire
         * spurious LEFT + ARRIVED za placeu gde je user fizički bio; sa strogim cheap
         * path constraints (50m/30s) fell back na fresh fetch koji Doze prekinuo pa
         * Inconclusive → phantom prošao. Sa širim fallback-om, stale fix pokaže da je
         * user unutar place-a → Confirmed IN → phantom EXIT skip.
         */
        private const val FALLBACK_LOC_MAX_ACCURACY_M = 150f
        private const val FALLBACK_LOC_MAX_AGE_MS = 5L * 60_000L
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        if (intent.action != GeofenceManager.ACTION_GEOFENCE_TRANSITION) return

        val event = GeofencingEvent.fromIntent(intent)
        if (event == null) {
            Timber.w("GeofenceReceiver: null GeofencingEvent")
            return
        }
        if (event.hasError()) {
            Timber.w("GeofenceReceiver: event error code=%d", event.errorCode)
            return
        }
        val transition = event.geofenceTransition
        val type = when (transition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> PlaceEventModel.TYPE_ENTER
            Geofence.GEOFENCE_TRANSITION_EXIT -> PlaceEventModel.TYPE_EXIT
            else -> {
                Timber.d("GeofenceReceiver: ignore transition=%d", transition)
                return
            }
        }
        val triggered = event.triggeringGeofences.orEmpty()
        if (triggered.isEmpty()) {
            Timber.w("GeofenceReceiver: no triggering geofences")
            return
        }
        // FILTER 1 (STARTUP GRACE): Play Services firira spurious reconciliation event-e u
        // prvih 2min posle geofence re-registracije. Ovi event-i imaju fresh accuracy i
        // timestamp, pa ih standardni filter-i ne hvataju. Real-world scenario koji je
        // trigger-ovao ovo (Jul 2026): user je uninstall + reinstall (posle 1.1.1 crash-a),
        // po instalaciji 1.1.2 geofence subsystem se re-registruje i Play Services "reconciles"
        // stanje slanjem ENTER Home + EXIT Banjica + EXIT babina kuca istovremeno iako user
        // nije mrdao. Drugi članovi kruga dobiju 3 spam notif-a.
        // In-memory companion se resetuje na 0 pri proces death, pa ako je Play Services
        // fire-ovala broadcast pre nego što je LocationTrackingService podigao FGS +
        // registerAll(), lastRegisteredAtMs=0 i grace ne štiti. Fallback: čitanje iz
        // LocalPrefs (persist na disk). Bug (Jul 2026, Jelena restart): user je killed
        // + reopened app dok je bio u placeu; Play je poslala reconciliation ENTER pre
        // registracije, drugi članovi kruga su dobili phantom notif.
        val nowMs = System.currentTimeMillis()
        val effectiveRegisterMs = GeofenceManager.lastRegisteredAtMs.takeIf { it > 0L }
            ?: localPrefs.lastGeofenceRegisterMs
        val sinceRegister = nowMs - effectiveRegisterMs
        if (effectiveRegisterMs > 0L && sinceRegister < GeofenceManager.STARTUP_GRACE_MS) {
            Timber.w(
                "GeofenceReceiver: skip transition inside startup grace (%dms since register, type=%d, count=%d)",
                sinceRegister, transition, triggered.size,
            )
            return
        }
        // Process uptime grace: pokriva scenario kad Play Services fire broadcast dok
        // je proces tek startao — LocationTrackingService još nije stigao da re-registruje
        // geofences pa `lastGeofenceRegisterMs` iz LocalPrefs pokazuje na prethodnu sesiju
        // (starije od 2min pa gornji grace ne štiti). BroadcastReceiver može biti pozvan
        // "cold" (Play Services starta samo proces za ovaj event), Process.getStartElapsedRealtime
        // hvata tačno taj scenario. Bug (Jul 2026): Jelena kill+reopen dok je u placeu →
        // Play fire reconciliation ENTER PRE nego što LocationTrackingService podigne FGS.
        val processUptimeMs = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
        if (processUptimeMs in 0L until GeofenceManager.STARTUP_GRACE_MS) {
            Timber.w(
                "GeofenceReceiver: skip transition inside process startup grace (uptime=%dms, type=%d, count=%d)",
                processUptimeMs, transition, triggered.size,
            )
            return
        }
        // FILTER 2 (BATCH RECONCILIATION): user u realnom svetu ne moze da predje 2+ granice
        // istovremeno (mesta su tipicno 100m+ razdaljena). Kad Play Services firira broadcast
        // sa 2+ geofences odjednom, to je 99% reconciliation "state fix" event, ne stvarna
        // fizicka tranzicija. Skip ceo broadcast.
        if (triggered.size >= 2) {
            Timber.w(
                "GeofenceReceiver: skip batch transition (likely reconciliation, count=%d type=%d)",
                triggered.size, transition,
            )
            return
        }
        // Filter na kvalitet triggering location-a. Google Play Services zna da "reconciliraju"
        // geofence stanje sa unbelievable events (npr. user je fizicki bio na X, GPS je jitter-ovao
        // ka Y, sad Play kaze "exit Y" iako user nikad nije bio na Y). Signal:
        // - Accuracy > 150m: GPS je bio nepouzdan, ne verujemo transition-u.
        // - Age > 5 min: transition je back-dated, ili user je bio offline. Skip.
        val triggeringLocation = event.triggeringLocation
        if (triggeringLocation != null) {
            val accuracy = triggeringLocation.accuracy
            val ageMs = System.currentTimeMillis() - triggeringLocation.time
            if (accuracy > 150f) {
                Timber.w("GeofenceReceiver: skip low-accuracy transition (accuracy=%.1fm type=%d)", accuracy, transition)
                return
            }
            if (ageMs > 5 * 60_000L) {
                Timber.w("GeofenceReceiver: skip stale transition (age=%dms type=%d)", ageMs, transition)
                return
            }
        }
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Timber.w("GeofenceReceiver: no auth user, skip logging")
            return
        }

        val pendingResult = goAsync()
        scope.launch {
            try {
                val userName = fetchUserName(uid)
                // FILTER 4 (GPS VERIFY): pre nego što upišemo event, verifikuj distancu
                // do centra place-a. Ako fizičko stanje ne odgovara tipu event-a → phantom, skip.
                //
                // Real-world scenario (Jul 2026): Jelena je dobila notif da je user "izašao iz
                // babine lokacije" u 21:28h iako je user bio kod kuće sve vreme. Play Services
                // je poslao spurious EXIT sa fresh accuracy/timestamp posle Doze wake-up-a.
                // Postojeci filteri 1-3 ne hvataju to jer signal izgleda validan. GPS verify
                // je jedini pouzdan način: uporedi lokaciju sa center-om place-a i threshold-om.
                //
                // Dva-stepeni pristup (baterija):
                //  A) Jeftin path: koristi `event.triggeringLocation` ako je accuracy < 50m
                //     i age < 30s. Play Services ga već ima u ruci, ne pravimo novi GPS poziv.
                //     Pokriva 80-90% slučajeva kad user fizički prelazi granicu.
                //  B) Skup path (fallback): `getCurrentLocation(HIGH_ACCURACY)` sa 15s timeout.
                //     Aktivira se kad je triggeringLocation odsutan/neprecizan (WiFi-only fix,
                //     wake-from-Doze reconciliation, itd.). Tada baš tu i treba pouzdan fresh
                //     fix — phantom-i najčešće dolaze upravo iz te kategorije.
                //
                // Ako oba puta faila (timeout, permission) degradiramo na stanje pre ovog
                // fiksa (upisujemo event bez verifikacije). Bolje malo phantom event-a nego
                // totalno gubljenje real event-a.
                val triggeringLoc = event.triggeringLocation
                val triggeringAgeMs = if (triggeringLoc != null) {
                    System.currentTimeMillis() - triggeringLoc.time
                } else {
                    Long.MAX_VALUE
                }
                val verifyLocation: Location? = if (
                    triggeringLoc != null &&
                    triggeringLoc.accuracy < TRIGGERING_LOC_MAX_ACCURACY_M &&
                    triggeringAgeMs < TRIGGERING_LOC_MAX_AGE_MS
                ) {
                    Timber.d(
                        "GeofenceReceiver: verify via triggeringLocation (acc=%.1fm age=%dms) — cheap path",
                        triggeringLoc.accuracy, triggeringAgeMs,
                    )
                    triggeringLoc
                } else {
                    Timber.d(
                        "GeofenceReceiver: triggeringLocation not suitable (acc=%s age=%s), fetching fresh fix",
                        triggeringLoc?.accuracy?.toString() ?: "null",
                        if (triggeringLoc != null) "${triggeringAgeMs}ms" else "n/a",
                    )
                    tryGetFreshLocation(context) ?: triggeringLoc?.takeIf {
                        // Poslednji resort: prihvati stale triggeringLocation kad fresh
                        // fetch fail (Doze). Vidi FALLBACK_LOC_* docs.
                        it.accuracy < FALLBACK_LOC_MAX_ACCURACY_M &&
                            triggeringAgeMs < FALLBACK_LOC_MAX_AGE_MS
                    }?.also {
                        Timber.d(
                            "GeofenceReceiver: fresh fetch failed, fallback to stale triggeringLocation (acc=%.1fm age=%dms)",
                            it.accuracy, triggeringAgeMs,
                        )
                    }
                }
                if (verifyLocation == null) {
                    Timber.w("GeofenceReceiver: no location for verify, proceeding without")
                }
                // Persistent per-place transition-type map (LocalPrefs backed). Read-only
                // load ovde (za PhantomFilter classify); commit ide preko atomic
                // updatePlaceTransitionType da spreči race između paralelnih broadcast-a.
                val persistedTypes = localPrefs.loadPlaceTransitionTypes()
                // Dedup mape se učitavaju iz LocalPrefs (TTL-filtered) tako da process
                // death ne resetuje stanje. Mutate lokalno u forEach loop-u, sačuvaj
                // batch-u na kraju (izbegava per-event disk write). Vidi LocalPrefs
                // docs za razlog persistence-a.
                val eventDedupMap = localPrefs.loadEventDedup(DEDUP_WINDOW_MS)
                val oppositeDedupMap = localPrefs.loadOppositeDedup(OPPOSITE_DEDUP_WINDOW_MS)
                var dedupDirty = false
                triggered.forEach { fence ->
                    val (circleId, placeId) = parseRequestId(fence.requestId) ?: return@forEach
                    val placeInfo = fetchPlaceInfo(circleId, placeId)
                    val prevRecord = persistedTypes[placeId]
                    val prevType = prevRecord?.type
                    val prevAgeMs = prevRecord?.atMs?.let { atMs ->
                        if (atMs <= 0L) Long.MAX_VALUE else (nowMs - atMs).coerceAtLeast(0L)
                    } ?: Long.MAX_VALUE
                    // Izračunaj GPS verify outcome pa proslijedi u PhantomFilter (pure fn).
                    // Bug koji ovo popravlja (Jul 2026, Jelena): user prijavljuje EXIT
                    // notif za place na kome fizički nikad nije bio. Root cause: verify
                    // je vraćao null (GPS timeout posle Doze) pa je stariji kod propustao
                    // event bez ikakve provere. Novi fail-closed brani ovaj scenario jer
                    // prefs neće imati ENTER (user nikad tamo nije bio).
                    val verifyOutcome = if (verifyLocation != null && placeInfo != null) {
                        val distance = distanceMeters(
                            verifyLocation.latitude, verifyLocation.longitude,
                            placeInfo.lat, placeInfo.lng,
                        )
                        val thresholdIn = placeInfo.radius + PHANTOM_THRESHOLD_M
                        PhantomFilter.VerifyOutcome.Confirmed(userInside = distance <= thresholdIn)
                    } else {
                        PhantomFilter.VerifyOutcome.Inconclusive
                    }
                    val decision = PhantomFilter.classify(type, prevType, prevAgeMs, verifyOutcome)
                    if (decision is PhantomFilter.Decision.Skip) {
                        Timber.w(
                            "GeofenceReceiver: skip %s place=%s prev=%s age=%s verify=%s — %s",
                            type,
                            placeInfo?.name ?: placeId,
                            prevType ?: "null",
                            if (prevAgeMs == Long.MAX_VALUE) "n/a" else "${prevAgeMs}ms",
                            verifyOutcome::class.simpleName,
                            decision.reason,
                        )
                        return@forEach
                    }
                    // Cross-type dedup: ako je isti place upravo (unutar 90s) generisao
                    // obrnutu tranziciju, ovo je 99% phantom (screen-wake burst iz Doze
                    // reconciliation-a). Bug: user probudio ekran, drugi dobio LEFT +
                    // ARRIVED skoro istovremeno iako se nisu pomerali. Persistent u
                    // LocalPrefs jer process death između dva broadcast-a bi izgubio
                    // in-memory state (proces može biti ubijen zbog memory pressure-a).
                    val opposite = oppositeDedupMap[placeId]
                    if (opposite != null && opposite.first != type &&
                        (nowMs - opposite.second) < OPPOSITE_DEDUP_WINDOW_MS
                    ) {
                        Timber.w(
                            "GeofenceReceiver: skip opposite %s (last %s %dms ago) place=%s",
                            type, opposite.first, nowMs - opposite.second,
                            placeInfo?.name ?: placeId,
                        )
                        return@forEach
                    }
                    // Dedup POSLE verify-a: da phantom event ne zauzme dedup slot i time
                    // blokira legitiman event koji stigne par minuta kasnije. Persistent
                    // u LocalPrefs iz istog razloga kao opposite-dedup — proces death.
                    val key = "$placeId:$type"
                    val last = eventDedupMap[key] ?: 0L
                    if (nowMs - last < DEDUP_WINDOW_MS) {
                        Timber.d("GeofenceReceiver: dedup skip $key (last ${nowMs - last}ms ago)")
                        return@forEach
                    }
                    eventDedupMap[key] = nowMs
                    oppositeDedupMap[placeId] = type to nowMs
                    dedupDirty = true
                    localPrefs.updatePlaceTransitionType(placeId, type, nowMs)
                    val placeName = placeInfo?.name ?: fetchPlaceName(circleId, placeId) ?: placeId
                    placeRepository.logEvent(
                        circleId = circleId,
                        placeId = placeId,
                        placeName = placeName,
                        userId = uid,
                        userName = userName,
                        type = type,
                    )
                }
                // Batch dedup persist na kraju loop-a — jedan disk write nezavisno
                // od broja event-a. Ako nema izmena (svi skinuti), preskoči.
                if (dedupDirty) {
                    localPrefs.saveEventDedup(eventDedupMap)
                    localPrefs.saveOppositeDedup(oppositeDedupMap)
                }
            } catch (e: Exception) {
                Timber.e(e, "GeofenceReceiver: logging failed")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private data class PlaceInfo(
        val name: String,
        val lat: Double,
        val lng: Double,
        val radius: Int,
    )

    private suspend fun fetchPlaceInfo(circleId: String, placeId: String): PlaceInfo? {
        return runCatching {
            val doc = firestore.collection("circles").document(circleId)
                .collection("places").document(placeId).get().await()
            val name = doc.getString("name") ?: return@runCatching null
            val lat = doc.getDouble("lat") ?: return@runCatching null
            val lng = doc.getDouble("lng") ?: return@runCatching null
            val radius = (doc.getLong("radius") ?: 100L).toInt()
            PlaceInfo(name = name, lat = lat, lng = lng, radius = radius)
        }.onFailure { e ->
            // Bez ovog log-a Crashlytics ne prijavljuje Firestore fail-ove →
            // verify pada u Inconclusive tiho i phantom event prolazi. Ako se
            // dešavaju masovno (permission denied, offline predugo), moraju biti
            // vidljivi u dashboard-u.
            Timber.w(e, "GeofenceReceiver: fetchPlaceInfo failed circle=%s place=%s", circleId, placeId)
        }.getOrNull()
    }

    private fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
        val out = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, out)
        return out[0]
    }

    private suspend fun tryGetFreshLocation(context: Context): Location? {
        val hasFine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine) {
            Timber.w("GeofenceReceiver: no fine location permission, skip GPS verify")
            return null
        }
        val client = LocationServices.getFusedLocationProviderClient(context)
        val cts = CancellationTokenSource()
        return try {
            withTimeoutOrNull(GPS_VERIFY_TIMEOUT_MS) {
                @SuppressLint("MissingPermission")
                val task = client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                task.await()
            }
        } catch (e: Exception) {
            Timber.w(e, "GeofenceReceiver: getCurrentLocation failed")
            null
        } finally {
            cts.cancel()
        }
    }

    private fun parseRequestId(requestId: String): Pair<String, String>? {
        val parts = requestId.split(":", limit = 2)
        if (parts.size != 2) {
            Timber.w("GeofenceReceiver: malformed requestId=%s", requestId)
            return null
        }
        return parts[0] to parts[1]
    }

    private suspend fun fetchUserName(uid: String): String {
        return runCatching {
            firestore.collection("users").document(uid).get().await()
                .getString("displayName").orEmpty()
        }.onFailure { e ->
            Timber.w(e, "GeofenceReceiver: fetchUserName failed uid=%s", uid)
        }.getOrDefault("").ifBlank { "Član" }
    }

    private suspend fun fetchPlaceName(circleId: String, placeId: String): String? {
        return runCatching {
            firestore.collection("circles").document(circleId)
                .collection("places").document(placeId).get().await()
                .getString("name")
        }.onFailure { e ->
            Timber.w(e, "GeofenceReceiver: fetchPlaceName failed circle=%s place=%s", circleId, placeId)
        }.getOrNull()
    }
}
