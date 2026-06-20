package org.krug.app.feature.map

import android.annotation.SuppressLint
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.NearMe
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotation
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.OnPointAnnotationClickListener
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createCircleAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.scalebar.scalebar
import android.view.Gravity
import org.krug.app.R
import org.krug.app.core.location.LocationTrackingService
import org.krug.app.feature.circle.CircleIconAssets

private const val DEFAULT_LAT = 44.7866 // Belgrade
private const val DEFAULT_LNG = 20.4489
private const val DEFAULT_ZOOM = 12.0
private val SosRed = Color(0xFFDC2626)
private val SosRedDark = Color(0xFFB91C1C)
private val PrivateGray = Color(0xFF9CA3AF)

/** Granica nakon koje smatramo da je član u "privatnom modu" — FGS ubijen, sharing off, ili offline. */
private const val PRIVATE_MODE_THRESHOLD_MS = 15 * 60_000L

private fun MemberWithLocation.isPrivate(): Boolean {
    if (isSelf) return false // za sebe ne pokazujemo private mode
    val updatedAt = location?.updatedAt ?: return true
    return System.currentTimeMillis() - updatedAt > PRIVATE_MODE_THRESHOLD_MS
}

/** Lokalno vreme — uveče i noću se prebacuje na tamniju varijantu mape. */
private fun pickMapStyle(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return if (hour in 7..18) Style.STANDARD else Style.DARK
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onOpenCircles: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCircleDetail: (circleId: String) -> Unit = {},
    viewModel: MapViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val haptic: () -> Unit = remember(view) {
        { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }
    }
    val mapViewState = remember { MapViewHolder() }
    val photoCache = remember { mutableStateMapOf<String, Bitmap>() }
    var detailUid by remember { mutableStateOf<String?>(null) }
    // Pending refocus: kad user tapne Osveži, pamtimo (uid, since). Kad stigne
    // sveži location.updatedAt > since za taj uid, automatski flyTo na novu poziciju.
    var pendingRefocus by remember { mutableStateOf<Pair<String, Long>?>(null) }

    LaunchedEffect(pendingRefocus, state.members) {
        val pending = pendingRefocus ?: return@LaunchedEffect
        val (uid, since) = pending
        val loc = state.members.firstOrNull { it.uid == uid }?.location
        if (loc != null && loc.updatedAt > since) {
            mapViewState.flyTo(loc.lng, loc.lat)
            pendingRefocus = null
        }
    }
    // Timeout — ako member nije publish-ovao u 30s (FGS ubijen, no permission), drop.
    LaunchedEffect(pendingRefocus) {
        val pending = pendingRefocus ?: return@LaunchedEffect
        kotlinx.coroutines.delay(30_000)
        if (pendingRefocus == pending) pendingRefocus = null
    }

    DisposableEffect(Unit) {
        LocationTrackingService.start(context)
        onDispose { }
    }

    // Click handler za pin — fly-to + otvori MemberDetail sheet.
    DisposableEffect(mapViewState, state.members) {
        mapViewState.onPinClick = { uid ->
            haptic()
            state.members.firstOrNull { it.uid == uid }?.location?.let { loc ->
                mapViewState.flyTo(loc.lng, loc.lat)
            }
            detailUid = uid
        }
        onDispose { mapViewState.onPinClick = null }
    }

    // Učitaj profilne fotke (Google sign-in) za sve članove kojima imamo URL.
    LaunchedEffect(state.members.map { it.photoUrl }) {
        val urls = state.members.mapNotNull { it.photoUrl?.takeIf { u -> u.isNotBlank() } }.toSet()
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
    // oko SOS markera. Kad nema aktivnih SOS-ova, animation se i dalje vrti (jeftino),
    // ali updateSosRipples ne radi ništa.
    val infiniteTransition = rememberInfiniteTransition(label = "sosRipple")
    val sosPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sosRipplePhase",
    )
    LaunchedEffect(activeSosMembers.map { "${it.uid}:${it.location?.lat}:${it.location?.lng}" }, sosPhase) {
        mapViewState.updateSosRipples(activeSosMembers, sosPhase)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MapboxContainer(members = state.members, photoCache = photoCache, holder = mapViewState)

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
                onOpenCircles = onOpenCircles,
                onOpenPicker = { circlePickerVisible = true },
                onOpenSettings = onOpenSettings,
            )
            if (activeSosMembers.isNotEmpty()) {
                Spacer(Modifier.size(12.dp))
                SosBanner(
                    members = activeSosMembers,
                    selfUid = state.selfUid,
                    onClickMember = { m ->
                        m.location?.let { loc ->
                            mapViewState.flyTo(loc.lng, loc.lat)
                        }
                    },
                    onCancelSelf = { viewModel.clearSos() },
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

        MembersPill(
            members = state.members,
            photoCache = photoCache,
            onClick = { sheetVisible = true },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 36.dp),
        )

        if (sheetVisible) {
            ModalBottomSheet(
                onDismissRequest = { sheetVisible = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                MembersSheet(
                    members = state.members,
                    photoCache = photoCache,
                    onMemberClick = { uid ->
                        haptic()
                        sheetVisible = false
                        state.members.firstOrNull { it.uid == uid }?.location?.let { loc ->
                            mapViewState.flyTo(loc.lng, loc.lat)
                        }
                        detailUid = uid
                    },
                )
            }
        }

        val detailMember = detailUid?.let { uid -> state.members.firstOrNull { it.uid == uid } }
        if (detailMember != null) {
            ModalBottomSheet(
                onDismissRequest = { detailUid = null },
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                MemberDetailSheet(
                    member = detailMember,
                    selfLocation = state.selfLocation,
                    photo = detailMember.photoUrl?.let { photoCache[it] },
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
                        pendingRefocus = detailMember.uid to System.currentTimeMillis()
                        if (detailMember.isSelf) {
                            LocationTrackingService.refreshSelf(context)
                        } else {
                            viewModel.refreshMember(detailMember.uid)
                        }
                    },
                )
            }
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
                        haptic()
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
            AlertDialog(
                onDismissRequest = { sosConfirmVisible = false },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = SosRed,
                    )
                },
                title = { Text("Pošalji SOS?") },
                text = {
                    Text(
                        "Svi članovi tvojih krugova će dobiti hitno obaveštenje sa tvojom " +
                            "trenutnom lokacijom. Koristi samo u stvarnoj opasnosti",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.triggerSos()
                            sosConfirmVisible = false
                        },
                    ) {
                        Text("Pošalji SOS", color = SosRed, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { sosConfirmVisible = false }) { Text("Otkaži") }
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
    onOpenCircles: () -> Unit,
    onOpenPicker: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val active = circles.firstOrNull { it.id == activeCircleId } ?: circles.firstOrNull()
    val pillLabel = active?.name ?: stringResource(R.string.map_title)
    val onPillClick: () -> Unit = if (circles.isEmpty()) onOpenCircles else onOpenPicker
    val pillShape = RoundedCornerShape(28.dp)
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
            CircleIconButton(
                icon = Icons.Outlined.Group,
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
    Box(
        modifier = Modifier
            .size(48.dp)
            .krugGlass(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface,
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
    Box(
        modifier = modifier
            .size(48.dp)
            .then(if (active) activeModifier else Modifier.krugGlass(CircleShape))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "SoS",
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
    onClickMember: (MemberWithLocation) -> Unit,
    onCancelSelf: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(20.dp), clip = false),
        shape = RoundedCornerShape(20.dp),
        color = SosRed,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = Color.White,
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    text = "Hitno — neko traži pomoć",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
            }
            Spacer(Modifier.size(8.dp))
            members.forEach { m ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val label = if (m.uid == selfUid) "Ti" else m.displayName.ifBlank { "Član" }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    if (m.uid == selfUid) {
                        TextButton(onClick = onCancelSelf) {
                            Text("Otkaži", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        TextButton(onClick = { onClickMember(m) }) {
                            Text("Vidi", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (m != members.last()) Spacer(Modifier.size(6.dp))
            }
        }
    }
}

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
) {
    val context = LocalContext.current

    androidx.compose.ui.viewinterop.AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            MapView(ctx).also { mv ->
                holder.mapView = mv
                // SOS ripple manager mora biti kreiran PRE pin annotation manager-a — circle
                // se render-uje ispod pinova jer je dodat prvi u layer stack-u Mapbox-a.
                holder.circleManager = mv.annotations.createCircleAnnotationManager()
                val manager = mv.annotations.createPointAnnotationManager()
                holder.annotationManager = manager
                manager.addClickListener(
                    OnPointAnnotationClickListener { annotation ->
                        val uid = holder.annotationToUid[annotation.id]
                        if (uid != null) {
                            holder.onPinClick?.invoke(uid)
                            true
                        } else {
                            false
                        }
                    },
                )
                // Compass se default-no pojavi top-right kad rotirаš mapu — ali tamo je
                // Settings button. Isključujem ga jer ovaj app ne traži kompas (pin-ovi
                // su orijentisani severom kroz "Centriraj" / flyTo akcije).
                mv.compass.updateSettings { enabled = false }
                // Scale bar — dole-levo, ispod Članovi pill-a (pill je centriran,
                // levi ugao je slobodan). Metric units.
                val density = ctx.resources.displayMetrics.density
                mv.scalebar.updateSettings {
                    position = Gravity.BOTTOM or Gravity.START
                    marginLeft = 16f * density
                    marginBottom = 8f * density
                    marginTop = 0f
                    marginRight = 0f
                    isMetricUnits = true
                }
                mv.mapboxMap.setCamera(
                    CameraOptions.Builder()
                        .center(Point.fromLngLat(DEFAULT_LNG, DEFAULT_LAT))
                        .zoom(DEFAULT_ZOOM)
                        .build(),
                )
                // Auto light/dark prema vremenu: 7-19h → STANDARD (vibrant day), 19-7h → DARK.
                // Set once na factory creation; ako user otvori app kasnije, novi map view
                // se kreira sa drugačijim style-om.
                mv.mapboxMap.loadStyle(pickMapStyle())
            }
        },
        update = { _ ->
            val manager = holder.annotationManager ?: return@AndroidView
            // Fingerprint check — preskoči deleteAll+create ako se data nije promenila
            // (recomposition se često događa zbog state-a koji ne utiče na pin-ove).
            val fingerprint = members.joinToString("|") { m ->
                val loc = m.location
                val ph = m.photoUrl?.let { photoCache[it] }?.hashCode() ?: 0
                "${m.uid}:${loc?.lat ?: ""}:${loc?.lng ?: ""}:${loc?.batteryPct ?: -1}:${m.sos != null}:${m.displayName}:$ph"
            }
            if (fingerprint == holder.lastFingerprint) return@AndroidView
            holder.lastFingerprint = fingerprint

            manager.deleteAll()
            holder.annotationToUid.clear()
            members.forEach { member ->
                val loc = member.location ?: return@forEach
                val priv = member.isPrivate()
                val color = when {
                    member.sos != null -> "#DC2626" // red for SOS
                    priv -> "#9CA3AF" // gray — stara lokacija, privatni mod
                    member.isSelf -> "#818CF8"
                    else -> MapMarkers.colorForUid(member.uid)
                }
                val photo = member.photoUrl?.let { photoCache[it] }
                val initials = MapMarkers.computeInitials(member.displayName)
                val label = member.displayName
                    .ifBlank { if (member.isSelf) "Ti" else "Član" }
                    .take(18)
                val batteryPct = member.location.batteryPct.takeIf { it in 0..100 }
                val annotation = manager.create(
                    PointAnnotationOptions()
                        .withPoint(Point.fromLngLat(loc.lng, loc.lat))
                        .withIconImage(MapMarkers.pinMarker(context, color, photo, initials, batteryPct))
                        .withTextField(label)
                        .withTextSize(12.0)
                        .withTextOffset(listOf(0.0, 1.6))
                        .withTextColor("#1F2937")
                        .withTextHaloColor("#FFFFFF")
                        .withTextHaloWidth(2.0),
                )
                holder.annotationToUid[annotation.id] = member.uid
            }
            val self = members.firstOrNull { it.isSelf }?.location
            if (self != null && !holder.didFlyToSelf) {
                holder.mapView?.mapboxMap?.flyTo(
                    CameraOptions.Builder()
                        .center(Point.fromLngLat(self.lng, self.lat))
                        .zoom(14.0)
                        .build(),
                    MapAnimationOptions.mapAnimationOptions { duration(1500L) },
                )
                holder.didFlyToSelf = true
            }
        },
    )
}

private class MapViewHolder {
    var mapView: MapView? = null
    var annotationManager: PointAnnotationManager? = null
    var circleManager: CircleAnnotationManager? = null
    var didFlyToSelf: Boolean = false
    val annotationToUid = mutableMapOf<String, String>()
    /** SOS ripple — per-UID circle annotation (radar pulse oko SOS markera). */
    val sosRipples = mutableMapOf<String, CircleAnnotation>()
    var onPinClick: ((String) -> Unit)? = null
    var lastFingerprint: String = ""

    fun flyTo(lng: Double, lat: Double) {
        mapView?.mapboxMap?.flyTo(
            CameraOptions.Builder()
                .center(Point.fromLngLat(lng, lat))
                .zoom(16.5)
                .build(),
            MapAnimationOptions.mapAnimationOptions { duration(1200L) },
        )
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
                        .withCircleColor("#DC2626")
                        .withCircleOpacity(alpha)
                        .withCircleStrokeWidth(2.0)
                        .withCircleStrokeColor("#DC2626")
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
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(circles, key = { it.id }) { c ->
                val selected = c.id == activeCircleId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                        )
                        .clickable { onPick(c.id) }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.RadioButton(
                        selected = selected,
                        onClick = { onPick(c.id) },
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = c.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onOpenDetail(c.id) }) {
                        Text(stringResource(R.string.map_circle_picker_detail))
                    }
                }
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
private fun MembersSheet(
    members: List<MemberWithLocation>,
    photoCache: Map<String, Bitmap>,
    onMemberClick: (String) -> Unit,
) {
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
                items(members, key = { it.uid }) { m ->
                    MemberRow(
                        member = m,
                        photo = m.photoUrl?.let { photoCache[it] },
                        onClick = { onMemberClick(m.uid) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MemberRow(
    member: MemberWithLocation,
    photo: Bitmap?,
    onClick: () -> Unit = {},
) {
    val markerColor = when {
        member.sos != null -> SosRed
        member.isSelf -> MaterialTheme.colorScheme.primary
        else -> Color(android.graphics.Color.parseColor(MapMarkers.colorForUid(member.uid)))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(14.dp),
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
            Text(
                text = member.displayName.ifBlank { if (member.isSelf) "Ti" else "Član" },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            val priv = member.isPrivate()
            val statusLine = when {
                member.sos != null -> "SOS — traži pomoć"
                priv -> "Privatni mod"
                else -> lastSeenLabel(member.location?.updatedAt)
            }
            val deviceSuffix = if (member.deviceModel.isNotBlank() && member.sos == null && !priv) {
                " · ${member.deviceModel}"
            } else {
                ""
            }
            Text(
                text = statusLine + deviceSuffix,
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
        shape = RoundedCornerShape(10.dp),
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
    pct >= 50 -> Color(0xFF10B981)
    pct >= 20 -> Color(0xFFF59E0B)
    else -> Color(0xFFEF4444)
}

private fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6371000.0
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val dphi = Math.toRadians(lat2 - lat1)
    val dlambda = Math.toRadians(lng2 - lng1)
    val a = kotlin.math.sin(dphi / 2).let { it * it } +
        kotlin.math.cos(phi1) * kotlin.math.cos(phi2) *
        kotlin.math.sin(dlambda / 2).let { it * it }
    val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    return r * c
}

private fun formatDistance(meters: Double): String = when {
    meters < 50 -> "blizu"
    meters < 1000 -> "${meters.toInt()} m"
    meters < 10_000 -> String.format("%.1f km", meters / 1000.0)
    else -> "${(meters / 1000.0).toInt()} km"
}

@Composable
private fun MemberDetailSheet(
    member: MemberWithLocation,
    selfLocation: org.krug.app.core.location.LocationModel?,
    photo: Bitmap?,
    onOpenInMaps: () -> Unit,
    onRefresh: () -> Unit,
) {
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
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(72.dp)
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
                Text(
                    text = member.displayName.ifBlank { if (member.isSelf) "Ti" else "Član" },
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                )
                if (member.deviceModel.isNotBlank()) {
                    Text(
                        text = member.deviceModel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        val isPrivate = member.isPrivate()

        if (member.sos != null) {
            Spacer(Modifier.height(16.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SosRed,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Aktivan SOS — traži pomoć",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        } else if (isPrivate) {
            Spacer(Modifier.height(16.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = PrivateGray.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Privatni mod",
                        color = PrivateGray,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Član trenutno ne deli lokaciju ili je offline. " +
                            "Prikazana je poslednja poznata pozicija",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val batt = member.location?.batteryPct
            // Sakrijemo bateriju u privatnom modu — vrednost je stara/zabludljiva.
            if (!isPrivate && batt != null && batt in 0..100) {
                val charging = member.location.charging
                StatChip(
                    label = if (charging) "Puni se" else "Baterija",
                    value = "$batt%",
                    accentColor = batteryColor(batt),
                    icon = if (charging) Icons.Filled.BatteryChargingFull
                    else Icons.Outlined.BatteryFull,
                    modifier = Modifier.weight(1f),
                )
            }
            // Udaljenost — samo za druge, kad imamo obe lokacije i nije privatan.
            if (!member.isSelf && !isPrivate && selfLocation != null && member.location != null) {
                StatChip(
                    label = "Udaljenost",
                    value = formatDistance(
                        haversineMeters(
                            selfLocation.lat, selfLocation.lng,
                            member.location.lat, member.location.lng,
                        ),
                    ),
                    accentColor = MaterialTheme.colorScheme.primary,
                    icon = Icons.Outlined.NearMe,
                    modifier = Modifier.weight(1f),
                )
            }
            StatChip(
                label = "Poslednje",
                value = lastSeenLabel(member.location?.updatedAt).ifBlank { "—" },
                accentColor = if (isPrivate) PrivateGray else MaterialTheme.colorScheme.primary,
                icon = Icons.Outlined.AccessTime,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(20.dp))
        if (member.isSelf) {
            androidx.compose.material3.Button(
                onClick = {
                    onRefresh()
                    refreshTriggered = true
                },
                enabled = !refreshTriggered,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (refreshTriggered) "Osveženo…" else "Osveži moju lokaciju")
            }
            if (member.location != null) {
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.OutlinedButton(
                    onClick = onOpenInMaps,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Otvori u Google Maps")
                }
            }
        } else if (member.location != null) {
            if (!isPrivate) {
                androidx.compose.material3.Button(
                    onClick = {
                        onRefresh()
                        refreshTriggered = true
                    },
                    enabled = !refreshTriggered,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (refreshTriggered) "Zahtev poslat…" else "Osveži lokaciju")
                }
                Spacer(Modifier.height(8.dp))
            }
            androidx.compose.material3.OutlinedButton(
                onClick = onOpenInMaps,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Otvori u Google Maps")
            }
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
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(14.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = accentColor,
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
