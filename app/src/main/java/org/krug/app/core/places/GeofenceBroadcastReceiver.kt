package org.krug.app.core.places

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
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
        private val lastEventTimes = mutableMapOf<String, Long>()

        /**
         * Timeout za fresh GPS fix pri verifikaciji. BroadcastReceiver.goAsync() daje
         * oko 30s do system kill-a, ostavljamo margine za Firestore fetch (place info +
         * logEvent write). 15s je uobičajen fused HIGH_ACCURACY warm-fix; ako uređaj
         * nema recent GPS, može trajati duže i mi ćemo fallback-ovati na standardnu
         * validaciju (bez GPS verify), tj. propustiti event kao pre.
         */
        private const val GPS_VERIFY_TIMEOUT_MS = 15_000L

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
        val sinceRegister = System.currentTimeMillis() - GeofenceManager.lastRegisteredAtMs
        if (GeofenceManager.lastRegisteredAtMs > 0L && sinceRegister < GeofenceManager.STARTUP_GRACE_MS) {
            Timber.w(
                "GeofenceReceiver: skip transition inside startup grace (%dms since register, type=%d, count=%d)",
                sinceRegister, transition, triggered.size,
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
                    tryGetFreshLocation(context)
                }
                if (verifyLocation == null) {
                    Timber.w("GeofenceReceiver: no location for verify, proceeding without")
                }
                // Persistent per-place transition-type map (LocalPrefs backed). Učitavamo
                // jednom po event-u, mutiramo lokalno, snimimo na kraju. Vraćamo se na prefs
                // (a ne samo in-memory companion) da Doze wake/process kill ne resetuje state.
                val persistedTypes = localPrefs.loadPlaceTransitionTypes()
                triggered.forEach { fence ->
                    val (circleId, placeId) = parseRequestId(fence.requestId) ?: return@forEach
                    val placeInfo = fetchPlaceInfo(circleId, placeId)
                    val prevType = persistedTypes[placeId]
                    // GPS verifikacija (Filter 4). Dva režima:
                    //  A) Verify uspešan (verifyLocation != null && placeInfo != null):
                    //     radi puni distance check kao pre — odbaci phantom po fizičkoj
                    //     poziciji spram center-a place-a.
                    //  B) Verify inconclusive (bilo koja komponenta null — GPS timeout ili
                    //     Firestore fetch fail): FAIL-CLOSED za EXIT — traži da postoji
                    //     PRETHODNI persisted ENTER u prefs. Ako ga nema, event je 99%
                    //     phantom (Play Services „reconciles" state za mesto gde user nikad
                    //     nije bio → EXIT fajruje iz vazduha). ENTER u inconclusive režimu
                    //     puštamo — miss legit ENTER je manja šteta od tihog gubljenja
                    //     stvarnog dolaska.
                    //
                    //  Bug koji ovo popravlja (Jul 2026, Jelena): user prijavljuje EXIT
                    //  notif za place na kome fizički nikad nije bio. Root cause: verify
                    //  je vraćao null (GPS timeout posle Doze) pa je stariji kod propustao
                    //  event bez ikakve provere. Novi fail-closed brani ovaj scenario jer
                    //  prefs neće imati ENTER (user nikad tamo nije bio).
                    if (verifyLocation != null && placeInfo != null) {
                        val distance = distanceMeters(
                            verifyLocation.latitude, verifyLocation.longitude,
                            placeInfo.lat, placeInfo.lng,
                        )
                        val thresholdIn = placeInfo.radius + PHANTOM_THRESHOLD_M
                        when (type) {
                            PlaceEventModel.TYPE_EXIT -> if (distance <= thresholdIn) {
                                Timber.w(
                                    "GeofenceReceiver: skip phantom EXIT place=%s dist=%.1fm radius=%d (user still inside)",
                                    placeInfo.name, distance, placeInfo.radius,
                                )
                                return@forEach
                            }
                            PlaceEventModel.TYPE_ENTER -> if (distance > thresholdIn) {
                                Timber.w(
                                    "GeofenceReceiver: skip phantom ENTER place=%s dist=%.1fm radius=%d (user still outside)",
                                    placeInfo.name, distance, placeInfo.radius,
                                )
                                return@forEach
                            }
                        }
                    } else if (type == PlaceEventModel.TYPE_EXIT && prevType != PlaceEventModel.TYPE_ENTER) {
                        Timber.w(
                            "GeofenceReceiver: skip phantom EXIT placeId=%s (verify inconclusive, no prior ENTER — verifyLoc=%s placeInfo=%s prevType=%s)",
                            placeId,
                            verifyLocation != null,
                            placeInfo != null,
                            prevType ?: "null",
                        )
                        return@forEach
                    }
                    // Dedup POSLE verify-a: da phantom event ne zauzme dedup slot i time
                    // blokira legitiman event koji stigne par minuta kasnije.
                    val key = "$placeId:$type"
                    val now = System.currentTimeMillis()
                    val last = synchronized(lastEventTimes) { lastEventTimes[key] } ?: 0L
                    if (now - last < DEDUP_WINDOW_MS) {
                        Timber.d("GeofenceReceiver: dedup skip $key (last ${now - last}ms ago)")
                        return@forEach
                    }
                    // Semantički guard: ne dopusti ENTER→ENTER (ili EXIT→EXIT) za isti place
                    // bez suprotnog event-a između. Hvata slučaj kad phantom EXIT ipak prođe
                    // distance filter (GPS drift preko radius+100m par minuta), pa sledeći
                    // ENTER nema fizičku osnovu. Sada čita iz persisted prefs → survive
                    // Doze/process kill.
                    if (prevType == type) {
                        Timber.w(
                            "GeofenceReceiver: skip repeated %s for place=%s (no intervening opposite transition)",
                            type, placeInfo?.name ?: placeId,
                        )
                        return@forEach
                    }
                    synchronized(lastEventTimes) { lastEventTimes[key] = now }
                    persistedTypes[placeId] = type
                    localPrefs.savePlaceTransitionTypes(persistedTypes)
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
        }.getOrDefault("").ifBlank { "Član" }
    }

    private suspend fun fetchPlaceName(circleId: String, placeId: String): String? {
        return runCatching {
            firestore.collection("circles").document(circleId)
                .collection("places").document(placeId).get().await()
                .getString("name")
        }.getOrNull()
    }
}
