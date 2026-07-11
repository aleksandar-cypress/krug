package org.krug.app.core.car

import android.content.Intent
import android.net.Uri
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarLocation
import androidx.car.app.model.ItemList
import androidx.car.app.model.Metadata
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceMarker
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.model.PlaceListMapTemplate
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.krug.app.R
import org.krug.app.core.circle.CircleRepository
import org.krug.app.core.location.LocationRepository
import org.krug.app.core.places.PlaceModel
import org.krug.app.core.places.PlaceRepository
import org.krug.app.core.prefs.LocalPrefs
import org.krug.app.core.user.UserRepository
import org.krug.app.core.util.formatDistance
import org.krug.app.core.util.haversineMeters
import timber.log.Timber

/**
 * Prvi ekran kada se konektujemo na Auto. Prikazuje listu članova aktivnog kruga
 * sa njihovom lokacijom (marker + adresa), tap → Google Maps navigate intent.
 *
 * Template: PlaceListNavigationTemplate = split-view sa mapom pored liste. Auto host
 * automatski render-uje mapu na osnovu Place.location fielda; ne treba mi custom
 * surface callback za MVP.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MapCarScreen(carContext: CarContext) : Screen(carContext) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface CarEntryPoint {
        fun circleRepository(): CircleRepository
        fun locationRepository(): LocationRepository
        fun userRepository(): UserRepository
        fun placeRepository(): PlaceRepository
        fun localPrefs(): LocalPrefs
        fun firebaseAuth(): FirebaseAuth
    }

    private val entryPoint = EntryPointAccessors.fromApplication(
        carContext.applicationContext,
        CarEntryPoint::class.java,
    )
    private val circleRepository = entryPoint.circleRepository()
    private val locationRepository = entryPoint.locationRepository()
    private val userRepository = entryPoint.userRepository()
    private val placeRepository = entryPoint.placeRepository()
    private val localPrefs = entryPoint.localPrefs()
    private val auth = entryPoint.firebaseAuth()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var collectorJob: Job? = null

    /** Member list snapshot — re-invalidate-uje se pri promeni. */
    @Volatile private var members: List<CarMember> = emptyList()
    @Volatile private var places: List<PlaceModel> = emptyList()
    @Volatile private var loaded: Boolean = false
    /** Sopstvena (auto) lat/lng za distance-to-member u subtitle. Null dok nemamo fix. */
    @Volatile private var selfLat: Double? = null
    @Volatile private var selfLng: Double? = null
    /**
     * UID člana na kog je user zadnje kliknuo. Koristi ga onGetTemplate za setAnchor —
     * PlaceListMapTemplate centrira mapu na anchor. Bez ovog, klik na člana samo
     * pokretao Google Maps intent (spolja) umesto da pomera mapu unutar Auto ekrana,
     * što je user prijavio kao bug (scroll pomera, klik ne).
     */
    @Volatile private var focusedMemberUid: String? = null

    private val authListener = com.google.firebase.auth.FirebaseAuth.AuthStateListener {
        // User signs in/out — refresh Screen and (re)start observers ako je user prisutan.
        // Sign-out treba da resetuje focusedMemberUid: bez toga anchor drži uid koji
        // više nije u listi, sledeći render bi tražio non-existent member.
        if (it.currentUser == null) focusedMemberUid = null
        invalidate()
        if (it.currentUser != null && collectorJob == null) {
            startObserving()
        }
    }

    init {
        startObserving()
        auth.addAuthStateListener(authListener)
        lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) {
                auth.removeAuthStateListener(authListener)
                collectorJob?.cancel()
                scope.cancel()
            }
        })
    }

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()
        if (auth.currentUser == null) {
            listBuilder.setNoItemsMessage(carContext.getString(R.string.car_signin_required))
        } else if (!loaded) {
            listBuilder.setNoItemsMessage(carContext.getString(R.string.car_loading))
        } else if (members.isEmpty() && places.isEmpty()) {
            listBuilder.setNoItemsMessage(carContext.getString(R.string.car_no_members))
        } else {
            val sLat = selfLat
            val sLng = selfLng
            // Prvo članovi (češće relevant), pa Places.
            members.forEach { m ->
                val row = Row.Builder()
                    .setTitle(m.name)
                    .addText(withDistance(m.subtitle, sLat, sLng, m.lat, m.lng))
                if (m.lat != null && m.lng != null) {
                    val place = Place.Builder(CarLocation.create(m.lat, m.lng))
                        .setMarker(
                            PlaceMarker.Builder()
                                .setColor(CarColor.BLUE)
                                .build(),
                        )
                        .build()
                    row.setMetadata(Metadata.Builder().setPlace(place).build())
                    // Klik = centriraj mapu na tog člana. Različito od Places (dole)
                    // koje pokreću navigaciju jer su mesta statična; članovi se pomeraju
                    // pa je „gde je sada" korisnije od „navigate do njega".
                    row.setOnClickListener {
                        focusedMemberUid = m.uid
                        invalidate()
                    }
                    row.setBrowsable(true)
                }
                listBuilder.addItem(row.build())
            }
            places.forEach { p ->
                val place = Place.Builder(CarLocation.create(p.lat, p.lng))
                    .setMarker(
                        PlaceMarker.Builder()
                            .setColor(CarColor.GREEN)
                            .build(),
                    )
                    .build()
                val row = Row.Builder()
                    .setTitle("📍 ${p.name}")
                    .addText(withDistance("${p.radius} m", sLat, sLng, p.lat, p.lng))
                    .setMetadata(Metadata.Builder().setPlace(place).build())
                    .setOnClickListener { launchNavigate(p.lat, p.lng, p.name) }
                    .setBrowsable(true)
                    .build()
                listBuilder.addItem(row)
            }
        }
        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.car_action_refresh))
                    .setOnClickListener {
                        invalidate()
                    }
                    .build(),
            )
            .build()
        val builder = PlaceListMapTemplate.Builder()
            .setTitle(carContext.getString(R.string.car_title))
            .setHeaderAction(Action.APP_ICON)
            .setItemList(listBuilder.build())
            .setActionStrip(actionStrip)
        // Anchor pomera default map center. Kad user klikne na člana, `focusedMemberUid`
        // se postavi + invalidate pozove; sledeći render postavlja anchor na njegovu
        // trenutnu poziciju. Bez ovog, klik nije davao vidljiv efekat na mapi (user bug).
        val anchor = focusedMemberUid?.let { fid ->
            members.firstOrNull { it.uid == fid }?.takeIf { it.lat != null && it.lng != null }
        }
        if (anchor?.lat != null && anchor.lng != null) {
            val anchorPlace = Place.Builder(CarLocation.create(anchor.lat, anchor.lng))
                .setMarker(PlaceMarker.Builder().setColor(CarColor.BLUE).build())
                .build()
            builder.setAnchor(anchorPlace)
        }
        return builder.build()
    }

    private fun startObserving() {
        val selfUid = auth.currentUser?.uid ?: return
        collectorJob?.cancel()
        collectorJob = scope.launch {
            circleRepository.observeMyCircles(selfUid)
                .flatMapLatest { circles ->
                    val active = circles.firstOrNull { it.id == localPrefs.activeCircleIdFlow.value }
                        ?: circles.firstOrNull()
                    if (active == null) flowOf(emptyList())
                    else {
                        val otherUids = (active.memberIds - selfUid).toList()
                        if (otherUids.isEmpty()) flowOf(emptyList())
                        else kotlinx.coroutines.flow.combine(
                            otherUids.map { uid ->
                                kotlinx.coroutines.flow.combine(
                                    locationRepository.observe(uid),
                                    userRepository.observeUser(uid),
                                ) { loc, user ->
                                    CarMember(
                                        uid = uid,
                                        name = user?.displayName?.takeIf { it.isNotBlank() } ?: "Član",
                                        subtitle = loc?.let { l ->
                                            val ageMs = System.currentTimeMillis() - l.updatedAt
                                            val ageMin = (ageMs / 60_000L).coerceAtLeast(0)
                                            val ageStr = when {
                                                ageMin < 1 -> "sada"
                                                ageMin < 60 -> "pre ${ageMin} min"
                                                else -> "pre ${ageMin / 60} h"
                                            }
                                            "$ageStr · GPS ${l.accuracy.toInt()}m"
                                        } ?: "-",
                                        lat = loc?.lat,
                                        lng = loc?.lng,
                                    )
                                }
                            },
                        ) { arr -> arr.toList() }
                    }
                }
                .collectLatest { list ->
                    members = list
                    // Ako je fokusirani član napustio circle (ili nije u novoj listi
                    // posle circle switch-a), skini anchor da ne držimo stale uid.
                    if (focusedMemberUid != null && list.none { it.uid == focusedMemberUid }) {
                        focusedMemberUid = null
                    }
                    loaded = true
                    invalidate()
                }
        }
        // Odvojen observer za Places aktivnog kruga. Koristi isti fallback pattern kao
        // MapViewModel.effectiveActiveCircleId — ako stored activeCircleId ne postoji među
        // user-ovim krugovima (ili je null za fresh user-e koji nikad nisu setActiveCircle
        // pozvali), pada na prvi krug. Bez toga Auto ekran je pokazivao 0 places-a iako je
        // telefon prikazivao members i places normalno.
        scope.launch {
            kotlinx.coroutines.flow.combine(
                circleRepository.observeMyCircles(selfUid),
                localPrefs.activeCircleIdFlow,
            ) { circles, stored ->
                (circles.firstOrNull { it.id == stored } ?: circles.firstOrNull())?.id
            }.flatMapLatest { activeId ->
                if (activeId.isNullOrBlank()) flowOf(emptyList())
                else placeRepository.observePlaces(activeId)
            }.collectLatest { list ->
                places = list
                invalidate()
            }
        }
        // Self location — puni selfLat/selfLng za distance kalkulaciju u onGetTemplate.
        // Koristi isti Firebase Realtime DB feed kao peer-i (LocationTrackingService FGS
        // ga pushuje kad je Krug u foreground-u ili share aktivan). Ako user nema aktivan
        // share, subtitle jednostavno nema distance prefix — graceful degradation.
        scope.launch {
            locationRepository.observe(selfUid).collectLatest { loc ->
                selfLat = loc?.lat
                selfLng = loc?.lng
                invalidate()
            }
        }
    }

    /**
     * Prepend distance-to-target ako imamo sopstvenu lokaciju i target coords, npr.
     * "3.2 km · sada · GPS 25m". Kad nema self fix-a, vraca original subtitle.
     */
    private fun withDistance(
        base: String,
        selfLat: Double?,
        selfLng: Double?,
        tgtLat: Double?,
        tgtLng: Double?,
    ): String {
        if (selfLat == null || selfLng == null || tgtLat == null || tgtLng == null) return base
        val meters = haversineMeters(selfLat, selfLng, tgtLat, tgtLng)
        return "${formatDistance(carContext, meters)} · $base"
    }

    private fun launchNavigate(lat: Double, lng: Double, label: String) {
        val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { carContext.startCarApp(intent) }
            .onFailure { Timber.w(it, "startCarApp navigate failed") }
    }

    private data class CarMember(
        val uid: String,
        val name: String,
        val subtitle: String,
        val lat: Double?,
        val lng: Double?,
    )
}
