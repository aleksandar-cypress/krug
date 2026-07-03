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
    val range by viewModel.selectedDay.collectAsStateWithLifecycle()
    var dayOffset by remember { mutableStateOf(0) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var polylineManager by remember { mutableStateOf<PolylineAnnotationManager?>(null) }
    var pointManager by remember { mutableStateOf<com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager?>(null) }
    var scrubTime by remember { mutableStateOf(1f) } // 0..1 = start..end dana

    // Kad se promene point-i ili scrub, re-render polyline + start/end markere.
    LaunchedEffect(points, scrubTime, polylineManager, pointManager) {
        val pm = polylineManager ?: return@LaunchedEffect
        val ptm = pointManager ?: return@LaunchedEffect
        pm.deleteAll()
        ptm.deleteAll()
        if (points.isEmpty()) return@LaunchedEffect
        val cutoff = range.fromMs + ((range.toMs - range.fromMs) * scrubTime).toLong()
        val visible = points.filter { p -> (p.timestamp?.time ?: 0L) <= cutoff }
        if (visible.size < 2) {
            if (visible.isNotEmpty()) {
                val p = visible.first()
                ptm.create(
                    PointAnnotationOptions()
                        .withPoint(Point.fromLngLat(p.lng, p.lat))
                        .withIconColor("#10B981"),
                )
            }
            return@LaunchedEffect
        }
        val line = LineString.fromLngLats(
            visible.map { Point.fromLngLat(it.lng, it.lat) },
        )
        pm.create(
            PolylineAnnotationOptions()
                .withGeometry(line)
                .withLineColor("#4F46E5")
                .withLineWidth(4.0),
        )
        // Fit bounds na traku
        val bounds = visible.map { it.lat to it.lng }
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
                    Text(
                        stringResource(R.string.history_empty),
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                // Time scrubber
                Text(
                    stringResource(R.string.history_time_label, formatTime(range.fromMs, scrubTime)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = scrubTime,
                    onValueChange = { scrubTime = it },
                )
                Text(
                    stringResource(R.string.history_points_count, points.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

private fun formatDay(ms: Long): String {
    val sdf = SimpleDateFormat("EEEE, d. MMMM", Locale("sr"))
    return sdf.format(java.util.Date(ms))
}

private fun formatTime(fromMs: Long, ratio: Float): String {
    val ms = fromMs + (24 * 60 * 60_000L * ratio).toLong()
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(java.util.Date(ms))
}
