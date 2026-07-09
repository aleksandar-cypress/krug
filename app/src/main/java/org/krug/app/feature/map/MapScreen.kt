package org.krug.app.feature.map

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.compose.foundation.border
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.core.graphics.toColorInt
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.krug.app.core.util.clickHaptic
import org.krug.app.core.util.confirmHaptic
import org.krug.app.core.util.rejectHaptic
import androidx.compose.ui.res.painterResource
import org.krug.app.ui.brand.pressScaleClickable
import timber.log.Timber
import android.view.HapticFeedbackConstants
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxDelicateApi
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.easeTo
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotation
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.OnPointAnnotationClickListener
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createCircleAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolygonAnnotation
import com.mapbox.maps.plugin.annotation.generated.PolygonAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolygonAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPolygonAnnotationManager
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.scalebar.scalebar
import android.view.Gravity
import org.krug.app.R
import org.krug.app.core.location.LocationTrackingService
import org.krug.app.core.util.bearingDegrees
import org.krug.app.core.util.compactLastSeen
import org.krug.app.core.util.formatDistance
import org.krug.app.core.util.haversineMeters
import org.krug.app.core.util.sosRelativeTime
import org.krug.app.feature.circle.CircleIconAssets
import org.krug.app.ui.theme.LogoBlue
import org.krug.app.ui.theme.LogoBlueLight
import org.krug.app.ui.theme.LogoOrange
import org.krug.app.ui.theme.LogoTeal

private const val DEFAULT_LAT = 44.7866 // Belgrade
private const val DEFAULT_LNG = 20.4489
private const val DEFAULT_ZOOM = 12.0
/** Hard cap na photoCache — sprečava unbounded memory rast (svaki avatar može biti par MB). */
private const val MAX_PHOTO_CACHE_ENTRIES = 64
internal val SosRed = Color(0xFFDC2626)
internal val SosRedDark = Color(0xFFB91C1C)
internal val PrivateGray = Color(0xFF9CA3AF)

/**
 * Mapbox circle/text annotation API prima samo string hex boje (ne Compose Color objekat).
 * Konstante derivovane iz odgovarajućih Color/theme tokena radi održavanja jedinstva —
 * ako se brand boja promeni u Color.kt, ovde se update-uje jedanput.
 */
private const val HEX_SOS_RED = "#DC2626"
private const val HEX_PRIVATE_GRAY = "#9CA3AF"
private const val HEX_SELF_BLUE = "#3A86C8" // = LogoBlue

/**
 * Course-up speed threshold (m/s). Iznad ovoga, mapa se rotira po smeru kretanja
 * ("driving mode"). 2.78 m/s = 10 km/h — granica između hodanja (1.4 m/s avg) i vožnje.
 */
private const val COURSE_UP_SPEED_THRESHOLD = 2.78f
private const val HEX_PIN_TEXT = "#1F2937"
private const val HEX_PIN_HALO = "#FFFFFF"
private const val HEX_PULSE_INDIGO = "#818CF8" // = BrandIndigo500
private const val HEX_PULSE_INDIGO_DARK = "#6366F1" // = BrandIndigo600

private fun MemberWithLocation.isPrivate(): Boolean {
    if (isSelf) return false // za sebe ne pokazujemo private mode
    val loc = location ?: return true
    // Privatni mod = eksplicitna pauza. Staleness ima svoj "Poslednje: pre X min"
    // chip; battery-mode intervali (LOW=15min, STILL=20min, LOW_THROTTLED=30min)
    // su normalan rad i ne smeju flipovati peer u privatni mod.
    return loc.paused
}

/**
 * Member je "long offline" ako lokacija nije osvežena 24h+. Razlozi mogu biti: app
 * obrisan, telefon zaglavljen u Doze-u, isključen, no permission, no network. Ne možemo
 * razlikovati bez Cloud Functions-a → klijent-side UI pokazuje korisniku "možda nije
 * aktivan" hint sa opcijom da ga ukloni iz kruga.
 */
private const val LONG_OFFLINE_THRESHOLD_MS = 24L * 60L * 60L * 1000L  // 24h

private fun MemberWithLocation.isLongOffline(now: Long = System.currentTimeMillis()): Boolean {
    if (isSelf) return false
    val updatedAt = location?.updatedAt ?: return false
    if (updatedAt <= 0L) return false
    return (now - updatedAt) > LONG_OFFLINE_THRESHOLD_MS
}

/**
 * Grace period pre nego što UI signalizira offline: 5 minuta. Pokriva kratke WiFi
 * flicker-e (šetnja između AP-eva, ulaz u lift) gde RTDB onDisconnect firira `online=false`
 * ~30s posle stvarnog dropout-a — bez grace-a, member "trepće" između offline i online
 * na svaki mrežni hicu. 5min je dovoljno za realne short disruptions ali dovoljno kratko
 * da user vidi problem kad je stvarno dugotrajno.
 */
// 10min umesto 5min — sa novim heartbeat-om FGS piše updatedAt svakih 3min čak i u Doze-u,
// pa 10min grace je i dalje "sveza" data od pouzdanog FGS-a. Sirok grace period ubija false-
// positive treperenje "online → offline → online" pri kratkim mrežnim prekidima (metro, lift,
// tunel). Ako je stvarno offline duže od 10min, i dalje se prikazuje jasno.
private const val OFFLINE_GRACE_MS = 10L * 60L * 1000L

/**
 * Bottom padding u pixel-ima za flyTo kad je ModalBottomSheet otvoren. ModalBottomSheet
 * (skipPartiallyExpanded=true) zauzima ~55% ekrana. Bez ovog offset-a, Mapbox centrira pin
 * u vizuelnom centru mape → pin završi ispod sheet-a, user ga ne vidi.
 *
 * 1100px je kompromis: dovoljno da pin ostane iznad sheet-a na phone-ovima 1080x2340 (S24),
 * dovoljno malo da ne shift-uje kameru previše na malim ekranima. Bolje bi bilo dinamički
 * čitati sheet visinu, ali Mapbox easeTo/flyTo očekuju sync vrednost pre nego što sheet
 * animacija završi — statička vrednost radi za sve uobičajene resolucije.
 */
private const val MEMBER_SHEET_OFFSET_PX = 1100.0

/**
 * Trenutno offline: server-side onDisconnect handler je označio member-a kao izgubio
 * vezu (~30s posle stvarnog disconnect-a) I nema fresh publish-a u poslednjih 5min.
 * Grace period sprečava false-positive "Van mreže" pri kratkim network flicker-ima —
 * ako klijent reconnect-uje u tih 5min i pošalje nov fix, UI ostaje "online" bez treperenja.
 * Različito od `isPrivate` (deliberatno pauza) i `isLongOffline` (24h+ tišina).
 */
private fun MemberWithLocation.isOffline(now: Long = System.currentTimeMillis()): Boolean {
    if (isSelf) return false
    val loc = location ?: return false
    if (loc.paused) return false // paused ima svoj chip, nije isto
    if (loc.updatedAt <= 0L) return false // nikad nije publikovao — ne pokazuj offline
    if (loc.online) return false
    // online=false ali updatedAt fresh (< 5min): verovatno kratak network flicker,
    // klijent se već reconnected pre nego što bi onDisconnect ušao u effect.
    return (now - loc.updatedAt) > OFFLINE_GRACE_MS
}


