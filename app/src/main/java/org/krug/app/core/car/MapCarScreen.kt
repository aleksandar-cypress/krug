package org.krug.app.core.car

import android.content.Intent
import android.net.Uri
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarLocation
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.Metadata
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceMarker
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.PlaceListNavigationTemplate
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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

    init {
        startObserving()
    }

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()
        if (!loaded) {
            listBuilder.setNoItemsMessage(carContext.getString(R.string.car_loading))
        } else if (members.isEmpty() && places.isEmpty()) {
            listBuilder.setNoItemsMessage(carContext.getString(R.string.car_no_members))
        } else {
            // Prvo članovi (češće relevant), pa Places.
            members.forEach { m ->
                val row = Row.Builder()
                    .setTitle(m.name)
                    .addText(m.subtitle)
                if (m.lat != null && m.lng != null) {
                    val place = Place.Builder(CarLocation.create(m.lat, m.lng))
                        .setMarker(
                            PlaceMarker.Builder()
                                .setColor(CarColor.BLUE)
                                .build(),
                        )
                        .build()
                    row.setMetadata(Metadata.Builder().setPlace(place).build())
                    row.setOnClickListener { launchNavigate(m.lat, m.lng, m.name) }
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
                    .addText("${p.radius} m")
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
        // Car App Library 1.3+ pattern: Header objekat umesto individualnih
        // setTitle/setHeaderAction poziva (koji su deprecated). Konsoliduje title +
        // start action u jedan zvanicni header koji Auto host konzistentno renderuje.
        val header = Header.Builder()
            .setTitle(carContext.getString(R.string.car_title))
            .setStartHeaderAction(Action.APP_ICON)
            .build()
        return PlaceListNavigationTemplate.Builder()
            .setHeader(header)
            .setItemList(listBuilder.build())
            .setActionStrip(actionStrip)
            .build()
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
                    loaded = true
                    invalidate()
                }
        }
        // Odvojen observer za Places aktivnog kruga.
        scope.launch {
            localPrefs.activeCircleIdFlow.flatMapLatest { activeId ->
                if (activeId.isNullOrBlank()) flowOf(emptyList())
                else placeRepository.observePlaces(activeId)
            }.collectLatest { list ->
                places = list
                invalidate()
            }
        }
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
