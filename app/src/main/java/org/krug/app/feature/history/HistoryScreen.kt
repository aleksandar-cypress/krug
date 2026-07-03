package org.krug.app.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager
import com.mapbox.maps.plugin.locationcomponent.location
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import org.krug.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val points by viewModel.points.collectAsStateWithLifecycle()
    val activePlaces by viewModel.activePlaces.collectAsStateWithLifecycle()
    val range by viewModel.selectedDay.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    var dayOffset by remember { mutableStateOf(0) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var polylineManager by remember { mutableStateOf<PolylineAnnotationManager?>(null) }
    var pointManager by remember { mutableStateOf<com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager?>(null) }
    var scrubTime by remember { mutableStateOf(1f) } // 0..1 = start..end dana
    var playing by remember { mutableStateOf(false) }
    // Fit-bounds samo jednom po (dan, points-first-load). Kad user počne da menja
    // scrub, ne pomeramo kameru — dozvoljavamo mu manuelnu kontrolu (pinch zoom, pan).
    var cameraFitDone by remember(range.fromMs) { mutableStateOf(false) }

    // Auto-play: advance scrubTime kroz vreme dok playing=true.
    // Playback traje ~30s za ceo dan (24h scaled), stops kad stigne do 1.0.
    LaunchedEffect(playing, points.size) {
        if (!playing || points.isEmpty()) return@LaunchedEffect
        if (scrubTime >= 1f) scrubTime = 0f
        while (playing && scrubTime < 1f) {
            kotlinx.coroutines.delay(50)
            scrubTime = (scrubTime + 0.0016f).coerceAtMost(1f) // 0.0016/50ms → ~30s total
        }
        if (scrubTime >= 1f) playing = false
    }

    // Kad se promene point-i ili scrub, re-render polyline + start/end markere + Places pinove.
    LaunchedEffect(points, activePlaces, scrubTime, polylineManager, pointManager) {
        val pm = polylineManager ?: return@LaunchedEffect
        val ptm = pointManager ?: return@LaunchedEffect
        pm.deleteAll()
        ptm.deleteAll()
        // 1) Places pinovi — statični, ne zavise od scrub-a.
        activePlaces.forEach { place ->
            ptm.create(
                PointAnnotationOptions()
                    .withPoint(Point.fromLngLat(place.lng, place.lat))
                    .withIconImage(
                        org.krug.app.feature.map.MapMarkers.placeMarker(context, place.category),
                    )
                    .withIconOffset(listOf(0.0, -21.0))
                    .withTextField(place.name)
                    .withTextOffset(listOf(0.0, 0.6))
                    .withTextSize(11.0)
                    .withTextColor("#1F2937")
                    .withTextHaloColor("#FFFFFF")
                    .withTextHaloWidth(1.5),
            )
        }
        if (points.isEmpty()) return@LaunchedEffect
        val cutoff = range.fromMs + ((range.toMs - range.fromMs) * scrubTime).toLong()
        val visible = points.filter { p -> (p.timestamp?.time ?: 0L) <= cutoff }
        // Start marker — prva tačka dana (zelena)
        points.firstOrNull()?.let { start ->
            ptm.create(
                PointAnnotationOptions()
                    .withPoint(Point.fromLngLat(start.lng, start.lat))
                    .withIconImage(
                        org.krug.app.feature.map.MapMarkers.dotMarker(context, "#10B981"),
                    ),
            )
        }
        // Current position marker — poslednja visible tačka (indigo, veća od start-a)
        visible.lastOrNull()?.let { cur ->
            ptm.create(
                PointAnnotationOptions()
                    .withPoint(Point.fromLngLat(cur.lng, cur.lat))
                    .withIconImage(
                        org.krug.app.feature.map.MapMarkers.dotMarker(context, "#4F46E5", size = 18f),
                    ),
            )
        }
        if (visible.size < 2) return@LaunchedEffect
        val line = LineString.fromLngLats(
            visible.map { Point.fromLngLat(it.lng, it.lat) },
        )
        pm.create(
            PolylineAnnotationOptions()
                .withGeometry(line)
                .withLineColor("#4F46E5")
                .withLineWidth(4.0),
        )
        // Fit bounds SAMO prvi put kad points za taj dan stignu — kasnije user ima punu
        // manuelnu kontrolu (pinch zoom, pan). Bez ovog: scrub bi svaki put resetovao
        // camera i user ne bi mogao da zumira detalje.
        if (!cameraFitDone) {
            val bounds = points.map { it.lat to it.lng }
            val minLat = bounds.minOf { it.first }
            val maxLat = bounds.maxOf { it.first }
            val minLng = bounds.minOf { it.second }
            val maxLng = bounds.maxOf { it.second }
            mapView?.mapboxMap?.setCamera(
                CameraOptions.Builder()
                    .center(Point.fromLngLat((minLng + maxLng) / 2, (minLat + maxLat) / 2))
                    .zoom(13.0)
                    .build(),
            )
            cameraFitDone = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.displayName.ifBlank { stringResource(R.string.history_title) }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).also { mv ->
                            mapView = mv
                            mv.location.updateSettings { enabled = false }
                            mv.mapboxMap.loadStyle(Style.STANDARD)
                            polylineManager = mv.annotations.createPolylineAnnotationManager()
                            pointManager = mv.annotations.createPointAnnotationManager()
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                if (points.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                MaterialTheme.shapes.medium,
                            )
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            stringResource(R.string.history_empty),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            stringResource(R.string.history_empty_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Day selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        dayOffset = (dayOffset - 1).coerceAtLeast(-29)
                        viewModel.setDayOffset(dayOffset)
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                    Text(
                        text = formatDay(range.fromMs),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            dayOffset = (dayOffset + 1).coerceAtMost(0)
                            viewModel.setDayOffset(dayOffset)
                        },
                        enabled = dayOffset < 0,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer { scaleX = -1f },
                        )
                    }
                }
                // Time scrubber sa play/pause dugmetom
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(
                        onClick = {
                            if (scrubTime >= 1f) {
                                scrubTime = 0f
                                playing = true
                            } else {
                                playing = !playing
                            }
                        },
                        enabled = points.isNotEmpty(),
                    ) {
                        Icon(
                            imageVector = if (scrubTime >= 1f) Icons.Filled.Replay
                            else if (playing) Icons.Filled.Pause
                            else Icons.Filled.PlayArrow,
                            contentDescription = null,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.history_time_label, formatTime(range.fromMs, scrubTime)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Slider(
                            value = scrubTime,
                            onValueChange = { scrubTime = it; playing = false },
                        )
                    }
                }
                val stats = remember(points) { computeStats(points) }
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatCell(
                        label = stringResource(R.string.history_stat_distance),
                        value = formatKm(stats.distanceMeters),
                        modifier = Modifier.weight(1f),
                    )
                    StatCell(
                        label = stringResource(R.string.history_stat_active),
                        value = formatDuration(stats.activeMs),
                        modifier = Modifier.weight(1f),
                    )
                    StatCell(
                        label = stringResource(R.string.history_stat_max_speed),
                        value = formatSpeed(stats.maxSpeedMps),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mapView = null
            polylineManager = null
            pointManager = null
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private data class HistoryStats(
    val distanceMeters: Double,
    val activeMs: Long,
    val maxSpeedMps: Float,
    val avgSpeedMps: Float,
)

/**
 * Distance: sabira haversine udaljenosti između uzastopnih point-a.
 * Active time: sabira interval samo ako je gap između tačaka < 15min
 * (>15min pretpostavljamo da je user bio ne-aktivan, npr. spava).
 * Speed: max iz `speed` polja (m/s), avg = distance / activeTime.
 */
private fun computeStats(points: List<org.krug.app.core.location.LocationHistoryPoint>): HistoryStats {
    if (points.size < 2) return HistoryStats(0.0, 0L, 0f, 0f)
    var dist = 0.0
    var active = 0L
    var maxSpeed = 0f
    for (i in 1 until points.size) {
        val a = points[i - 1]
        val b = points[i]
        val res = FloatArray(1)
        android.location.Location.distanceBetween(a.lat, a.lng, b.lat, b.lng, res)
        dist += res[0]
        val gap = (b.timestamp?.time ?: 0L) - (a.timestamp?.time ?: 0L)
        if (gap in 1L..15 * 60_000L) active += gap
        if (b.speed > maxSpeed) maxSpeed = b.speed
    }
    val avg = if (active > 0) (dist / (active / 1000.0)).toFloat() else 0f
    return HistoryStats(dist, active, maxSpeed, avg)
}

private fun formatSpeed(mps: Float): String {
    if (mps <= 0.1f) return "-"
    val kmh = mps * 3.6f
    return "%.0f km/h".format(kmh)
}

private fun formatKm(meters: Double): String {
    return if (meters < 1000) "${meters.toInt()} m"
    else "%.1f km".format(meters / 1000)
}

private fun formatDuration(ms: Long): String {
    val mins = ms / 60_000L
    return when {
        mins < 60 -> "${mins}min"
        else -> "${mins / 60}h ${mins % 60}min"
    }
}

private fun formatDay(ms: Long): String {
    // Forsiraj latinicu — sr_RS default u Android-u je ćirilica, a app UI je celi na latinici.
    val sdf = SimpleDateFormat("EEEE, d. MMMM", Locale.forLanguageTag("sr-Latn"))
    return sdf.format(java.util.Date(ms))
}

private fun formatTime(fromMs: Long, ratio: Float): String {
    val ms = fromMs + (24 * 60 * 60_000L * ratio).toLong()
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(java.util.Date(ms))
}