@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onOpenCircles: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenReliability: () -> Unit = {},
    onOpenCircleDetail: (circleId: String) -> Unit = {},
    onCreateCircle: () -> Unit = {},
    onJoinByCode: () -> Unit = {},
    onOpenPlacesForCircle: (circleId: String) -> Unit = {},
    onOpenHistory: (uid: String, displayName: String) -> Unit = { _, _ -> },
    onOpenDriving: (uid: String, displayName: String) -> Unit = { _, _ -> },
    viewModel: MapViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activePlaces by viewModel.activePlaces.collectAsStateWithLifecycle()
    val eventsByPlace by viewModel.eventsByPlace.collectAsStateWithLifecycle()
    val autoStatusByUid by viewModel.autoStatusByUid.collectAsStateWithLifecycle()
    val mapStyle by viewModel.mapStyle.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val haptic: () -> Unit = remember(view) {
        { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }
    }
    val mapViewState = remember { MapViewHolder() }
    val photoCache = remember { mutableStateMapOf<String, Bitmap>() }
    var detailUid by remember { mutableStateOf<String?>(null) }
    var detailPlaceId by remember { mutableStateOf<String?>(null) }
    var placePendingDelete by remember { mutableStateOf<org.krug.app.core.places.PlaceModel?>(null) }
    var showCheckInConfirm by remember { mutableStateOf(false) }
    var showEtaPicker by remember { mutableStateOf(false) }
    // One-shot event collector — Toast + haptic za check-in ishod. Haptic razlikuje
    // uspeh (confirm — lagan pozitivan) od neuspeha (reject — dvostruk pulse) tako da
    // user oseti rezultat čak i ako mu je zvuk isključen ili Toast promakao.
    LaunchedEffect(Unit) {
        viewModel.events.collect { evt ->
            when (evt) {
                MapEvent.CheckInSent -> {
                    view.confirmHaptic()
                    android.widget.Toast.makeText(
                        context, R.string.checkin_toast_sent, android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
                MapEvent.CheckInFailed -> {
                    view.rejectHaptic()
                    android.widget.Toast.makeText(
                        context, R.string.checkin_toast_failed, android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    // Whats-new modal: prikazuje se jednom po version-code bump-u.
    var showWhatsNew by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val currentVersion = org.krug.app.BuildConfig.VERSION_CODE
        val lastSeen = viewModel.lastSeenWhatsNewVersion()
        if (lastSeen == 0) {
            // Fresh install ili prvi put — ne prikazujemo modal, samo pamtimo trenutni version.
            viewModel.markWhatsNewSeen(currentVersion)
        } else if (lastSeen < currentVersion) {
            showWhatsNew = true
        }
    }
    if (showWhatsNew) {
        WhatsNewDialog(onDismiss = {
            showWhatsNew = false
            viewModel.markWhatsNewSeen(org.krug.app.BuildConfig.VERSION_CODE)
        })
    }

    // Battery-opt re-prompt: kad user skip-uje u onboarding-u ili OS reset-uje flag,
    // FGS ne opstaje na Xiaomi/Samsung agresivnim battery restrikcijama i lokacija ispada.
    // Cooldown 7d — dovoljno da se dokaže vrednost ali ne dosadno.
    var showBatteryPrompt by remember { mutableStateOf(false) }
    LaunchedEffect(showWhatsNew) {
        // Ne pokazuj istovremeno sa whats-new-om — daj mu redosled.
        if (showWhatsNew) return@LaunchedEffect
        val exempt = org.krug.app.core.permissions.PermissionUtils.isIgnoringBatteryOptimizations(context)
        if (!exempt && viewModel.batteryPromptCooldownExpired()) {
            showBatteryPrompt = true
            viewModel.markBatteryPromptShown()
        }
    }
    if (showBatteryPrompt) {
        AlertDialog(
            onDismissRequest = { showBatteryPrompt = false },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.BatteryFull,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            title = { Text(stringResource(R.string.reliability_dialog_title)) },
            text = { Text(stringResource(R.string.reliability_dialog_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showBatteryPrompt = false
                    (context as? android.app.Activity)?.let {
                        org.krug.app.core.permissions.PermissionUtils.openBatteryOptimizationRequest(it)
                    }
                }) {
                    Text(stringResource(R.string.reliability_dialog_open))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatteryPrompt = false }) {
                    Text(stringResource(R.string.reliability_dialog_later))
                }
            },
        )
    }
    // Pending refocus: kad user tapne Osveži, pamtimo (uid, since). Kad stigne
    // sveži location.updatedAt > since za taj uid, automatski flyTo na novu poziciju.
    var pendingRefocus by remember { mutableStateOf<Pair<String, Long>?>(null) }

    // SOS deep-link iz notifikacije — SosNotifier postavlja EXTRA_FOCUS_SOS_UID, MainActivity
    // emituje u SosFocusBus, ovde fokusiramo pin čim member sa tim uid-om ima lokaciju
    // (može da bude pre nego što Firestore + RTDB stignu na first frame, pa collect-ujemo
    // dok god je bus non-null i čekamo članove). Posle obrade consume() resetuje bus.
    val pendingSosFocus by org.krug.app.core.sos.SosFocusBus.pendingUid.collectAsStateWithLifecycle()
    LaunchedEffect(pendingSosFocus, state.members) {
        val uid = pendingSosFocus ?: return@LaunchedEffect
        val member = state.members.firstOrNull { it.uid == uid } ?: return@LaunchedEffect
        val loc = member.location ?: return@LaunchedEffect
        mapViewState.flyTo(loc.lng, loc.lat, sheetOffsetPx = MEMBER_SHEET_OFFSET_PX)
        detailUid = uid
        org.krug.app.core.sos.SosFocusBus.consume()
    }

    // Place focus deep-link — PlacesScreen postavi Focus, ovde flyTo + consume.
    // Zoom se skalira sa radius-om: veći krug = širi pogled da se ceo krug vidi.
    val pendingPlaceFocus by org.krug.app.core.places.PlaceFocusBus.pending.collectAsStateWithLifecycle()
    val pendingPlaceId by org.krug.app.core.places.PlaceFocusBus.pendingId.collectAsStateWithLifecycle()
    // Notif click → PlaceFocusBus.requestById(placeId) → MainActivity → ovde:
    // čekamo da activePlaces sadrži place, pa emit-ujemo standard Focus. Bez ovog,
    // notif klik samo otvara app a mapa ostane na prethodnoj kameri.
    LaunchedEffect(pendingPlaceId, activePlaces) {
        val id = pendingPlaceId ?: return@LaunchedEffect
        val place = activePlaces.firstOrNull { it.id == id } ?: return@LaunchedEffect
        Timber.i("PlaceFocus resolved id=$id → name='${place.name}' lat=${place.lat} lng=${place.lng}")
        org.krug.app.core.places.PlaceFocusBus.request(
            lat = place.lat, lng = place.lng,
            name = place.name, radius = place.radius,
        )
        org.krug.app.core.places.PlaceFocusBus.consumeId()
    }
    LaunchedEffect(pendingPlaceFocus) {
        val focus = pendingPlaceFocus ?: return@LaunchedEffect
        // Čekaj da mapa bude spremna pre flyTo.
        var attempts = 0
        while ((mapViewState.mapView == null || !mapViewState.styleLoaded) && attempts < 100) {
            kotlinx.coroutines.delay(50)
            attempts++
        }
        // Formula: baseline 100m → zoom 16.5, svako 2x radius smanji zoom za 1.
        // 50m → 17.5, 100m → 16.5, 200m → 15.5, 400m → 14.5, 500m → ~14.2.
        val zoom = 16.5 - kotlin.math.log2(focus.radius / 100.0)
        Timber.i("PlaceFocus flyTo: name='${focus.name}' lat=${focus.lat} lng=${focus.lng} zoom=$zoom (waited ${attempts * 50}ms)")
        mapViewState.flyTo(focus.lng, focus.lat, zoom.coerceIn(12.0, 18.0))
        org.krug.app.core.places.PlaceFocusBus.consume()
    }

    // Auto re-fit kamere na vraćanje iz background-a. Bez ovog: user vidi člana u
    // pokretu, lock-uje telefon ili ide na Home, član se kreće dalje. Posle resume-a
    // mapa je na staroj poziciji i pin je van vidnog polja — user mora ručno da klikne
    // Članovi → refresh. Snapshot updatedAt-a po članu na ON_PAUSE; na ON_RESUME poredi
    // sa current, ako je iko update-ovao → fit-to-bounds. Preskoči ako MemberDetailSheet
    // otvoren (easeFollow logika već prati fokusiranog). Ako nije bilo update-a (kratke
    // pauze, screen lock + unlock bez kretanja), ne diraj view da ne uznemiravamo user-a.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val currentStateRef = androidx.compose.runtime.rememberUpdatedState(state)
    val currentDetailUidRef = androidx.compose.runtime.rememberUpdatedState(detailUid)
    DisposableEffect(lifecycle) {
        var snapshotUpdatedAt: Map<String, Long>? = null
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    snapshotUpdatedAt = currentStateRef.value.members.associate {
                        it.uid to (it.location?.updatedAt ?: 0L)
                    }
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    // 1) Self-refresh — kad user otvori Krug, forsiraj svež publish tako
                    //    da članovi kruga odmah vide njegovu lokaciju kao svežu (bez čekanja
                    //    sledećeg FORCE_PUBLISH_INTERVAL_MS ciklusa).
                    org.krug.app.core.location.LocationTrackingService.refreshSelf(context)
                    // 2) Auto-ping stale članova — njihov FGS ako je živ će odgovoriti sa
                    //    svežom lokacijom u par sekundi. Ovo je client-side alternativa za
                    //    Cloud Functions push (koji još nemamo).
                    viewModel.refreshStaleMembers()
                    val snap = snapshotUpdatedAt ?: return@LifecycleEventObserver
                    snapshotUpdatedAt = null
                    if (currentDetailUidRef.value != null) return@LifecycleEventObserver
                    val members = currentStateRef.value.members
                    val anyChanged = members.any { m ->
                        val now = m.location?.updatedAt ?: 0L
                        val before = snap[m.uid] ?: 0L
                        now > before
                    }
                    if (!anyChanged) return@LifecycleEventObserver
                    val points = members.mapNotNull { m ->
                        m.location?.let { Point.fromLngLat(it.lng, it.lat) }
                    }
                    if (points.isNotEmpty()) {
                        Timber.d("Resume re-fit: ${points.size} member(s) updated in background")
                        mapViewState.fitToMembers(points)
                    }
                }
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(pendingRefocus, state.members) {
        val pending = pendingRefocus ?: return@LaunchedEffect
        val (uid, since) = pending
        val loc = state.members.firstOrNull { it.uid == uid }?.location
        if (loc != null && loc.updatedAt > since) {
            mapViewState.flyTo(loc.lng, loc.lat, sheetOffsetPx = MEMBER_SHEET_OFFSET_PX)
            pendingRefocus = null
        }
    }
    // Timeout — ako member nije publish-ovao u 30s (FGS ubijen, no permission), drop.
    LaunchedEffect(pendingRefocus) {
        val pending = pendingRefocus ?: return@LaunchedEffect
        kotlinx.coroutines.delay(30_000)
        if (pendingRefocus == pending) pendingRefocus = null
    }

    // Follow focused member: dok je MemberDetailSheet otvoren, kamera prati svaki nov
    // location update tog člana. Bez ovog, korisnik koji posmatra člana u kretanju (ili
    // se vrati iz background-a posle što se član pomerio) vidi pin koji se izvlači iz
    // vidnog polja — kamera stoji na staroj poziciji dok pin "klizi" dalje.
    //
    // Baseline = updatedAt u trenutku otvaranja sheet-a (taj snapshot je već flyTo-van
    // u onPinClick / onMemberClick). Pratimo samo update-ove POSLE otvaranja, da ne
    // dupliramo onaj initial flyTo. Reset baseline-a kad se detailUid promeni.
    var followBaseline by remember { mutableStateOf<Pair<String, Long>?>(null) }
    LaunchedEffect(detailUid) {
        followBaseline = detailUid?.let { uid ->
            val ts = state.members.firstOrNull { it.uid == uid }?.location?.updatedAt ?: 0L
            uid to ts
        }
    }
    LaunchedEffect(detailUid, state.members) {
        val uid = detailUid ?: return@LaunchedEffect
        val (baseUid, baseTs) = followBaseline ?: return@LaunchedEffect
        if (baseUid != uid) return@LaunchedEffect
        // Refresh path (pendingRefocus) koristi flyTo sa zoom 16.5 — pusti njega da odradi
        // svoj posao kad je aktivan; bez ovog, oba LaunchedEffect-a bi se borila za istu
        // kameru i easeTo (pan-only) bi gazio flyTo zoom.
        if (pendingRefocus?.first == uid) return@LaunchedEffect
        val loc = state.members.firstOrNull { it.uid == uid }?.location ?: return@LaunchedEffect
        if (loc.updatedAt > baseTs) {
            mapViewState.easeFollow(loc.lng, loc.lat)
            followBaseline = uid to loc.updatedAt
        }
    }

    // Initial flyTo na self — radi i kad user nema krugove (state.members prazno) jer
    // koristi state.selfLocation direktno iz FGS publish-a. Bez ovog, novi user koji još
    // nije kreirao/se pridružio krugu ostaje "zaglavljen" na default Belgrade Topčider
    // koordinati dok ne kreira krug. didFlyToSelf flag sprečava ponavljanje pri svakom
    // location update-u (FGS publish-uje na ~30s) — user može da pomera mapu po želji.
    LaunchedEffect(state.selfLocation?.lat, state.selfLocation?.lng) {
        val loc = state.selfLocation ?: return@LaunchedEffect
        Timber.d("FlyTo trigger: lat=${loc.lat}, lng=${loc.lng}, didFlyToSelf=${mapViewState.didFlyToSelf}")
        if (mapViewState.didFlyToSelf) return@LaunchedEffect
        // Ako je user došao preko PlaceFocusBus (klik na Place row → nav pop u Map),
        // pending focus ima prioritet — self flyTo bi override-ovao mesto koje je user
        // hteo da vidi (race: oba LaunchedEffect-a startuju istovremeno, self setCamera
        // je poslednji pa prebrisuje). Skip da PlaceFocus flyTo bude poslednji.
        if (org.krug.app.core.places.PlaceFocusBus.pending.value != null) {
            Timber.d("FlyTo skip self: pending PlaceFocus has priority")
            mapViewState.didFlyToSelf = true
            return@LaunchedEffect
        }
        // Sačekaj i da factory završi (mapView != null) i da style završi load
        // (styleLoaded = true). Bez čekanja na style, flyTo poziv pre style-loaded
        // se "izgubi" — Mapbox queue-uje camera op-ove ali setCamera u factory (Belgrade)
        // dolazi na red KASNIJE i prebrisuje naš flyTo. Otuda user vidi Belgrade i posle
        // log-a "FlyTo: Čačak". setCamera (instant, ne flyTo animacija) jer je ovo "initial
        // jump" — animacija nema smisao kad mapa nikad nije ni prikazana usera.
        var attempts = 0
        while ((mapViewState.mapView == null || !mapViewState.styleLoaded) && attempts < 100) {
            kotlinx.coroutines.delay(50)
            attempts++
        }
        val mv = mapViewState.mapView
        if (mv == null || !mapViewState.styleLoaded) {
            Timber.w("FlyTo skip: mapView=${mv != null} style=${mapViewState.styleLoaded} posle ${attempts * 50}ms")
            return@LaunchedEffect
        }
        // Sekundarni check: ako je PlaceFocus pending stigao dok smo čekali mapView, skip.
        if (org.krug.app.core.places.PlaceFocusBus.pending.value != null) {
            Timber.d("FlyTo skip self (post-wait): pending PlaceFocus has priority")
            mapViewState.didFlyToSelf = true
            return@LaunchedEffect
        }
        Timber.i("setCamera self: lat=${loc.lat}, lng=${loc.lng} (waited ${attempts * 50}ms)")
        mv.mapboxMap.setCamera(
            CameraOptions.Builder()
                .center(Point.fromLngLat(loc.lng, loc.lat))
                .zoom(14.0)
                .build(),
        )
        mapViewState.didFlyToSelf = true
    }

    // Course-up navigation: kada se self kreće brže od 2.78 m/s (10 km/h ≈ vožnja),
    // kamera bearing prati GPS bearing pa je smer kretanja UVEK gore. Kad stane, vraća
    // se na north-up (bearing 0). Bez ovog, mapa stoji u apsolutnoj orijentaciji i
    // user mora ručno da rotira kompasom kad vozi. Threshold 2.78 m/s je standardni
    // "driving vs walking" prag u nav app-ovima.
    val selfBearing = state.selfLocation?.bearing
    val selfSpeed = state.selfLocation?.speed
    LaunchedEffect(selfBearing, selfSpeed) {
        val bearing = selfBearing ?: return@LaunchedEffect
        val speed = selfSpeed ?: return@LaunchedEffect
        if (speed >= COURSE_UP_SPEED_THRESHOLD) {
            mapViewState.rotateBearing(bearing)
        } else {
            // Kad user stane (npr. semafor, parking), reset na north-up. 0f je north.
            mapViewState.rotateBearing(0f)
        }
    }

    DisposableEffect(Unit) {
        LocationTrackingService.start(context)
        onDispose { }
    }

    // MapView lifecycle cleanup — bez ovog, MapView (+ annotations + click listeners +
    // sosRipples map) ostaje u memory kad user navigira sa Map ekrana. Mapbox MapView je
    // teška Android View (drži OpenGL resources, telemetry, style sheets). Bez explicit
    // teardown-a, repeated open/close akumulira deseti MB.
    // Places re-render — na svaku promenu activePlaces prebriši oba manager-a
    // (pin i polygon radius) i dodaj nove annotation-e. Čeka do 5s da factory završi.
    LaunchedEffect(activePlaces, mapViewState) {
        var attempts = 0
        while ((mapViewState.placeManager == null || mapViewState.placeRadiusManager == null) &&
            attempts < 100
        ) {
            kotlinx.coroutines.delay(50)
            attempts++
        }
        val pm = mapViewState.placeManager ?: return@LaunchedEffect
        val prm = mapViewState.placeRadiusManager ?: return@LaunchedEffect
        pm.deleteAll()
        prm.deleteAll()
        mapViewState.annotationToPlaceId.clear()
        if (activePlaces.isEmpty()) return@LaunchedEffect
        activePlaces.forEach { place ->
            val (colorHex, _) = MapMarkers.categoryStyle(place.category)
            // 1) Radius polygon — 64-strani polygon aproksimacija kruga u boji kategorije.
            val ring = buildGeoCircle(place.lat, place.lng, place.radius.toDouble(), 64)
            prm.create(
                PolygonAnnotationOptions()
                    .withPoints(listOf(ring))
                    .withFillColor(colorHex)
                    .withFillOpacity(0.15),
            )
            // 2) Pin (per-category bitmap) sa imenom ispod.
            val annotation = pm.create(
                PointAnnotationOptions()
                    .withPoint(Point.fromLngLat(place.lng, place.lat))
                    .withIconImage(MapMarkers.placeMarker(context, place.category))
                    .withIconOffset(listOf(0.0, -21.0))
                    .withTextField(place.name)
                    .withTextOffset(listOf(0.0, 0.6))
                    .withTextSize(12.0)
                    .withTextColor("#1F2937")
                    .withTextHaloColor("#FFFFFF")
                    .withTextHaloWidth(1.5),
            )
            mapViewState.annotationToPlaceId[annotation.id] = place.id
        }
    }

    // ETA destination pin-ovi — svoj + tuđi (state.myEtaShare + state.otherEtaShares).
    // Re-render se okida kad se lista promeni ili ETA update-uje. Pin izgleda kao OTHER
    // Place marker sa labelom „→ {label} ({eta}min)" — dovoljno distinktan a ne treba nov asset.
    LaunchedEffect(state.myEtaShare, state.otherEtaShares, mapViewState) {
        var attempts = 0
        while (mapViewState.etaDestManager == null && attempts < 100) {
            kotlinx.coroutines.delay(50)
            attempts++
        }
        val em = mapViewState.etaDestManager ?: return@LaunchedEffect
        em.deleteAll()
        val shares = listOfNotNull(state.myEtaShare) + state.otherEtaShares
        shares.forEach { share ->
            if (share.arrivedAt != null) return@forEach
            val label = share.destinationLabel.ifBlank { "→" }
            val displayLabel = if (share.userName.isNotBlank()) {
                "→ ${share.userName}: $label (${share.etaMinutes}m)"
            } else {
                "→ $label (${share.etaMinutes}m)"
            }
            em.create(
                PointAnnotationOptions()
                    .withPoint(Point.fromLngLat(share.destinationLng, share.destinationLat))
                    .withIconImage(MapMarkers.destinationMarker(context))
                    .withIconOffset(listOf(0.0, -21.0))
                    .withTextField(displayLabel)
                    .withTextOffset(listOf(0.0, 0.6))
                    .withTextSize(11.0)
                    .withTextColor("#1F2937")
                    .withTextHaloColor("#FFFFFF")
                    .withTextHaloWidth(1.5),
            )
        }
    }

    DisposableEffect(mapViewState) {
        onDispose {
            // 1) Skloni click listener (drži referencu na onPinClick lambda → MapScreen scope).
            mapViewState.onPinClick = null
            // 2) Obriši sve aktivne annotation (pin-ove + ripple krugove) — manageri sami
            //    drže reference na Annotation objekte, GC ih ne čisti dok je manager živ.
            runCatching {
                mapViewState.annotationManager?.deleteAll()
                mapViewState.placeManager?.deleteAll()
                mapViewState.placeRadiusManager?.deleteAll()
                mapViewState.etaDestManager?.deleteAll()
                mapViewState.circleManager?.deleteAll()
            }
            mapViewState.annotationToUid.clear()
            mapViewState.sosRipples.clear()
            // 3) MapView.onDestroy() oslobađa OpenGL kontekst i telemetry kanale.
            //    Posle ovog, mapView referenca je no-op (ne sme se koristiti).
            runCatching { mapViewState.mapView?.onDestroy() }
            mapViewState.mapView = null
            mapViewState.annotationManager = null
            mapViewState.placeManager = null
            mapViewState.placeRadiusManager = null
            mapViewState.etaDestManager = null
            mapViewState.circleManager = null
        }
    }

    // Lazy prompt za Activity Recognition — pokazujemo brand rationale dijalog pre
    // sistemskog dialog-a. Bez ovog, user vidi golu sistemsku poruku "Allow Krug to
    // access physical activity?" bez konteksta zašto. Flag u LocalPrefs sprečava da
    // se rationale ponovo prikazuje pri svakom ulasku u Mapu — pokaže se jednom (allow
    // ili dismiss), posle se može uključiti samo kroz sistemska podešavanja.
    var activityRecRationaleVisible by remember { mutableStateOf(false) }
    val activityRecLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            // Restart FGS da pickup-uje permission i registruje ActivityRecognitionClient.
            LocationTrackingService.start(context)
        }
    }
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
            !org.krug.app.core.permissions.PermissionUtils.hasActivityRecognition(context) &&
            viewModel.shouldShowActivityRecPrompt()
        ) {
            activityRecRationaleVisible = true
        }
    }

    // State: kad user tapne cluster pin (2+ članova), otvorimo chooser sheet sa listom
    // članova iz tog klastera. Solo pin ide direktno na detail (postojeći UX).
    var clusterChooserUids by remember { mutableStateOf<List<String>?>(null) }

    // Click handler za pin — solo: fly-to + otvori MemberDetail. Cluster: otvori chooser.
    DisposableEffect(mapViewState, state.members) {
        mapViewState.onPinClick = { uids ->
            haptic()
            if (uids.size == 1) {
                val uid = uids.first()
                state.members.firstOrNull { it.uid == uid }?.location?.let { loc ->
                    mapViewState.flyTo(loc.lng, loc.lat, sheetOffsetPx = MEMBER_SHEET_OFFSET_PX)
                }
                detailUid = uid
            } else {
                // Cluster — user bira kog od 2+ članova hoće da vidi.
                clusterChooserUids = uids
            }
        }
        onDispose { mapViewState.onPinClick = null }
    }

    // Click handler za Place pin — otvara PlaceDetailSheet.
    DisposableEffect(mapViewState) {
        mapViewState.onPlaceClick = { placeId ->
            haptic()
            detailPlaceId = placeId
        }
        onDispose { mapViewState.onPlaceClick = null }
    }

    // Učitaj profilne fotke (Google sign-in) za sve članove kojima imamo URL.
    // Cache se sad eksplicitno cleanup-uje: zadržavamo SAMO URL-ove vidljivih članova
    // + hard cap od 64 ulaza. Bez ovog, korisnik koji je davno napustio krug i dalje
    // drži bitmap u memory zauvek (svaki bitmap može biti par MB za high-res Google avatar).
    LaunchedEffect(state.members.map { it.photoUrl }) {
        val urls = state.members.mapNotNull { it.photoUrl?.takeIf { u -> u.isNotBlank() } }.toSet()
        // Evict entry-je koji više nisu u trenutnoj listi članova. toList() pravi snapshot
        // pre iteration-a — bez ovog, remove() iznutra mogao bi da zbuni iterator kad
        // mutableStateMapOf interno re-balansira (Compose SnapshotStateMap drži version-e).
        val stale = (photoCache.keys.toSet() - urls).toList()
        stale.forEach { photoCache.remove(it) }
        // Hard cap — odsečemo najstarije (iteration order je insertion order) ako pređemo limit.
        while (photoCache.size > MAX_PHOTO_CACHE_ENTRIES) {
            val oldest = photoCache.keys.toList().firstOrNull() ?: break
            photoCache.remove(oldest)
        }
        urls.forEach { url ->
            if (photoCache[url] != null) return@forEach
            val req = ImageRequest.Builder(context).data(url).allowHardware(false).build()
            val result = ImageLoader(context).execute(req)
            if (result is SuccessResult) {
                (result.drawable as? BitmapDrawable)?.bitmap?.let { photoCache[url] = it }
            }
        }
    }

    var sheetVisible by remember { mutableStateOf(false) }
    var sosConfirmVisible by remember { mutableStateOf(false) }
    var circlePickerVisible by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val activeSosMembers = state.members.filter { it.sos != null }

    // SOS ripple animation — infinite phase 0..1, ~2s ciklus. Drive-uje radar pulse
    // oko SOS markera + glow na in-app banner-u. Kad nema aktivnih SOS-ova, sosPhase je
    // statički 0f — bez ovog, animacija je tikala 60fps i okidala LaunchedEffect svake
    // ms (60 coroutine startup-a/s) iako updateSosRipples nije imao šta da uradi.
    val infiniteTransition = rememberInfiniteTransition(label = "sosRipple")
    val animatedSosPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sosRipplePhase",
    )
    val sosPhase = if (activeSosMembers.isEmpty()) 0f else animatedSosPhase
    // Reset ripples kad lista SOS-ova promeni (per-uid kreiranje/uklanjanje).
    LaunchedEffect(activeSosMembers.map { "${it.uid}:${it.location?.lat}:${it.location?.lng}" }) {
        mapViewState.updateSosRipples(activeSosMembers, sosPhase)
    }
    // Tik animaciju — samo dok ima aktivnih SOS-ova. Bez gate-a, LaunchedEffect re-runs
    // svaku frame čak i u "no SOS" stanju.
    if (activeSosMembers.isNotEmpty()) {
        LaunchedEffect(sosPhase) {
            mapViewState.updateSosRipples(activeSosMembers, sosPhase)
        }
    }

    // Update pulse — kratak vizuelni "tap" oko markera kad mu se ažurira lokacija.
    // Tracking last observed updatedAt per uid; kad se uid-ova vrednost poveća, pokreni
    // one-shot animaciju (suptilan scale + fade). Bez pulse na inicijalnu vrednost.
    // Cleanup: posle obrade, uklonimo UID-ove koji više nisu u members listi (npr.
    // user je napustio krug) — bez ovog, mapa raste sa svakim historijskim članom.
    val lastObservedUpdate = remember { mutableStateMapOf<String, Long>() }
    LaunchedEffect(state.members.map { "${it.uid}:${it.location?.updatedAt ?: 0L}" }) {
        val scope = this
        val activeUids = state.members.map { it.uid }.toSet()
        state.members.forEach { member ->
            val loc = member.location ?: return@forEach
            val prev = lastObservedUpdate[member.uid]
            lastObservedUpdate[member.uid] = loc.updatedAt
            // Inicijalna observacija — nema pulse-a (samo zapamtimo baseline).
            if (prev != null && loc.updatedAt > prev && member.sos == null) {
                scope.launch { mapViewState.runUpdatePulse(loc.lng, loc.lat) }
            }
        }
        // Drop entry-je koji nisu više među aktivnim članovima — sprečava unbounded rast.
        val stale = lastObservedUpdate.keys - activeUids
        stale.forEach { lastObservedUpdate.remove(it) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MapboxContainer(
            members = state.members,
            photoCache = photoCache,
            holder = mapViewState,
            styleUri = mapStyle.styleUri,
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
        ) {
            TopFloatingBar(
                circles = state.myCircles,
                activeCircleId = state.activeCircleId,
                circlesLoaded = state.circlesLoaded,
                onOpenCircles = onOpenCircles,
                onOpenPicker = { circlePickerVisible = true },
                onOpenSettings = onOpenSettings,
                onCreateCircle = onCreateCircle,
                onJoinByCode = onJoinByCode,
            )
            // Permission warning — re-check svaki put kad se app vrati u foreground.
            // Ako je user revokovao FINE_LOCATION u sistemskim Settings-ima dok je app bio
            // u backgroundu, FGS će biti ubijen i mapa ne dobija update. Banner pomaže
            // korisniku da brzo skoči u sistemski settings i vrati permission.
            PermissionWarningBanner(
                onOpenSettings = {
                    (context as? android.app.Activity)?.let {
                        org.krug.app.core.permissions.PermissionUtils.openAppSettings(it)
                    }
                },
            )
            // Self-share broken banner — pojavljuje se KADA je moja lokacija zaustavljena
            // ali ja to ne znam jer sam ja onaj koga ne vide (nema drugi da kaže). Bez
            // ovog, user prosto koristi app misleci da svi drugi vide njegovu poziciju
            // dok Aleksandar poziva na telefon "gde si nestao". Detektuje: FGS ne radi
            // ILI poslednji publish > 10 min stariji.
            SelfShareBrokenBanner(onOpenReliability = onOpenReliability)
            OfflineBanner(
                isOnline = state.isOnline,
                lastUpdatedAt = state.selfLocation?.updatedAt,
            )
            // GPS waiting — pokazuj samo ako imamo permission ali još nema fix-a.
            // Bez permission-a, PermissionWarningBanner gore ionako kaže šta da uradi.
            // Debounce 500ms: na cold start RTDB cache obično emituje fix unutar
            // ~100-300ms; bez debounce-a banner bi blesnuo (fade-in pa odmah fade-out).
            val rawGpsWaiting = state.selfLocation == null &&
                org.krug.app.core.permissions.PermissionUtils.hasForegroundLocation(context)
            var debouncedGpsWaiting by remember { mutableStateOf(false) }
            LaunchedEffect(rawGpsWaiting) {
                if (rawGpsWaiting) {
                    kotlinx.coroutines.delay(500)
                    debouncedGpsWaiting = true
                } else {
                    debouncedGpsWaiting = false
                }
            }
            GpsWaitingBanner(isWaiting = debouncedGpsWaiting)
            PowerSaveBanner(isOnSaver = state.isPowerSaveMode)
            AnimatedVisibility(
                visible = activeSosMembers.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                val activeCircleName = state.myCircles
                    .firstOrNull { it.id == state.activeCircleId }?.name
                Column {
                    Spacer(Modifier.size(12.dp))
                    SosBanner(
                        members = activeSosMembers,
                        selfUid = state.selfUid,
                        circleName = activeCircleName,
                        pulsePhase = sosPhase,
                        onClickMember = { m ->
                            m.location?.let { loc ->
                                mapViewState.flyTo(loc.lng, loc.lat)
                            }
                        },
                        onCancelSelf = {
                            // Laki confirm haptic — user reševa SOS svojevoljno, potvrda
                            // da je action prošla (razlikuje od "digao SOS greškom pa
                            // nastaje panika"). rejectHaptic bi bio previše dramatičan.
                            view.confirmHaptic()
                            viewModel.clearSos()
                        },
                    )
                }
            }
            // ETA share banner — trajno vidljiv dok user aktivno deli ETA. Ispod SOS
            // banner-a (SOS je uvek najviši prioritet).
            state.myEtaShare?.let { share ->
                EtaShareBanner(
                    share = share,
                    onCancel = { viewModel.cancelEtaShare() },
                )
            }
        }

        SosFab(
            active = state.selfSosActive,
            onClick = {
                haptic()
                if (state.selfSosActive) viewModel.clearSos()
                else sosConfirmVisible = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 44.dp),
        )

        // Bez krugova MembersPill nema šta da prikaže — sakriti dok user ne napravi prvi krug.
        // Plus dok se ne učita state, ne renderujemo (sprečava flicker).
        if (state.circlesLoaded && state.myCircles.isNotEmpty()) {
            MembersPill(
                members = state.members,
                photoCache = photoCache,
                onClick = { sheetVisible = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 36.dp),
            )
        }

        if (sheetVisible) {
            ModalBottomSheet(
                onDismissRequest = { sheetVisible = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                MembersSheet(
                    members = state.members,
                    photoCache = photoCache,
                    autoStatusByUid = autoStatusByUid,
                    onMemberClick = { uid ->
                        haptic()
                        sheetVisible = false
                        state.members.firstOrNull { it.uid == uid }?.location?.let { loc ->
                            mapViewState.flyTo(loc.lng, loc.lat, sheetOffsetPx = MEMBER_SHEET_OFFSET_PX)
                        }
                        detailUid = uid
                    },
                )
            }
        }

        // Cluster chooser sheet: kad user tapne pin na kome je 2+ članova (isti auto),
        // filtriramo MembersSheet na tu podgrupu. Bez ovog, cluster klik bi otvarao samo
        // primary člana i drugi bi bili "sakriveni" iza istog pin-a.
        val chooserUids = clusterChooserUids
        if (chooserUids != null) {
            val chooserMembers = state.members.filter { it.uid in chooserUids }
            ModalBottomSheet(
                onDismissRequest = { clusterChooserUids = null },
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                MembersSheet(
                    members = chooserMembers,
                    photoCache = photoCache,
                    autoStatusByUid = autoStatusByUid,
                    onMemberClick = { uid ->
                        haptic()
                        clusterChooserUids = null
                        state.members.firstOrNull { it.uid == uid }?.location?.let { loc ->
                            mapViewState.flyTo(loc.lng, loc.lat, sheetOffsetPx = MEMBER_SHEET_OFFSET_PX)
                        }
                        detailUid = uid
                    },
                )
            }
        }

        val detailMember = detailUid?.let { uid -> state.members.firstOrNull { it.uid == uid } }
        if (detailMember != null) {
            // skipPartiallyExpanded: sheet se odmah otvara na punu visinu. Bez ovog,
            // ModalBottomSheet default-uje na half-expanded pa donja dugmad (View history)
            // ostaju iza fold-a i mogu se accidentalno kliknuti dok se sheet zatvara.
            val detailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { detailUid = null },
                sheetState = detailSheetState,
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                MemberDetailSheet(
                    member = detailMember,
                    selfLocation = state.selfLocation,
                    photo = detailMember.photoUrl?.let { photoCache[it] },
                    autoStatus = autoStatusByUid[detailMember.uid],
                    onOpenInMaps = {
                        detailMember.location?.let { loc ->
                            val uri = android.net.Uri.parse(
                                "geo:${loc.lat},${loc.lng}?q=${loc.lat},${loc.lng}(${detailMember.displayName})",
                            )
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                            runCatching { context.startActivity(intent) }
                        }
                    },
                    onRefresh = {
                        haptic()
                        // 1) Odmah flyTo na poslednju poznatu poziciju člana — user vidi
                        //    da je akcija registrovana, čak i ako nova lokacija stigne za
                        //    nekoliko sekundi. Bez ovog, kamera ne reaguje na tap dok ne
                        //    stigne refresh odgovor (može da deluje kao da dugme ne radi).
                        detailMember.location?.let { loc ->
                            mapViewState.flyTo(loc.lng, loc.lat, sheetOffsetPx = MEMBER_SHEET_OFFSET_PX)
                        }
                        // 2) Baseline = CURRENT updatedAt — kad stigne fresh fix sa novim
                        //    server timestamp-om, LaunchedEffect(pendingRefocus, members)
                        //    automatski flyTo na NOVU poziciju (u slučaju da se član kreće).
                        val baseline = detailMember.location?.updatedAt ?: 0L
                        pendingRefocus = detailMember.uid to baseline
                        if (detailMember.isSelf) {
                            LocationTrackingService.refreshSelf(context)
                        } else {
                            viewModel.refreshMember(detailMember.uid)
                        }
                    },
                    onOpenHistory = {
                        detailUid = null
                        onOpenHistory(detailMember.uid, detailMember.displayName)
                    },
                    onOpenDriving = {
                        detailUid = null
                        onOpenDriving(detailMember.uid, detailMember.displayName)
                    },
                    onCheckIn = if (detailMember.isSelf) {
                        {
                            haptic()
                            showCheckInConfirm = true
                        }
                    } else null,
                    onShareEta = if (detailMember.isSelf) {
                        {
                            haptic()
                            detailUid = null
                            showEtaPicker = true
                        }
                    } else null,
                    fetchRoadDistanceKm = { fromLat, fromLng, toLat, toLng ->
                        viewModel.roadDistanceKm(fromLat, fromLng, toLat, toLng)
                    },
                )
            }
        }
        if (showCheckInConfirm) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showCheckInConfirm = false },
                title = { Text(stringResource(R.string.checkin_confirm_title)) },
                text = { Text(stringResource(R.string.checkin_confirm_body)) },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            showCheckInConfirm = false
                            detailUid = null
                            viewModel.sendCheckIn()
                        },
                    ) {
                        Text(stringResource(R.string.checkin_confirm_cta))
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { showCheckInConfirm = false },
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }
        if (showEtaPicker) {
            EtaDestinationPicker(
                onDismiss = { showEtaPicker = false },
                onSelect = { lat, lng, label ->
                    showEtaPicker = false
                    viewModel.startEtaShare(lat, lng, label)
                },
                onSearch = { query -> viewModel.searchDestinations(query) },
            )
        }

        val detailPlace = detailPlaceId?.let { id -> activePlaces.firstOrNull { it.id == id } }
        if (detailPlace != null) {
            val creator = state.members.firstOrNull { it.uid == detailPlace.createdBy }?.displayName
                .orEmpty()
            val lastEvent = eventsByPlace[detailPlace.id]
            PlaceDetailSheet(
                place = detailPlace,
                creatorName = creator,
                lastEvent = lastEvent,
                onDismiss = { detailPlaceId = null },
                onEdit = {
                    val activeCid = state.activeCircleId
                    detailPlaceId = null
                    if (activeCid != null) onOpenPlacesForCircle(activeCid)
                },
                onDelete = {
                    placePendingDelete = detailPlace
                    detailPlaceId = null
                },
                onToggleMute = { muted -> viewModel.togglePlaceMute(detailPlace.id, muted) },
            )
        }

        placePendingDelete?.let { p ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { placePendingDelete = null },
                title = { Text(stringResource(R.string.places_delete_confirm_title)) },
                text = { Text(stringResource(R.string.places_delete_confirm_msg)) },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            viewModel.deletePlace(p.id)
                            placePendingDelete = null
                        },
                    ) {
                        Text(
                            stringResource(R.string.places_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { placePendingDelete = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }

        if (circlePickerVisible && state.myCircles.isNotEmpty()) {
            ModalBottomSheet(
                onDismissRequest = { circlePickerVisible = false },
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                CirclePickerSheet(
                    circles = state.myCircles,
                    activeCircleId = state.activeCircleId,
                    onPick = { id ->
                        // Bez haptic-a — promena aktivnog kruga je sekundarna navigacija
                        // (svakodnevna akcija), za razliku od SOS-a / member detail-a.
                        viewModel.setActiveCircle(id)
                        circlePickerVisible = false
                    },
                    onOpenDetail = { id ->
                        circlePickerVisible = false
                        onOpenCircleDetail(id)
                    },
                    onCreateNew = {
                        circlePickerVisible = false
                        onOpenCircles()
                    },
                )
            }
        }

        if (sosConfirmVisible) {
            SosConfirmDialog(
                onDismiss = { sosConfirmVisible = false },
                onConfirm = {
                    // Jak haptik na SOS trigger — irreversible action, korisnik treba da
                    // OSETI da je SOS poslat. REJECT/LONG_PRESS je jači od CONTEXT_CLICK.
                    view.rejectHaptic()
                    viewModel.triggerSos()
                    // Boost FGS na BURST profil 30min — peers prate najsvežiju lokaciju
                    // tokom hitne situacije.
                    LocationTrackingService.triggerSosBoost(context)
                    sosConfirmVisible = false
                },
            )
        }

        if (activityRecRationaleVisible) {
            ActivityRecognitionRationaleDialog(
                onAllow = {
                    activityRecRationaleVisible = false
                    viewModel.markActivityRecPromptShown()
                    activityRecLauncher.launch(android.Manifest.permission.ACTIVITY_RECOGNITION)
                },
                onDismiss = {
                    activityRecRationaleVisible = false
                    viewModel.markActivityRecPromptShown()
                },
            )
        }
    }
}


/**
 * "Frosted glass" pill — translucent white + suptilan inner highlight + jak shadow.
 * Iznad colorful Mapbox-a daje vizuelni utisak frosted glass-a bez stvarnog
 * backdrop blur-a (koji bi tražio dodatnu lib kao haze).
 */
private fun Modifier.krugGlass(shape: Shape): Modifier = this
    .shadow(elevation = 14.dp, shape = shape, clip = false)
    .clip(shape)
    .background(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.82f),
                Color.White.copy(alpha = 0.72f),
            ),
        ),
    )
    .border(
        width = 1.dp,
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.65f),
                Color.White.copy(alpha = 0.15f),
            ),
        ),
        shape = shape,
    )

