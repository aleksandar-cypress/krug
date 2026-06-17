package org.krug.app.feature.map

import android.annotation.SuppressLint
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Group
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.OnPointAnnotationClickListener
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import org.krug.app.R
import org.krug.app.core.location.LocationTrackingService

private const val DEFAULT_LAT = 44.7866 // Belgrade
private const val DEFAULT_LNG = 20.4489
private const val DEFAULT_ZOOM = 12.0
private val SosRed = Color(0xFFDC2626)
private val SosRedDark = Color(0xFFB91C1C)

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
    val mapViewState = remember { MapViewHolder() }
    val photoCache = remember { mutableStateMapOf<String, Bitmap>() }
    var detailUid by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        LocationTrackingService.start(context)
        onDispose { }
    }

    // Click handler za pin — otvara MemberDetail sheet.
    DisposableEffect(mapViewState) {
        mapViewState.onPinClick = { uid -> detailUid = uid }
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val activeSosMembers = state.members.filter { it.sos != null }

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
                onOpenCircles = onOpenCircles,
                onOpenCircleDetail = onOpenCircleDetail,
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
                if (state.selfSosActive) viewModel.clearSos()
                else sosConfirmVisible = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 32.dp),
        )

        MembersPill(
            count = state.members.size,
            onClick = { sheetVisible = true },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        )

        if (sheetVisible) {
            ModalBottomSheet(
                onDismissRequest = { sheetVisible = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                MembersSheet(state.members, onMemberClick = { uid ->
                    sheetVisible = false
                    detailUid = uid
                })
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
                    onFlyTo = {
                        detailMember.location?.let { mapViewState.flyTo(it.lng, it.lat) }
                        detailUid = null
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
                            "trenutnom lokacijom. Koristi samo u stvarnoj opasnosti.",
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

@Composable
private fun TopFloatingBar(
    circles: List<CircleBrief>,
    onOpenCircles: () -> Unit,
    onOpenCircleDetail: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val pillLabel = when (circles.size) {
        0 -> stringResource(R.string.map_title)
        1 -> circles.first().name
        else -> stringResource(R.string.map_pill_multi_circles, circles.size)
    }
    val onPillClick: () -> Unit = when (circles.size) {
        0 -> onOpenCircles
        1 -> { { onOpenCircleDetail(circles.first().id) } }
        else -> onOpenCircles
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp,
            onClick = onPillClick,
        ) {
            Text(
                text = pillLabel,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
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
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        modifier = Modifier.size(48.dp),
    ) {
        IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SosFab(
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .shadow(elevation = 8.dp, shape = CircleShape, clip = false)
            .size(48.dp),
        shape = CircleShape,
        color = if (active) SosRedDark else MaterialTheme.colorScheme.surface,
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = "SOS",
                tint = if (active) Color.White else SosRed,
                modifier = Modifier.size(22.dp),
            )
        }
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
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .shadow(elevation = 10.dp, shape = RoundedCornerShape(28.dp), clip = false),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Group,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = stringResource(R.string.map_members_sheet_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
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
                mv.mapboxMap.setCamera(
                    CameraOptions.Builder()
                        .center(Point.fromLngLat(DEFAULT_LNG, DEFAULT_LAT))
                        .zoom(DEFAULT_ZOOM)
                        .build(),
                )
            }
        },
        update = { _ ->
            val manager = holder.annotationManager ?: return@AndroidView
            manager.deleteAll()
            holder.annotationToUid.clear()
            members.forEach { member ->
                val loc = member.location ?: return@forEach
                val color = when {
                    member.sos != null -> "#DC2626" // red for SOS
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
    var didFlyToSelf: Boolean = false
    val annotationToUid = mutableMapOf<String, String>()
    var onPinClick: ((String) -> Unit)? = null

    fun flyTo(lng: Double, lat: Double) {
        mapView?.mapboxMap?.flyTo(
            CameraOptions.Builder()
                .center(Point.fromLngLat(lng, lat))
                .zoom(15.0)
                .build(),
            MapAnimationOptions.mapAnimationOptions { duration(1200L) },
        )
    }
}

@Composable
private fun MembersSheet(
    members: List<MemberWithLocation>,
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
                    MemberRow(m, onClick = { onMemberClick(m.uid) })
                }
            }
        }
    }
}

@Composable
private fun MemberRow(member: MemberWithLocation, onClick: () -> Unit = {}) {
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
            val initial = member.displayName.firstOrNull()?.uppercaseChar()?.toString()
            if (initial != null) {
                Text(
                    text = initial,
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
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = member.displayName.ifBlank { if (member.isSelf) "Ti" else "Član" },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            val statusLine = when {
                member.sos != null -> "SOS — traži pomoć"
                else -> lastSeenLabel(member.location?.updatedAt)
            }
            val deviceSuffix = if (member.deviceModel.isNotBlank() && member.sos == null) {
                " · ${member.deviceModel}"
            } else {
                ""
            }
            Text(
                text = statusLine + deviceSuffix,
                style = MaterialTheme.typography.bodySmall,
                color = if (member.sos != null) SosRed
                else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (member.sos != null) FontWeight.Bold else FontWeight.Normal,
            )
        }
        if (member.location?.batteryPct != null && member.location.batteryPct >= 0) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Text(
                    text = "${member.location.batteryPct}%",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun MemberDetailSheet(
    member: MemberWithLocation,
    photo: Bitmap?,
    onOpenInMaps: () -> Unit,
    onFlyTo: () -> Unit,
) {
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
        }

        Spacer(Modifier.height(20.dp))
        // Stats row: battery + last seen.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val batt = member.location?.batteryPct
            if (batt != null && batt in 0..100) {
                StatChip(
                    label = "Baterija",
                    value = "$batt%",
                    accentColor = when {
                        batt >= 50 -> Color(0xFF10B981)
                        batt >= 20 -> Color(0xFFF59E0B)
                        else -> Color(0xFFEF4444)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            StatChip(
                label = "Poslednje",
                value = lastSeenLabel(member.location?.updatedAt).ifBlank { "—" },
                accentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(20.dp))
        if (!member.isSelf && member.location != null) {
            androidx.compose.material3.Button(
                onClick = onFlyTo,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Centriraj na mapi")
            }
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.OutlinedButton(
                onClick = onOpenInMaps,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Otvori u Google Maps")
            }
        } else if (member.isSelf) {
            Text(
                text = "Ovo si ti.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatChip(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
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
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = accentColor,
        )
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