@Composable
private fun TopFloatingBar(
    circles: List<CircleBrief>,
    activeCircleId: String?,
    circlesLoaded: Boolean,
    onOpenCircles: () -> Unit,
    onOpenPicker: () -> Unit,
    onOpenSettings: () -> Unit,
    onCreateCircle: () -> Unit,
    onJoinByCode: () -> Unit,
) {
    val active = circles.firstOrNull { it.id == activeCircleId } ?: circles.firstOrNull()
    val pillLabel = active?.name ?: stringResource(R.string.map_title)
    val pillShape = RoundedCornerShape(28.dp)

    // Empty state CTA samo kada smo SIGURNI da nema krugova (Firestore snapshot stigao).
    if (circlesLoaded && circles.isEmpty()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .shadow(elevation = 16.dp, shape = pillShape, clip = false)
                        .clip(pillShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(LogoBlue, LogoBlueLight),
                            ),
                        )
                        .pressScaleClickable(onClick = onCreateCircle)
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.map_create_first_circle),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                    )
                }
                CircleIconButton(
                    icon = Icons.Outlined.Settings,
                    description = stringResource(R.string.settings_title),
                    onClick = onOpenSettings,
                )
            }
            // Secondary path — user koji je dobio kod ide direktno na EnterCode.
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.85f),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .pressScaleClickable(onClick = onJoinByCode),
            ) {
                Text(
                    text = stringResource(R.string.map_have_invite_cta),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        return
    }

    val onPillClick: () -> Unit = onOpenPicker

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .krugGlass(pillShape)
                .clickable(onClick = onPillClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            // Aktivni krug — boja + ikonica u krugu (avatar-stil chip) levo od imena.
            if (active != null) {
                val accent = Color(android.graphics.Color.parseColor(active.colorHex))
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = CircleIconAssets.forKey(active.iconKey),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = pillLabel,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircleLogoButton(
                description = stringResource(R.string.map_action_circles),
                onClick = onOpenCircles,
            )
            CircleIconButton(
                icon = Icons.Outlined.Settings,
                description = stringResource(R.string.settings_title),
                onClick = onOpenSettings,
            )
        }
    }
}

@Composable
private fun CircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    // 360° spin na svaki tap. Tween 600ms FastOutSlowIn. Navigation delay 250ms — bez
    // ovog, screen transition (280ms) starts immediately i animacija nestaje sa screen-om
    // pre nego što stigne da bude vidljiva. Sa 250ms delay-em user vidi ~150° rotacije
    // pre nego što ekran krene, pa još malo tokom samog transition-a.
    var spinTrigger by remember { mutableIntStateOf(0) }
    val rotation by animateFloatAsState(
        targetValue = spinTrigger * 360f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "icon-spin",
    )
    val scope = rememberCoroutineScope()
    Box(
        modifier = Modifier
            .size(48.dp)
            .krugGlass(CircleShape)
            .clickable {
                spinTrigger += 1
                scope.launch {
                    kotlinx.coroutines.delay(250L)
                    onClick()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.rotate(rotation),
        )
    }
}

/**
 * Identičan layout kao [CircleIconButton] ali sadrži brand logo (color-aware VectorDrawable)
 * umesto monohromatske Material ikone. Logo u 48dp glass-button-u predstavlja "Krugovi" akciju
 * — vizuelno konzistentno sa splash-om i AuthScreen-om.
 */
@Composable
private fun CircleLogoButton(
    description: String,
    onClick: () -> Unit,
) {
    var spinTrigger by remember { mutableIntStateOf(0) }
    val rotation by animateFloatAsState(
        targetValue = spinTrigger * 360f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "logo-spin",
    )
    val scope = rememberCoroutineScope()
    Box(
        modifier = Modifier
            .size(48.dp)
            .krugGlass(CircleShape)
            .clickable {
                spinTrigger += 1
                scope.launch {
                    kotlinx.coroutines.delay(250L)
                    onClick()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_krug_logo),
            contentDescription = description,
            modifier = Modifier
                .size(36.dp)
                .rotate(rotation),
        )
    }
}

@Composable
private fun SosFab(
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Active SOS = solid crveni (signal urgency). Inactive = glass (suptilno, ne ometa).
    val activeModifier = Modifier
        .shadow(elevation = 14.dp, shape = CircleShape, clip = false)
        .clip(CircleShape)
        .background(SosRedDark)
    // Semantic label prati state: TalkBack user čuje jasan opis akcije koju klik radi
    // (trigger vs cancel), ne samo tekst "SoS" iz Text-a.
    val semanticLabel = stringResource(
        if (active) R.string.map_sos_button_active_cd
        else R.string.map_sos_button_cd,
    )
    Box(
        modifier = modifier
            .size(48.dp)
            .then(if (active) activeModifier else Modifier.krugGlass(CircleShape))
            // pressScaleClickable umesto clickable — daje press-feedback identičan drugim
            // CTA-ovima u app-u, plus role=Button za TalkBack. Kritični action treba OSETI
            // i vizuelno (scale) i tactilno (haptic okinut u onClick lambda).
            .pressScaleClickable(pressedScale = 0.92f, onClick = onClick)
            .semantics { contentDescription = semanticLabel },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.map_sos_button),
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
            ),
            color = if (active) Color.White else SosRed,
        )
    }
}

@Composable
private fun SosBanner(
    members: List<MemberWithLocation>,
    selfUid: String?,
    circleName: String?,
    pulsePhase: Float,
    onClickMember: (MemberWithLocation) -> Unit,
    onCancelSelf: () -> Unit,
) {
    val context = LocalContext.current
    val others = members.filter { it.uid != selfUid }
    // Pulse 0..1 (cosine) — drži glow ritmičan umesto sawtooth linearnog flicker-a.
    // Sinhron sa map ripple animacijom (isti pulsePhase iz rememberInfiniteTransition).
    val pulseStrength = (1f - kotlin.math.cos(pulsePhase * 2f * Math.PI).toFloat()) / 2f
    val glowDp = (8f + 14f * pulseStrength).dp

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = glowDp,
                shape = RoundedCornerShape(22.dp),
                clip = false,
                spotColor = SosRed,
                ambientColor = SosRed,
            ),
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(SosRed, SosRedDark),
                    ),
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Column {
                // Header — 🆘 icon u semitransparent krugu + naslov + subtitle.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "🆘",
                            fontSize = 22.sp,
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        val titleText = when {
                            others.size == 1 -> {
                                val name = others.first().displayName.ifBlank { stringResource(R.string.member_label_circle_member) }
                                stringResource(R.string.map_sos_banner_one_help, name)
                            }
                            others.size > 1 -> pluralStringResource(
                                R.plurals.map_sos_banner_multi_help,
                                others.size,
                                others.size,
                            )
                            else -> stringResource(R.string.map_sos_self_active)
                        }
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                            color = Color.White,
                        )
                        // Subtitle = krug + vreme od najsvežijeg SOS-a. Najsvežiji jer ako
                        // ima više SOS-ova, najnoviji je najrelevantniji "pre X min" marker.
                        val freshestSosAt = members.maxOfOrNull { it.sos?.triggeredAt ?: 0L } ?: 0L
                        val timeLabel = sosRelativeTime(context, freshestSosAt)
                        val circleLabel = if (!circleName.isNullOrBlank()) {
                            stringResource(R.string.map_sos_banner_circle_label, circleName)
                        } else ""
                        val subtitle = buildString {
                            if (circleLabel.isNotEmpty()) append(circleLabel)
                            if (timeLabel.isNotBlank()) {
                                if (isNotEmpty()) append(" · ")
                                append(timeLabel)
                            }
                        }
                        if (subtitle.isNotEmpty()) {
                            Spacer(Modifier.size(2.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f),
                            )
                        }
                    }
                }

                Spacer(Modifier.size(14.dp))

                members.forEachIndexed { index, m ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.18f))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Avatar — beli krug sa inicijalima u crvenoj (high contrast na
                        // crvenoj pozadini banner-a). Bolje od generic Person ikone.
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = MapMarkers.computeInitials(
                                    m.displayName.ifBlank { if (m.uid == selfUid) stringResource(R.string.member_label_you) else "?" },
                                ),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = SosRedDark,
                            )
                        }
                        Spacer(Modifier.size(12.dp))
                        val youLabel = stringResource(R.string.member_label_you)
                        val memberLabel = stringResource(R.string.member_label_circle_member)
                        val label = if (m.uid == selfUid) youLabel else m.displayName.ifBlank { memberLabel }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = Color.White,
                            modifier = Modifier.weight(1f),
                        )
                        if (m.uid == selfUid) {
                            TextButton(
                                onClick = onCancelSelf,
                            ) {
                                Text(
                                    text = stringResource(R.string.action_cancel),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        } else {
                            // FilledTonalButton — beli pill sa crvenim tekstom, pop-uje
                            // protiv crvene gradient pozadine. CTA očigledniji od starog
                            // TextButton-a.
                            androidx.compose.material3.FilledTonalButton(
                                onClick = { onClickMember(m) },
                                colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color.White,
                                    contentColor = SosRedDark,
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.action_show),
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                    if (index < members.lastIndex) Spacer(Modifier.size(8.dp))
                }
            }
        }
    }
}

// sosRelativeTime — preseljen u core.util.Time radi unit testabilnosti.

@Composable
private fun MembersPill(
    members: List<MemberWithLocation>,
    photoCache: Map<String, Bitmap>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(28.dp)
    val avatarSize = 26.dp
    val overlap = 9.dp
    val maxVisible = 3
    val visible = members.take(maxVisible)
    val extra = (members.size - maxVisible).coerceAtLeast(0)

    Row(
        modifier = modifier
            .krugGlass(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.map_members_sheet_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (members.isEmpty()) {
            // Krug bez članova — mini empty state.
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(1.5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Group,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
        } else {
            // Avatari sa 30% preklapanjem + beli border (kao iOS Find My / WhatsApp grupe).
            Row(horizontalArrangement = Arrangement.spacedBy(-overlap)) {
                visible.forEachIndexed { index, member ->
                    Box(modifier = Modifier.zIndex((maxVisible - index).toFloat())) {
                        MemberMiniAvatar(
                            member = member,
                            photoCache = photoCache,
                            size = avatarSize,
                        )
                    }
                }
                if (extra > 0) {
                    Box(modifier = Modifier.zIndex(0f)) {
                        Box(
                            modifier = Modifier
                                .size(avatarSize)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .border(1.5.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "+$extra",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberMiniAvatar(
    member: MemberWithLocation,
    photoCache: Map<String, Bitmap>,
    size: androidx.compose.ui.unit.Dp,
) {
    val markerColor = when {
        member.sos != null -> SosRed
        member.isSelf -> MaterialTheme.colorScheme.primary
        else -> Color(android.graphics.Color.parseColor(MapMarkers.colorForUid(member.uid)))
    }
    val photo = member.photoUrl?.let { photoCache[it] }
    // SOS active → suptilan crveni pulsing border (povezuje se sa SOS feature-om).
    val borderColor = if (member.sos != null) {
        val infinite = rememberInfiniteTransition(label = "sosBorder")
        val alpha by infinite.animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 700, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "sosBorderAlpha",
        )
        SosRed.copy(alpha = alpha)
    } else {
        Color.White
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(markerColor)
            .border(1.5.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            androidx.compose.foundation.Image(
                bitmap = photo.asImageBitmap(),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        } else {
            Text(
                text = MapMarkers.computeInitials(member.displayName),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
        }
    }
}

@Composable
private fun MapboxContainer(
    members: List<MemberWithLocation>,
    photoCache: Map<String, Bitmap>,
    holder: MapViewHolder,
    styleUri: String,
) {
    val context = LocalContext.current
    // Pre-resolve strings here (Composable scope) — AndroidView update lambda nije
    // @Composable pa ne može direktno da poziva stringResource() iz petlje.
    val labelYou = stringResource(R.string.member_label_you)
    val labelMember = stringResource(R.string.member_label_member)

    // Kad se style pref promeni (Settings → Map style), reload style na postojeći MapView.
    // MapView nije disposed kad user ide u Settings pa faktori ne fajruje ponovo. Bez
    // ovog LaunchedEffect-a, novi izbor bi bio primenjen tek pri sledećem cold start-u.
    androidx.compose.runtime.LaunchedEffect(styleUri) {
        val mv = holder.mapView ?: return@LaunchedEffect
        if (holder.currentStyleUri == styleUri) return@LaunchedEffect
        holder.currentStyleUri = styleUri
        holder.styleLoaded = false
        mv.location.updateSettings {
            enabled = false
            pulsingEnabled = false
        }
        mv.location.enabled = false
        mv.mapboxMap.loadStyle(styleUri) {
            mv.location.updateSettings {
                enabled = false
                pulsingEnabled = false
            }
            mv.location.enabled = false
            holder.styleLoaded = true
        }
    }

    androidx.compose.ui.viewinterop.AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            MapView(ctx).also { mv ->
                holder.mapView = mv
                // SOS ripple manager mora biti kreiran PRE pin annotation manager-a — circle
                // se render-uje ispod pinova jer je dodat prvi u layer stack-u Mapbox-a.
                holder.circleManager = mv.annotations.createCircleAnnotationManager()
                // Place radius polygon-i — PRE svih pin-ova (dno stack-a, ispod svega).
                holder.placeRadiusManager = mv.annotations.createPolygonAnnotationManager()
                // Place marker manager — iznad radius polygon-a, ispod member pin-ova.
                holder.placeManager = mv.annotations.createPointAnnotationManager()
                holder.placeManager?.addClickListener(
                    OnPointAnnotationClickListener { annotation ->
                        val placeId = holder.annotationToPlaceId[annotation.id]
                        if (placeId != null) {
                            holder.onPlaceClick?.invoke(placeId)
                            true
                        } else {
                            false
                        }
                    },
                )
                // ETA destination pin manager — iznad places, ispod member pin-ova
                // (kreiran ovim redom, Mapbox render-uje po insertion order-u).
                holder.etaDestManager = mv.annotations.createPointAnnotationManager()
                val manager = mv.annotations.createPointAnnotationManager()
                holder.annotationManager = manager
                manager.addClickListener(
                    OnPointAnnotationClickListener { annotation ->
                        val uids = holder.annotationToUid[annotation.id]
                        if (uids != null && uids.isNotEmpty()) {
                            holder.onPinClick?.invoke(uids)
                            true
                        } else {
                            false
                        }
                    },
                )
                val density = ctx.resources.displayMetrics.density
                // Compass — fadeWhenFacingNorth = true znači da je nevidljiv dok je mapa
                // poravnata sa severom. Čim user dva-prsta rotira (često slučajno tokom
                // vožnje), compass se pojavljuje gore-desno; tap vraća mapu na sever.
                // Pozicioniran ispod buttonsa (statusBars ~30 + padding 12 + button 48 +
                // spacer ~20 ≈ 110dp ≈ 110px na mdpi; množimo sa density za pravi pixel).
                mv.compass.updateSettings {
                    enabled = true
                    fadeWhenFacingNorth = true
                    position = Gravity.TOP or Gravity.END
                    marginTop = 110f * density
                    marginRight = 12f * density
                    marginBottom = 0f
                    marginLeft = 0f
                }
                // Scale bar — dole-levo, ispod Članovi pill-a (pill je centriran,
                // levi ugao je slobodan). Metric units.
                mv.scalebar.updateSettings {
                    position = Gravity.BOTTOM or Gravity.START
                    marginLeft = 16f * density
                    marginBottom = 8f * density
                    marginTop = 0f
                    marginRight = 0f
                    isMetricUnits = true
                }
                // Initial camera samo dok stil ne učita — bez ovog flash bi bio crn ekran.
                // MapScreen LaunchedEffect overrid-uje ovo na pravu self lokaciju čim
                // styleLoaded postane true. Belgrade ostaje samo ako user NEMA GPS fix
                // (no permission, gašen GPS).
                mv.mapboxMap.setCamera(
                    CameraOptions.Builder()
                        .center(Point.fromLngLat(DEFAULT_LNG, DEFAULT_LAT))
                        .zoom(DEFAULT_ZOOM)
                        .build(),
                )
                // Disable PRE loadStyle — neki style-ovi (Standard) imaju location layer u
                // konfiguraciji koji se aktivira pri load-u, pa ako disable kasnimo posle
                // loadStyle (koji je async), puck se već renderuje. Ovde disable settings
                // čekaju u plugin state-u, pa kad layer pokuša da se aktivira, ne uspe.
                mv.location.updateSettings {
                    enabled = false
                    pulsingEnabled = false
                }
                mv.location.enabled = false
                // Style URI dolazi iz Settings (Map style) preko MapViewModel-a.
                // STANDARD adaptira system theme-u; DARK je forsirano tamna; SATELLITE i
                // OUTDOORS su alternative za korisnika. Ne forsiramo lightPreset — neka
                // korisnik kroz system settings kontroliše ako je STANDARD.
                holder.currentStyleUri = styleUri
                mv.mapboxMap.loadStyle(styleUri) {
                    // Onstyle-loaded callback — re-aplikujemo disable jer style
                    // može da pokuša da re-enable-uje location komponentu kada se load-uje.
                    mv.location.updateSettings {
                        enabled = false
                        pulsingEnabled = false
                    }
                    mv.location.enabled = false
                    // Signal MapScreen LaunchedEffect-u da je sada bezbedno da setCamera-uje
                    // na user-ovu lokaciju. Pre style-loaded, camera op-ovi se mogu "izgubiti"
                    // tako što ih factory's initial setCamera prebrisuje.
                    holder.styleLoaded = true
                }
            }
        },
        update = { _ ->
            val manager = holder.annotationManager ?: return@AndroidView
            // Fingerprint check — preskoči deleteAll+create ako se data nije promenila
            // (recomposition se često događa zbog state-a koji ne utiče na pin-ove).
            // Battery se kvantizuje na 20% bucket-e — sirov 1% promena ne menja prikaz
            // pin-a, a ranije je svaki 1% (npr. 73% → 74%) okidao deleteAll() + recreate
            // svih anotacija (~60ms latency). Bucket: -1 (unknown), 0-19, 20-39, ..., 80-100.
            val fingerprint = members.joinToString("|") { m ->
                val loc = m.location
                val ph = m.photoUrl?.let { photoCache[it] }?.hashCode() ?: 0
                val battBucket = loc?.batteryPct?.let { it / 20 } ?: -1
                "${m.uid}:${loc?.lat ?: ""}:${loc?.lng ?: ""}:$battBucket:${m.sos != null}:${m.displayName}:$ph"
            }
            if (fingerprint == holder.lastFingerprint) return@AndroidView
            holder.lastFingerprint = fingerprint

            manager.deleteAll()
            holder.annotationToUid.clear()
            // Klasterovanje: članovi u istom vozilu se async publikuju lokaciju (30-90s
            // razmak), pa car pomeranje između njihovih fix-eva izgleda kao 2 odvojena
            // pina na mapi iako su fizički zajedno. Merge unutar 100m u jedan pin sa
            // "+N" badge-om — vizuelno jasno da su zajedno. Self ostaje solo (uvek
            // vidljiv sa svojim brand kolor pin-om, ne meša se sa cluster ikoničnošću).
            val (selfSolo, others) = members.partition { it.isSelf }
            val clusters = clusterMembersByProximity(others, thresholdMeters = 100.0)
            val allGroups: List<List<MemberWithLocation>> = selfSolo.map { listOf(it) } + clusters
            allGroups.forEach { group ->
                val primary = group.first()
                val loc = primary.location ?: return@forEach
                val priv = primary.isPrivate()
                val color = when {
                    primary.sos != null -> HEX_SOS_RED
                    priv -> HEX_PRIVATE_GRAY
                    primary.isSelf -> HEX_SELF_BLUE
                    else -> MapMarkers.colorForUid(primary.uid)
                }
                val photo = primary.photoUrl?.let { photoCache[it] }
                val initials = MapMarkers.computeInitials(primary.displayName)
                val label = if (group.size == 1) {
                    primary.displayName
                        .ifBlank { if (primary.isSelf) labelYou else labelMember }
                        .take(18)
                } else {
                    // Cluster label: prva 2 imena spojena zarezom, ako je više — "Ime i N".
                    val names = group.map { it.displayName.ifBlank { labelMember }.take(10) }
                    when (group.size) {
                        2 -> "${names[0]}, ${names[1]}"
                        else -> "${names[0]} i ${group.size - 1}"
                    }
                }
                val batteryPct = primary.location.batteryPct.takeIf { it in 0..100 }
                // Vizuelna gradacija pina po stepenu odsutnosti — isti pattern kao rowAlpha
                // u member listi. 24h+ ghost (0.4), trenutno offline (30s+ disconnect) blaži
                // fade (0.65), online normalno. Bez srednjeg stepena, offline peer i online
                // peer izgledaju identično na mapi.
                val iconOpacity = when {
                    primary.isLongOffline() -> 0.4
                    primary.isOffline() -> 0.65
                    else -> 1.0
                }
                val annotation = manager.create(
                    PointAnnotationOptions()
                        .withPoint(Point.fromLngLat(loc.lng, loc.lat))
                        .withIconImage(MapMarkers.pinMarker(
                            context, color, photo, initials, batteryPct,
                            isSelf = primary.isSelf,
                            clusterExtras = group.size - 1,
                        ))
                        .withIconOpacity(iconOpacity)
                        .withTextField(label)
                        .withTextSize(12.0)
                        .withTextOffset(listOf(0.0, 1.6))
                        .withTextColor(HEX_PIN_TEXT)
                        .withTextHaloColor(HEX_PIN_HALO)
                        .withTextHaloWidth(2.0)
                        .withTextOpacity(iconOpacity),
                )
                // Klik: solo → normal detail sheet. Cluster → chooser sheet sa
                // listom svih članova. onPinClick prima celu listu uid-ova.
                holder.annotationToUid[annotation.id] = group.map { it.uid }
            }
            // FlyTo na self je preseljen u MapScreen LaunchedEffect (vidi
            // `LaunchedEffect(state.selfLocation, ...)`) — radi i kad user nema krugove
            // (members je prazna) jer koristi `state.selfLocation` direktno iz FGS publish-a.
        },
    )
}

/**
 * Grupiše članove koji su geografski blizu (unutar `thresholdMeters`) — wrapper preko
 * generic `clusterByProximity` u core/util/. Koristi se za "putuju u istom autu"
 * scenario: async publish (30-90s razmak) daje 2 pina na različitim mestima iako su
 * fizički zajedno. Cluster ih spaja u 1 pin sa "+N" badge-om.
 */
private fun clusterMembersByProximity(
    members: List<MemberWithLocation>,
    thresholdMeters: Double,
): List<List<MemberWithLocation>> = org.krug.app.core.util.clusterByProximity(
    items = members,
    latLng = { m -> m.location?.let { it.lat to it.lng } },
    thresholdMeters = thresholdMeters,
)

/**
 * Aproksimira krug (u geo-koordinatama, radijus u metrima) sa N-stranim polygon-om.
 * Skalira se pravilno sa zoom-om jer koristi lat/lng.
 * Formula: destination point-a po početnoj tački, bearing-u i distanci (Haversine inverz).
 */
private fun buildGeoCircle(centerLat: Double, centerLng: Double, radiusMeters: Double, segments: Int): List<Point> {
    val earthRadius = 6371000.0
    val latRad = Math.toRadians(centerLat)
    val lngRad = Math.toRadians(centerLng)
    val angularDistance = radiusMeters / earthRadius
    val points = mutableListOf<Point>()
    for (i in 0..segments) {
        val bearing = 2.0 * Math.PI * i / segments
        val lat2 = Math.asin(
            Math.sin(latRad) * Math.cos(angularDistance) +
                Math.cos(latRad) * Math.sin(angularDistance) * Math.cos(bearing),
        )
        val lng2 = lngRad + Math.atan2(
            Math.sin(bearing) * Math.sin(angularDistance) * Math.cos(latRad),
            Math.cos(angularDistance) - Math.sin(latRad) * Math.sin(lat2),
        )
        points.add(Point.fromLngLat(Math.toDegrees(lng2), Math.toDegrees(lat2)))
    }
    return points
}

private class MapViewHolder {
    var mapView: MapView? = null
    var annotationManager: PointAnnotationManager? = null
    var placeManager: PointAnnotationManager? = null
    var placeRadiusManager: PolygonAnnotationManager? = null
    /**
     * Destination pin-ovi za sve aktivne ETA share-ove (svoj + tuđi). Poseban manager
     * (ne reuse placeManager) da lifecycle bude nezavisan — ETA se osvežava svakih 60s,
     * dok su places statični dok user ne edituje.
     */
    var etaDestManager: PointAnnotationManager? = null
    var circleManager: CircleAnnotationManager? = null
    var didFlyToSelf: Boolean = false
    /** True nakon prvog `loadStyle` callback-a — gate za camera op-ove iz LaunchedEffect-a. */
    var styleLoaded: Boolean = false
    /** Trenutno load-ovan style URI. Koristi ga LaunchedEffect(mapStyle) da preskoči redundant reload. */
    var currentStyleUri: String? = null
    // Annotation → list of member uids u tom pin-u. Solo pin = [uid]. Cluster (2+) =
    // all uids. Bez ovog, cluster klik bi opet otvarao samo primary člana bez ikakvog
    // pristupa drugim članovima cluster-a.
    val annotationToUid = mutableMapOf<String, List<String>>()
    val annotationToPlaceId = mutableMapOf<String, String>()
    var onPlaceClick: ((String) -> Unit)? = null
    /** SOS ripple — per-UID circle annotation (radar pulse oko SOS markera). */
    val sosRipples = mutableMapOf<String, CircleAnnotation>()
    var onPinClick: ((List<String>) -> Unit)? = null
    var lastFingerprint: String = ""

    /**
     * flyTo sa opcionim bottom padding-om za scenario kad je ModalBottomSheet otvoren.
     * ModalBottomSheet zauzima ~55% ekrana; bez padding-a, flyTo centrira pin u sredini
     * ekrana što znači da pin završi ispod sheet-a i user ga ne vidi. Sa bottom padding-om
     * (u pikselima), Mapbox pomera visual center gore pa pin ostaje vidljiv u gornjem
     * delu mape iznad sheet-a.
     *
     * Default sheetOffsetPx=0 očuvava postojeće ponašanje za pozive bez sheet-a.
     */
    fun flyTo(lng: Double, lat: Double, zoom: Double = 16.5, sheetOffsetPx: Double = 0.0) {
        val padding = if (sheetOffsetPx > 0.0) {
            EdgeInsets(0.0, 0.0, sheetOffsetPx, 0.0)
        } else null
        mapView?.mapboxMap?.flyTo(
            CameraOptions.Builder()
                .center(Point.fromLngLat(lng, lat))
                .zoom(zoom)
                .apply { if (padding != null) padding(padding) }
                .build(),
            MapAnimationOptions.mapAnimationOptions { duration(1200L) },
        )
    }

    /**
     * Fit kamere na sve dostavljene tačke (self + ne-self članove). Koristi se na
     * ON_RESUME kad se app vrati iz background-a i bar jedan član je u međuvremenu
     * ažurirao lokaciju — bez ovog, mapa ostaje na staroj poziciji i pin "iskoči"
     * iz vidnog polja. Padding računa permission/offline banner-e gore i FAB-ove dole.
     */
    @OptIn(MapboxDelicateApi::class)
    fun fitToMembers(points: List<Point>) {
        val mv = mapView ?: return
        if (!styleLoaded || points.isEmpty()) return
        if (points.size == 1) {
            val p = points.first()
            mv.mapboxMap.easeTo(
                CameraOptions.Builder()
                    .center(p)
                    .zoom(15.0)
                    .build(),
                MapAnimationOptions.mapAnimationOptions { duration(900L) },
            )
            return
        }
        val cam = mv.mapboxMap.cameraForCoordinates(
            coordinates = points,
            camera = CameraOptions.Builder().build(),
            coordinatesPadding = EdgeInsets(140.0, 80.0, 220.0, 80.0),
            maxZoom = 15.0,
            offset = null,
        )
        mv.mapboxMap.easeTo(
            cam,
            MapAnimationOptions.mapAnimationOptions { duration(900L) },
        )
    }

    /**
     * Continuous follow — pan-only easeTo, bez zoom change-a. Koristi se dok je
     * MemberDetailSheet otvoren i član se kreće: na svaki svež location update kamera
     * pomeri se na novu poziciju. flyTo nije pogodan ovde jer pravi arc + zoom reset
     * na svaki update; korisnik koji posmatra člana koji se kreće bi imao agresivnu
     * kameru. easeTo bez zoom-a daje smooth "kamera ga prati" osećaj.
     */
    fun easeFollow(lng: Double, lat: Double) {
        val mv = mapView ?: return
        mv.mapboxMap.easeTo(
            CameraOptions.Builder().center(Point.fromLngLat(lng, lat)).build(),
            MapAnimationOptions.mapAnimationOptions { duration(800L) },
        )
    }

    /**
     * Course-up rotation: postavi camera bearing na [bearing] (stepeni, 0=sever).
     * Kratka animacija (400ms) — daje smooth osećaj rotacije dok kreiranja, ne "skok".
     * Koristi se samo kad je `speed > threshold` (vožnja); kad user stane, pozovi sa 0f
     * da resetuje na north-up.
     */
    fun rotateBearing(bearing: Float) {
        val mv = mapView ?: return
        mv.mapboxMap.easeTo(
            CameraOptions.Builder().bearing(bearing.toDouble()).build(),
            MapAnimationOptions.mapAnimationOptions { duration(400L) },
        )
    }

    /**
     * Kratak "tap" pulse oko markera kad mu lokacija osveži — suptilan vizuelni signal
     * "kreće se / svež update". One-shot, traje ~800ms. Pokreće se iz MapScreen-a kad
     * detektuje povećan updatedAt za uid (LaunchedEffect-om). Self-uid pulse-uje takođe.
     */
    suspend fun runUpdatePulse(lng: Double, lat: Double) {
        val mgr = circleManager ?: return
        val ann = mgr.create(
            CircleAnnotationOptions()
                .withPoint(Point.fromLngLat(lng, lat))
                .withCircleRadius(10.0)
                .withCircleColor(HEX_PULSE_INDIGO)
                .withCircleOpacity(0.55)
                .withCircleStrokeWidth(1.5)
                .withCircleStrokeColor(HEX_PULSE_INDIGO_DARK)
                .withCircleStrokeOpacity(0.65),
        )
        // try/finally — bug: kad se član kreće, location update stigne brzo, LaunchedEffect
        // key se menja, coroutine se cancel-uje pre nego što stigne do `mgr.delete(ann)`.
        // Annotation ostaje na mapi ZAUVEK (vidljiv kao indigo "plavi" krug). User je video
        // niz takvih krugova kao "track" prethodnih pozicija. finally garantuje cleanup čak
        // i pri cancellation-u.
        try {
            val totalMs = 800L
            val steps = 24
            val stepMs = totalMs / steps
            for (i in 1..steps) {
                val phase = i.toFloat() / steps
                ann.circleRadius = 10.0 + 32.0 * phase
                val alpha = 0.55 * (1.0 - phase).coerceAtLeast(0.0)
                ann.circleOpacity = alpha
                ann.circleStrokeOpacity = alpha
                runCatching { mgr.update(ann) }
                kotlinx.coroutines.delay(stepMs)
            }
        } finally {
            runCatching { mgr.delete(ann) }
        }
    }

    /**
     * Radar-stil SOS ripple — krug oko markera koji se širi i nestaje. `phase` ide 0..1
     * (drive-uje ga Compose infiniteTransition u MapScreen-u). Sync-uje za svaki frame:
     *  - kreira CircleAnnotation za novi SOS marker
     *  - ažurira radius/alpha postojećih
     *  - uklanja kad SOS nestane
     */
    fun updateSosRipples(sosMembers: List<MemberWithLocation>, phase: Float) {
        val mgr = circleManager ?: return
        val currentUids = sosMembers.mapNotNull { m -> m.location?.let { m.uid } }.toSet()
        // Skini ripple za UID-ove koji više nemaju aktivan SOS.
        val toRemove = sosRipples.keys - currentUids
        toRemove.forEach { uid ->
            sosRipples.remove(uid)?.let { mgr.delete(it) }
        }
        // 20px → 80px, alpha 0.5 → 0 kroz fazu.
        val radius = 20.0 + 60.0 * phase
        val alpha = 0.5 * (1.0 - phase)
        sosMembers.forEach { m ->
            val loc = m.location ?: return@forEach
            val existing = sosRipples[m.uid]
            if (existing == null) {
                val ann = mgr.create(
                    CircleAnnotationOptions()
                        .withPoint(Point.fromLngLat(loc.lng, loc.lat))
                        .withCircleRadius(radius)
                        .withCircleColor(HEX_SOS_RED)
                        .withCircleOpacity(alpha)
                        .withCircleStrokeWidth(2.0)
                        .withCircleStrokeColor(HEX_SOS_RED)
                        .withCircleStrokeOpacity(alpha),
                )
                sosRipples[m.uid] = ann
            } else {
                existing.point = Point.fromLngLat(loc.lng, loc.lat)
                existing.circleRadius = radius
                existing.circleOpacity = alpha
                existing.circleStrokeOpacity = alpha
                mgr.update(existing)
            }
        }
    }
}

@Composable
private fun CirclePickerSheet(
    circles: List<CircleBrief>,
    activeCircleId: String?,
    onPick: (String) -> Unit,
    onOpenDetail: (String) -> Unit,
    onCreateNew: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.map_circle_picker_title),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 12.dp),
        )
        // weight(1f) na LazyColumn-i + weightless button ispod = button uvek vidljiv
        // bez obzira na broj krugova. Bez ovog, sa 9+ krugova LazyColumn naraste preko
        // dna sheet-a i Manage circles dugme nestane.
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(circles, key = { it.id }) { c ->
                CirclePickerRow(
                    circle = c,
                    selected = c.id == activeCircleId,
                    onPick = { onPick(c.id) },
                    onOpenDetail = { onOpenDetail(c.id) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        androidx.compose.material3.OutlinedButton(
            onClick = onCreateNew,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.map_circle_picker_manage))
        }
    }
}

@Composable
private fun CirclePickerRow(
    circle: CircleBrief,
    selected: Boolean,
    onPick: () -> Unit,
    onOpenDetail: () -> Unit,
) {
    // Boja kruga je centralni vizuelni element — daje svakom redu identitet bez čitanja
    // imena. Selected state je obojeni border + accent pozadina umesto generic
    // primaryContainer (koji se gubi između drugih elemenata u sheet-u).
    val accent = Color(android.graphics.Color.parseColor(circle.colorHex))
    val bgColor = if (selected) accent.copy(alpha = 0.10f)
        else MaterialTheme.colorScheme.surfaceContainerHigh
    val borderColor = if (selected) accent else Color.Transparent
    // Ako je krug već aktivan, `onPick` bi bio no-op (isti krug) — čitav red vodi na
    // detail umesto, pa user ne mora da cilja malu strelicu. Za neaktivne redove,
    // click i dalje prebacuje aktivan krug.
    val rowClick = if (selected) onOpenDetail else onPick
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp),
            )
            .pressScaleClickable(pressedScale = 0.98f, onClick = rowClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Circle icon — colored circle sa icon-om u sredini, isti vizuelni jezik kao
        // chip u TopFloatingBar pill-u. Active state: subtle glow ring oko (accent pri 0.4).
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = CircleIconAssets.forKey(circle.iconKey),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = circle.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (selected) {
                Text(
                    text = stringResource(R.string.map_active_circle),
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        IconButton(onClick = onOpenDetail) {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = stringResource(R.string.map_circle_picker_detail),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MembersSheet(
    members: List<MemberWithLocation>,
    photoCache: Map<String, Bitmap>,
    autoStatusByUid: Map<String, String> = emptyMap(),
    onMemberClick: (String) -> Unit,
) {
    // Sort: self prvi, pa SOS, pa active clanovi po imenu, pa long-offline na kraju.
    // Bez ovog redosled je nepredvidiv (Firestore snapshot order) — user mora da
    // skroluje da bi našao sebe ili člana u nevolji.
    //
    // Zašto NE sortiramo po `updatedAt` u aktivnoj grupi: svaki fresh location publish
    // menja updatedAt, i lista skače (član sa novim fix-om preleti na vrh). Kod 6+ članova
    // to je vizuelni chaos. Umesto toga sortiramo po displayName (stabilan između update-a),
    // a "svežinu" pokazujemo kao subtitle u MemberRow-u (npr. "aktivan pre 2 min").
    // Long-offline (24h+) ide na dno posebno, da ne zauzima prostor pored aktivnih.
    val sortedMembers = members.sortedWith(
        compareByDescending<MemberWithLocation> { it.isSelf }
            .thenByDescending { it.sos != null }
            .thenBy { it.isLongOffline() }
            .thenBy { it.displayName.lowercase() }
            .thenBy { it.uid },
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.map_members_sheet_title),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 12.dp),
        )
        if (members.isEmpty()) {
            Text(
                text = stringResource(R.string.map_members_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(sortedMembers, key = { it.uid }) { m ->
                    MemberRow(
                        member = m,
                        photo = m.photoUrl?.let { photoCache[it] },
                        autoStatus = autoStatusByUid[m.uid],
                        onClick = { onMemberClick(m.uid) },
                    )
                }
                // Hint kad je user sam u krugu — pomaže novom user-u da shvati šta dalje
                // (otvori Detalji kruga → generiši pozivnicu).
                if (members.size == 1 && members.first().isSelf) {
                    item {
                        Spacer(Modifier.size(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                )
                                .padding(16.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.map_members_alone_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberRow(
    member: MemberWithLocation,
    photo: Bitmap?,
    autoStatus: String? = null,
    onClick: () -> Unit = {},
) {
    val markerColor = when {
        member.sos != null -> SosRed
        member.isSelf -> MaterialTheme.colorScheme.primary
        else -> Color(android.graphics.Color.parseColor(MapMarkers.colorForUid(member.uid)))
    }
    // Vizuelna gradacija po stepenu odsutnosti: long-offline (24h+, verovatno uninstalled)
    // najjači fade → offline (server-side disconnect, trenutno u tunelu/dead battery)
    // srednji fade → online punom vidljivošću. Bez ovog, offline peer u listi izgleda isto
    // kao aktivan.
    val isLongOffline = member.isLongOffline()
    val isOffline = member.isOffline()
    val rowAlpha = when {
        isLongOffline -> 0.55f
        isOffline -> 0.72f
        else -> 1f
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .pressScaleClickable(pressedScale = 0.98f, onClick = onClick)
            .padding(14.dp)
            .alpha(rowAlpha),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(markerColor),
            contentAlignment = Alignment.Center,
        ) {
            if (photo != null) {
                androidx.compose.foundation.Image(
                    bitmap = photo.asImageBitmap(),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            } else {
                val initials = MapMarkers.computeInitials(member.displayName)
                if (initials.isNotBlank()) {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = member.displayName.ifBlank { if (member.isSelf) stringResource(R.string.member_label_you) else stringResource(R.string.member_label_member) },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (member.isChild) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Outlined.ChildCare,
                        contentDescription = stringResource(R.string.member_child_cd),
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            val priv = member.isPrivate()
            val offline = member.isOffline()
            val sosLabel = stringResource(R.string.member_state_sos_help)
            val privateLabel = stringResource(R.string.member_state_private)
            val offlineLabel = stringResource(R.string.member_state_offline)
            val statusLine = when {
                member.sos != null -> sosLabel
                priv -> privateLabel
                // Offline uzima prednost nad "last seen" — kaže user-u ZAŠTO nema svežih
                // podataka umesto da čita "pre 5 min" i pita se da li je peer pauzirao.
                offline -> offlineLabel
                else -> lastSeenLabel(member.location?.updatedAt)
            }
            // Ako je displayName već device naziv (anon user bez nicknamea), ne ponavljaj
            // device u status liniji — bila bi duplikacija. AutoStatus (u pokretu / u
            // mestu) ima prednost nad device suffix-om jer je informativniji.
            val suffix = when {
                !autoStatus.isNullOrBlank() && member.sos == null && !priv && !offline -> " · $autoStatus"
                member.deviceModel.isNotBlank() &&
                    member.sos == null &&
                    !priv &&
                    !member.displayName.equals(member.deviceModel, ignoreCase = true) -> " · ${member.deviceModel}"
                else -> ""
            }
            Text(
                text = statusLine + suffix,
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    member.sos != null -> SosRed
                    priv -> PrivateGray
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = if (member.sos != null) FontWeight.Bold else FontWeight.Normal,
            )
        }
        // Privatni mod — bez baterije (podaci su stari/nepouzdani).
        if (!member.isPrivate() && member.location?.batteryPct != null && member.location.batteryPct >= 0) {
            BatteryBadge(pct = member.location.batteryPct, charging = member.location.charging)
        }
    }
}

@Composable
private fun BatteryBadge(pct: Int, charging: Boolean) {
    val color = batteryColor(pct)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.14f),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(
                imageVector = if (charging) Icons.Filled.BatteryChargingFull
                else Icons.Outlined.BatteryFull,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "$pct%",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = color,
            )
        }
    }
}

private fun batteryColor(pct: Int): Color = when {
    pct >= 50 -> LogoTeal // logo teal (zdrava baterija)
    pct >= 20 -> LogoOrange // logo orange (srednja)
    else -> Color(0xFFEF4444) // kritično crvena
}

// haversineMeters / formatDistance — preseljeni u core.util.Geo radi unit testabilnosti.

@Composable
private fun MemberDetailSheet(
    member: MemberWithLocation,
    selfLocation: org.krug.app.core.location.LocationModel?,
    photo: Bitmap?,
    autoStatus: String?,
    onOpenInMaps: () -> Unit,
    onRefresh: () -> Unit,
    onOpenHistory: (() -> Unit)?,
    onOpenDriving: (() -> Unit)?,
    onCheckIn: (() -> Unit)? = null,
    onShareEta: (() -> Unit)? = null,
    fetchRoadDistanceKm: (suspend (Double, Double, Double, Double) -> Double?)? = null,
) {
    // Road distance — Mapbox Directions API poziv pri otvaranju sheet-a. Loading dok
    // stigne (~200-500ms), fallback na haversine sa eksplicitnom „vazdušna" oznakom
    // ako API pukne.
    var roadDistanceKm by remember(member.uid) { mutableStateOf<Double?>(null) }
    var roadDistanceLoaded by remember(member.uid) { mutableStateOf(false) }
    LaunchedEffect(member.uid, selfLocation?.lat, selfLocation?.lng, member.location?.lat, member.location?.lng) {
        if (member.isSelf) return@LaunchedEffect
        if (selfLocation == null || member.location == null) return@LaunchedEffect
        if (fetchRoadDistanceKm == null) return@LaunchedEffect
        roadDistanceLoaded = false
        val km = fetchRoadDistanceKm(
            selfLocation.lat, selfLocation.lng,
            member.location.lat, member.location.lng,
        )
        roadDistanceKm = km
        roadDistanceLoaded = true
    }
    var refreshTriggered by remember { mutableStateOf(false) }
    LaunchedEffect(refreshTriggered) {
        if (refreshTriggered) {
            kotlinx.coroutines.delay(5000)
            refreshTriggered = false
        }
    }
    val markerColor = when {
        member.sos != null -> SosRed
        member.isSelf -> MaterialTheme.colorScheme.primary
        else -> Color(android.graphics.Color.parseColor(MapMarkers.colorForUid(member.uid)))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp)
            .navigationBarsPadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .shadow(elevation = 8.dp, shape = CircleShape, clip = false,
                        ambientColor = markerColor, spotColor = markerColor)
                    .clip(CircleShape)
                    .background(markerColor),
                contentAlignment = Alignment.Center,
            ) {
                if (photo != null) {
                    androidx.compose.foundation.Image(
                        bitmap = photo.asImageBitmap(),
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                } else {
                    Text(
                        text = MapMarkers.computeInitials(member.displayName),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = member.displayName.ifBlank { if (member.isSelf) stringResource(R.string.member_label_you) else stringResource(R.string.member_label_member) },
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (member.isChild) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Outlined.ChildCare,
                            contentDescription = stringResource(R.string.member_child_cd),
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                // Subtitle samo ako je displayName SET-OVANO ime (nije isto što i device).
                if (
                    member.deviceModel.isNotBlank() &&
                    !member.displayName.equals(member.deviceModel, ignoreCase = true)
                ) {
                    Text(
                        text = member.deviceModel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Auto-status pill — "Kod kuće" / "U pokretu 45 km/h". Prikazuje se
                // samo za druge (ne self) i samo ako je fresh location (< 15 min).
                if (!member.isSelf && !autoStatus.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = autoStatus,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }

        val isPrivate = member.isPrivate()
        val isLongOffline = member.isLongOffline()
        val isOffline = member.isOffline()

        // Banner-i za posebna stanja. Standardizovan shape (16dp), padding (16dp), icon size
        // (22dp), spacing između ikone i teksta (12dp). Svaki banner ima svoju „temperature":
        //  - SOS: LogoRed full-bleed, hitno
        //  - LongOffline: PrivateGray stronger (~16% alpha), akcija potrebna
        //  - Offline: LogoOrange tint, tranzientno „javiće se sam"
        //  - Private: PrivateGray light (~12% alpha), neutralno info
        val bannerShape = RoundedCornerShape(16.dp)
        val bannerPadding = 16.dp
        val bannerIconSize = 22.dp
        val bannerIconTextGap = 12.dp
        if (member.sos != null) {
            Spacer(Modifier.height(16.dp))
            Surface(
                shape = bannerShape,
                color = SosRed,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(bannerPadding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(bannerIconSize),
                    )
                    Spacer(Modifier.width(bannerIconTextGap))
                    val sosName = member.displayName
                        .ifBlank { if (member.isSelf) stringResource(R.string.member_label_you) else stringResource(R.string.member_label_member) }
                    Text(
                        text = if (member.isSelf) stringResource(R.string.map_sos_self_active)
                        else stringResource(R.string.map_sos_member_needs_help, sosName),
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        } else if (isLongOffline) {
            Spacer(Modifier.height(16.dp))
            val daysOffline = ((System.currentTimeMillis() - (member.location?.updatedAt ?: 0L)) /
                (24L * 60L * 60L * 1000L)).toInt().coerceAtLeast(1)
            Surface(
                shape = bannerShape,
                color = PrivateGray.copy(alpha = 0.16f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(bannerPadding),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudOff,
                        contentDescription = null,
                        tint = PrivateGray,
                        modifier = Modifier.size(bannerIconSize),
                    )
                    Spacer(Modifier.width(bannerIconTextGap))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.member_state_long_offline_title, daysOffline),
                            color = PrivateGray,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.member_state_long_offline_body),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        } else if (isOffline) {
            // Offline (tranzientno): LogoOrange tint umesto sivog — signalizuje „privremeno,
            // javiće se". Sivi ton je za long-offline (mrtvo stanje, treba akcija).
            Spacer(Modifier.height(16.dp))
            Surface(
                shape = bannerShape,
                color = LogoOrange.copy(alpha = 0.14f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(bannerPadding),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudOff,
                        contentDescription = null,
                        tint = LogoOrange,
                        modifier = Modifier.size(bannerIconSize),
                    )
                    Spacer(Modifier.width(bannerIconTextGap))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.member_state_offline),
                            color = LogoOrange,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.member_state_offline_body),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        } else if (isPrivate) {
            Spacer(Modifier.height(16.dp))
            Surface(
                shape = bannerShape,
                color = PrivateGray.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(bannerPadding),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.VisibilityOff,
                        contentDescription = null,
                        tint = PrivateGray,
                        modifier = Modifier.size(bannerIconSize),
                    )
                    Spacer(Modifier.width(bannerIconTextGap))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.member_state_private),
                            color = PrivateGray,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.member_state_private_body),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        // Fixed 2×2 grid: layout se ne pomera kad član uđe u pokret ili offline.
        // Za druge: Battery+Distance / Speed+Last seen. Za self: Battery+Speed / Last seen (full).
        // Za private mode: samo Last seen full width (baterija/brzina/pozicija su zabludljive).
        if (isPrivate) {
            StatChip(
                label = stringResource(R.string.member_chip_last_seen),
                value = compactLastSeen(LocalContext.current, member.location?.updatedAt),
                accentColor = PrivateGray,
                icon = Icons.Outlined.AccessTime,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            val batt = member.location?.batteryPct
            val charging = member.location?.charging == true
            val speedMps = member.location?.speed ?: 0f
            val kmh = (speedMps * 3.6f).toInt().coerceAtLeast(0)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Slot 1: Battery (uvek za oba, ali placeholder ako nema fresh vrednosti)
                    if (batt != null && batt in 0..100) {
                        StatChip(
                            label = if (charging) stringResource(R.string.member_chip_battery_charging) else stringResource(R.string.member_chip_battery),
                            value = "$batt%",
                            accentColor = batteryColor(batt),
                            icon = if (charging) Icons.Filled.BatteryChargingFull else Icons.Outlined.BatteryFull,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    // Slot 2: Distance za druge, Speed za self. Preferiramo road distance
                    // (Mapbox Directions) kad se učita — realno korisniji signal od vazdušne
                    // linije (razlika ume da bude 30-50% u urbanoj mreži). Fallback:
                    // haversine sa explicit „vazdušna" labelom dok se road ne učita ili ako pukne.
                    if (!member.isSelf && selfLocation != null && member.location != null) {
                        val aerialMeters = haversineMeters(
                            selfLocation.lat, selfLocation.lng,
                            member.location.lat, member.location.lng,
                        )
                        val bearing = if (aerialMeters >= 30.0) {
                            bearingDegrees(
                                selfLocation.lat, selfLocation.lng,
                                member.location.lat, member.location.lng,
                            )
                        } else 0f
                        val roadKm = roadDistanceKm
                        val displayLabel: String
                        val displayValue: String
                        when {
                            !roadDistanceLoaded -> {
                                // Loading — pokazuj haversine sa oznakom "≈" da user zna da nije final.
                                displayLabel = stringResource(R.string.member_chip_distance)
                                displayValue = "≈ " + formatDistance(LocalContext.current, aerialMeters)
                            }
                            roadKm != null -> {
                                displayLabel = stringResource(R.string.member_chip_distance)
                                displayValue = formatDistance(LocalContext.current, roadKm * 1000.0)
                            }
                            else -> {
                                // API pukao — jasno reci da je vazdušna linija.
                                displayLabel = stringResource(R.string.member_chip_distance_aerial)
                                displayValue = formatDistance(LocalContext.current, aerialMeters)
                            }
                        }
                        StatChip(
                            label = displayLabel,
                            value = displayValue,
                            accentColor = MaterialTheme.colorScheme.primary,
                            icon = Icons.Filled.Navigation,
                            iconRotationDeg = bearing,
                            modifier = Modifier.weight(1f),
                        )
                    } else if (member.isSelf) {
                        StatChip(
                            label = stringResource(R.string.member_chip_speed),
                            value = stringResource(R.string.member_chip_speed_value, kmh),
                            accentColor = MaterialTheme.colorScheme.tertiary,
                            icon = Icons.Outlined.Speed,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Row 2: Speed+LastSeen za druge, samo LastSeen (full) za self.
                    if (!member.isSelf) {
                        StatChip(
                            label = stringResource(R.string.member_chip_speed),
                            value = stringResource(R.string.member_chip_speed_value, kmh),
                            accentColor = MaterialTheme.colorScheme.tertiary,
                            icon = Icons.Outlined.Speed,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    StatChip(
                        label = stringResource(R.string.member_chip_last_seen),
                        value = compactLastSeen(LocalContext.current, member.location?.updatedAt),
                        accentColor = MaterialTheme.colorScheme.primary,
                        icon = Icons.Outlined.AccessTime,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        // Layout dugmadi u parovima (dva po redu, weight 1 svakome) umesto stack-a
        // pojedinačnih full-width dugmadi. Kompaktnije, deluje kao pravi action bar.
        //
        // OTHER member:
        //   Row 1: [Refresh]  [Directions icon 64dp]
        //   Row 2: [History]  [Trips]
        //
        // SELF member:
        //   Row 1: [Refresh]  [Check-in]
        //   Row 2: [Share ETA] [History]
        //   Row 3: [Trips]    [Directions icon 64dp]
        val showRefresh = member.isSelf || (!isPrivate && !isLongOffline && member.location != null)
        val buttonHeight = 48.dp
        val buttonShape = RoundedCornerShape(24.dp)
        val hasLocation = member.location != null

        if (member.isSelf) {
            // Row 1 self: Refresh + Check-in.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (showRefresh) {
                    androidx.compose.material3.Button(
                        onClick = {
                            onRefresh()
                            refreshTriggered = true
                        },
                        enabled = !refreshTriggered,
                        shape = buttonShape,
                        modifier = Modifier.weight(1f).height(buttonHeight),
                    ) {
                        Text(
                            if (refreshTriggered) stringResource(R.string.member_refresh_self_done)
                            else stringResource(R.string.member_refresh_self),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (onCheckIn != null) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = onCheckIn,
                        shape = buttonShape,
                        modifier = Modifier.weight(1f).height(buttonHeight),
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.checkin_button_short),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            // Row 2 self: Share ETA + History.
            if (onShareEta != null || onOpenHistory != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (onShareEta != null) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = onShareEta,
                            shape = buttonShape,
                            modifier = Modifier.weight(1f).height(buttonHeight),
                        ) {
                            Icon(Icons.Filled.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.eta_share_title),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (onOpenHistory != null) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = onOpenHistory,
                            shape = buttonShape,
                            modifier = Modifier.weight(1f).height(buttonHeight),
                        ) {
                            Icon(Icons.Outlined.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.history_cta),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            // Row 3 self: Trips + Directions icon.
            if (onOpenDriving != null || hasLocation) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (onOpenDriving != null) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = onOpenDriving,
                            shape = buttonShape,
                            modifier = Modifier.weight(1f).height(buttonHeight),
                        ) {
                            Icon(Icons.Outlined.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.member_driving_cta),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (hasLocation) {
                        androidx.compose.material3.FilledIconButton(
                            onClick = onOpenInMaps,
                            shape = buttonShape,
                            colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                                containerColor = org.krug.app.ui.theme.LogoTeal,
                                contentColor = Color.White,
                            ),
                            modifier = Modifier.size(width = 64.dp, height = buttonHeight),
                        ) {
                            Icon(
                                Icons.Filled.Directions,
                                contentDescription = stringResource(R.string.action_open_in_google_maps),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        } else {
            // OTHER member: Row 1 = Refresh + Directions icon, Row 2 = History + Trips.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (showRefresh) {
                    androidx.compose.material3.Button(
                        onClick = {
                            onRefresh()
                            refreshTriggered = true
                        },
                        enabled = !refreshTriggered,
                        shape = buttonShape,
                        modifier = Modifier.weight(1f).height(buttonHeight),
                    ) {
                        Text(
                            if (refreshTriggered) stringResource(R.string.member_refresh_other_sent)
                            else stringResource(R.string.member_refresh_other),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (hasLocation) {
                    androidx.compose.material3.FilledIconButton(
                        onClick = onOpenInMaps,
                        shape = buttonShape,
                        colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                            containerColor = org.krug.app.ui.theme.LogoTeal,
                            contentColor = Color.White,
                        ),
                        modifier = Modifier.size(width = 64.dp, height = buttonHeight),
                    ) {
                        Icon(
                            Icons.Filled.Directions,
                            contentDescription = stringResource(R.string.action_open_in_google_maps),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
            if (onOpenHistory != null || onOpenDriving != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (onOpenHistory != null) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = onOpenHistory,
                            shape = buttonShape,
                            modifier = Modifier.weight(1f).height(buttonHeight),
                        ) {
                            Icon(Icons.Outlined.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.history_cta),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (onOpenDriving != null) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = onOpenDriving,
                            shape = buttonShape,
                            modifier = Modifier.weight(1f).height(buttonHeight),
                        ) {
                            Icon(Icons.Outlined.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.member_driving_cta),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatChip(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    iconRotationDeg: Float = 0f,
) {
    // Chip pozadina je vrlo blagi tint accent boje (8% alpha) — daje brand prisustvo
    // svakom chip-u bez "vrišti" boje. Border iste boje sa 22% alpha za suptilnu ivicu.
    // iconRotationDeg za "kompas ka drugu" — Distance chip prosleđuje bearing (0=sever)
    // pa Navigation strelica rotira ka peer-u. Animiran spring za smooth rotaciju kad se
    // brzo krećeš (bez ovoga strelica bi "trznula" na svaki fix).
    val animatedRotation by animateFloatAsState(
        targetValue = iconRotationDeg,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
        ),
        label = "chip-icon-rotation",
    )
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(accentColor.copy(alpha = 0.08f))
            .border(
                width = 1.dp,
                color = accentColor.copy(alpha = 0.22f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier
                        .size(16.dp)
                        .then(if (iconRotationDeg != 0f) Modifier.rotate(animatedRotation) else Modifier),
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun lastSeenLabel(updatedAt: Long?): String {
    if (updatedAt == null || updatedAt == 0L) return ""
    val diffMs = System.currentTimeMillis() - updatedAt
    val mins = diffMs / 60_000
    return when {
        mins < 1 -> stringResource(R.string.map_member_last_seen_now)
        mins < 60 -> stringResource(R.string.map_member_last_seen_min, mins.toInt())
        mins < 60 * 24 -> stringResource(R.string.map_member_last_seen_h, (mins / 60).toInt())
        else -> stringResource(R.string.map_member_last_seen_old)
    }
}

/** Kompaktna verzija za StatChip — "sad", "5m", "2h", "1d+" (uvek single-line). */
// compactLastSeen — preseljen u core.util.Time radi unit testabilnosti.

/**
 * GPS waiting banner — prvi launch nakon onboarding-a, FGS traži prvi fix (2-15s).
 * Bez ovog korisnik vidi praznu mapu na Beograd default centru sa nikakvim self pin-om
 * i ne zna da li nešto ne radi ili samo čeka. Banner objašnjava da je u toku.
 */
@Composable
private fun GpsWaitingBanner(isWaiting: Boolean) {
    AnimatedVisibility(
        visible = isWaiting,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Column {
            Spacer(Modifier.size(12.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(Modifier.size(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.map_gps_waiting_title),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            text = stringResource(R.string.map_gps_waiting_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Battery Saver banner — sistemski power-save mode poseban od low-battery profila.
 * Saver režim agresivno usporava sve background usluge (FGS može da dobije ređi
 * callback ritam, JobScheduler je throttled). Banner kaže korisniku da je ažuriranje
 * lokacije manje često dok je Saver on. Manji ton od offline-a (tertiary boja).
 */
@Composable
private fun PowerSaveBanner(isOnSaver: Boolean) {
    AnimatedVisibility(
        visible = isOnSaver,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Column {
            Spacer(Modifier.size(12.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.BatterySaver,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.size(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.powersave_banner_title),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Text(
                            text = stringResource(R.string.powersave_banner_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Offline banner — connectivity loss feedback. Reaktivno preko ConnectivityManager-a
 * (NetworkMonitor u core/util). Pored "Offline" labele dodaje i "poslednje ažuriranje
 * pre X min" iz self-location.updatedAt kako bi user video koliko su podaci stari.
 * Tick se osvežava na 30s da labela ne ostane zamrznuta dok banner stoji.
 */
@Composable
private fun OfflineBanner(isOnline: Boolean, lastUpdatedAt: Long?) {
    AnimatedVisibility(
        visible = !isOnline,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        OfflineBannerContent(lastUpdatedAt = lastUpdatedAt)
    }
}

@Composable
private fun OfflineBannerContent(lastUpdatedAt: Long?) {
    var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lastUpdatedAt) {
        while (true) {
            kotlinx.coroutines.delay(30_000L)
            nowTick = System.currentTimeMillis()
        }
    }
    val context = LocalContext.current
    val ageLabel = if (lastUpdatedAt != null && lastUpdatedAt > 0L) {
        org.krug.app.core.util.sosRelativeTime(context, lastUpdatedAt, nowTick)
    } else null

    Column {
    Spacer(Modifier.size(12.dp))
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.offline_banner_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = if (ageLabel != null) {
                        stringResource(R.string.offline_banner_body_with_age, ageLabel)
                    } else {
                        stringResource(R.string.offline_banner_body_unknown)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
    }
}

/**
 * Subtle warning banner — pojavljuje se ako fali permission (FINE_LOCATION / background
 * location na A10+ / POST_NOTIFICATIONS na A13+). Re-check se izvršava svaki put kad app
 * dođe u RESUMED (user je možda revokovao permission u sistemskim Settings-ima).
 *
 * Dismiss-uje se kad user grant-uje. Otvaranje sistemskog settings ekrana je jedini
 * način — na A11+ se runtime permission ne sme tražiti dva puta posle "Don't ask again".
 */
@Composable
private fun PermissionWarningBanner(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    // Inicijalni check izvršiti sinhronizovano da banner odmah pokaže stanje pri prvom
    // composition-u; bez ovog, čekamo prvi ON_RESUME event koji ne dolazi na initial
    // mount pa user vidi mapu bez banner-a iako mu nedostaju permission-i.
    var missingPermissions by remember { mutableStateOf(computeMissingPermissions(context)) }

    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                missingPermissions = computeMissingPermissions(context)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    AnimatedVisibility(
        visible = missingPermissions.isNotEmpty(),
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Column {
            Spacer(Modifier.size(12.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onOpenSettings),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.size(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.permission_banner_missing),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            text = stringResource(
                                R.string.permission_banner_body,
                                missingPermissions.joinToString(" · "),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }
    }
}

/**
 * "Ostali te ne vide" banner — visible kad SELF-share nije reliable. Za razliku od
 * PermissionWarningBanner (koji covers runtime permissions) i OfflineBanner (koji covers
 * network) — ovaj banner hvata scenario gde je sve permissions OK, network OK, ali FGS
 * ne radi (killed by OEM) ili poslednji publish stariji od 10min. Bez ovog, user koristi
 * app misleci da svi vide njegovu lokaciju dok ga zapravo niko ne vidi.
 *
 * Threshold 10 min: dovoljno da izbegnemo false positive na kratke publish gap-ove
 * (bg-heavy interval je do 15min u STILL mode-u), ali dovoljno kratko da user brzo
 * dobija upozorenje ako nešto stvarno pukne.
 */
@Composable
private fun SelfShareBrokenBanner(onOpenReliability: () -> Unit) {
    // Rescan on ON_RESUME + periodic tick — banner treba da se auto-otkrije kad FGS
    // vremenom prestane da publish-uje (bez čekanja da user ide van + nazad na screen).
    //
    // Grace period 20s posle ON_RESUME: kad user otključa telefon posle dužeg Doze
    // perioda, FGS ima 15-20s da publikuje svež fix (one-shot request-uje se u
    // onStartCommand ili triggeruje kroz LocationTracking observer). Bez grace-a,
    // banner fire-uje momentalno na wake-up (jer je lastPublish 15+min star), a
    // 3s kasnije FGS publikuje pa banner nestane. User zbunjeno vidi "click to fix"
    // pa otvori ReliabilityScreen i tamo je sve OK — ova UX nesaglasnost je bila
    // glavni user feedback (task 9).
    var tick by remember { mutableStateOf(0) }
    var resumedAt by remember { mutableStateOf(System.currentTimeMillis()) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                resumedAt = System.currentTimeMillis()
                tick++
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000L)
            tick++
        }
    }
    // Posle svakog resume-a, forsiraj re-check 25s kasnije (nakon grace expiry) —
    // ako je FGS uspeo da publikuje tokom grace-a, banner ostaje sakriven; ako
    // nije, banner sada fajruje bez čekanja sledećeg 60s tick-a.
    LaunchedEffect(resumedAt) {
        kotlinx.coroutines.delay(25_000L)
        tick++
    }

    val (isBroken, minutesStale) = remember(tick) {
        val running = org.krug.app.core.location.LocationTrackingService.isRunning.get()
        val lastPublish = org.krug.app.core.location.LocationTrackingService.lastPublishAtMs
        val now = System.currentTimeMillis()
        val ageMinutes = if (lastPublish > 0L) ((now - lastPublish) / 60_000L).toInt() else -1
        val sinceResume = now - resumedAt
        // Grace: prvih 20s posle ON_RESUME ne pokazuj banner ako je servis running —
        // dozvoli FGS-u da publikuje svež fix pre alarma. Ne blokira slucaj kad je FGS
        // ubijen (!running → banner odmah).
        val inGrace = sinceResume < 20_000L && running
        val broken = if (inGrace) false else (!running || (ageMinutes >= 10))
        broken to ageMinutes
    }

    AnimatedVisibility(
        visible = isBroken,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Column {
            Spacer(Modifier.size(12.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onOpenReliability),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.size(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.self_share_broken_title),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            text = if (minutesStale >= 0) {
                                stringResource(R.string.self_share_broken_body, minutesStale)
                            } else {
                                stringResource(R.string.self_share_broken_body_never)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }
    }
}

private fun computeMissingPermissions(context: android.content.Context): List<String> {
    // Banner lista SAMO runtime permissions (location/bg location/notifications) koje user
    // mora eksplicitno da grant-uje. Battery-opt i activity-recognition su tretirani odvojeno
    // (onboarding, startup dialog, Settings > Location reliability) jer meshanje "permission"
    // i "OS setting" u istom banner-u konfuzira user-a — misli da je to nova permisija.
    val missing = mutableListOf<String>()
    if (!org.krug.app.core.permissions.PermissionUtils.hasForegroundLocation(context)) {
        missing += context.getString(R.string.permission_missing_location)
    }
    if (org.krug.app.core.permissions.PermissionUtils.needsBackgroundLocationPermission &&
        !org.krug.app.core.permissions.PermissionUtils.hasBackgroundLocation(context)
    ) {
        missing += context.getString(R.string.permission_missing_location_background)
    }
    if (org.krug.app.core.permissions.PermissionUtils.needsNotificationsPermission &&
        !org.krug.app.core.permissions.PermissionUtils.hasNotifications(context)
    ) {
        missing += context.getString(R.string.permission_missing_notifications)
    }
    return missing
}

@Composable
private fun EtaDestinationPicker(
    onDismiss: () -> Unit,
    onSelect: (lat: Double, lng: Double, label: String) -> Unit,
    onSearch: suspend (String) -> List<org.krug.app.core.directions.GeocodingRepository.Suggestion>,
) {
    var query by remember { mutableStateOf("") }
    var suggestions by remember {
        mutableStateOf<List<org.krug.app.core.directions.GeocodingRepository.Suggestion>>(emptyList())
    }
    // Debounce search — čeka 300ms posle poslednje izmene pre nego što udari mrežu.
    LaunchedEffect(query) {
        if (query.trim().length < 3) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(300)
        suggestions = onSearch(query)
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.eta_share_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.eta_share_pick_destination),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                if (suggestions.isNotEmpty()) {
                    Column(modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                        suggestions.forEach { sug ->
                            androidx.compose.material3.TextButton(
                                onClick = { onSelect(sug.lat, sug.lng, sug.placeName.ifBlank { sug.displayName }) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = sug.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (sug.placeName.isNotBlank() && sug.placeName != sug.displayName) {
                                        Text(
                                            text = sug.placeName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/**
 * Trajni banner na vrhu mape dok user aktivno deli ETA. Prikazuje remaining ETA + destination
 * label, sa dugmetom „Prekini". Kad share pređe u arrivedAt, banner se automatski skriva
 * (myEtaShare postane null posle repository cleanup-a ili user prekine).
 */
@Composable
private fun EtaShareBanner(
    share: org.krug.app.core.eta.EtaShareModel,
    onCancel: () -> Unit,
) {
    val arrived = share.arrivedAt != null
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (arrived) MaterialTheme.colorScheme.tertiaryContainer
        else MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Icon u round backgroundu — primary/tertiary color depending on state.
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (arrived) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.primary,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (arrived) Icons.Filled.CheckCircle else Icons.Filled.Navigation,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                // Primary line — ETA time (or "Stigao/la") u velikom fontu.
                Text(
                    text = if (arrived) stringResource(R.string.eta_share_arrived)
                    else "${share.etaMinutes} min",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (arrived) MaterialTheme.colorScheme.onTertiaryContainer
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                )
                // Secondary line — destinacija + remaining km. Ako nema destinacije,
                // prikazujemo generički placeholder umesto em-dash-a.
                val hasLabel = share.destinationLabel.isNotBlank()
                val label = if (hasLabel) share.destinationLabel else stringResource(R.string.eta_share_dest_unknown)
                val kmText = if (share.remainingKm > 0.1 && !arrived) {
                    " • %.1f km".format(share.remainingKm)
                } else ""
                Text(
                    text = label + kmText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (arrived) MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f)
                    else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!arrived) {
                Spacer(Modifier.width(8.dp))
                androidx.compose.material3.TextButton(
                    onClick = onCancel,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp, vertical = 8.dp,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.eta_share_cancel),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        }
    }
}
