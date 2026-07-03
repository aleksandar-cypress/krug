package org.krug.app.feature.places

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.locationcomponent.location
import org.krug.app.R
import org.krug.app.core.places.PlaceModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPlaceScreen(
    onBack: () -> Unit,
    viewModel: PlacesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var radius by remember { mutableStateOf(PlaceModel.DEFAULT_RADIUS_M.toFloat()) }
    val mapViewRef = remember { MapViewHolder2() }
    var initialCenterApplied by remember { mutableStateOf(false) }
    // Camera state za overlay krug — lat i zoom određuju pixels-per-meter.
    var cameraLat by remember { mutableStateOf(44.81) }
    var cameraZoom by remember { mutableStateOf(12.0) }
    val density = LocalDensity.current

    // Kad stigne current lokacija, centriraj kameru jednom (samo prvi put).
    LaunchedEffect(state.currentLat, state.currentLng) {
        val lat = state.currentLat
        val lng = state.currentLng
        val mv = mapViewRef.map ?: return@LaunchedEffect
        if (lat != null && lng != null && !initialCenterApplied) {
            mv.mapboxMap.setCamera(
                CameraOptions.Builder()
                    .center(Point.fromLngLat(lng, lat))
                    .zoom(15.0)
                    .build(),
            )
            initialCenterApplied = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.places_new_title)) },
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
            // Mapa sa fiksnim crosshair-om
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).also { mv ->
                            mapViewRef.map = mv
                            mv.mapboxMap.setCamera(
                                CameraOptions.Builder()
                                    .center(Point.fromLngLat(20.46, 44.81))
                                    .zoom(12.0)
                                    .build(),
                            )
                            mv.location.updateSettings { enabled = false }
                            mv.mapboxMap.loadStyle(Style.STANDARD)
                            // Prati kameru — overlay krug se skaluje na svaki move/zoom.
                            mv.mapboxMap.subscribeCameraChanged {
                                val cs = mv.mapboxMap.cameraState
                                cameraLat = cs.center.latitude()
                                cameraZoom = cs.zoom
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                // Overlay krug centriran na crosshair-u, skaluje se sa radius-om.
                val pixelRadius = remember(radius, cameraLat, cameraZoom) {
                    val metersPerPixelWorld = 156543.03392 *
                        kotlin.math.cos(cameraLat * kotlin.math.PI / 180.0) /
                        Math.pow(2.0, cameraZoom)
                    val metersPerPx = metersPerPixelWorld / density.density
                    (radius / metersPerPx).toFloat()
                }
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(
                        color = androidx.compose.ui.graphics.Color(0x334F46E5),
                        radius = pixelRadius,
                        center = center,
                    )
                    drawCircle(
                        color = androidx.compose.ui.graphics.Color(0xFF4F46E5),
                        radius = pixelRadius,
                        center = center,
                        style = Stroke(width = 3f),
                    )
                }
                // Fiksni crosshair na sredini
                Box(
                    modifier = Modifier.align(Alignment.Center),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp),
                    )
                }
                // "Moja lokacija" FAB
                FloatingActionButton(
                    onClick = {
                        val lat = state.currentLat ?: return@FloatingActionButton
                        val lng = state.currentLng ?: return@FloatingActionButton
                        mapViewRef.map?.mapboxMap?.setCamera(
                            CameraOptions.Builder()
                                .center(Point.fromLngLat(lng, lat))
                                .zoom(15.0)
                                .build(),
                        )
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    Icon(Icons.Outlined.MyLocation, contentDescription = null)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.places_tap_map_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text(stringResource(R.string.places_name_label)) },
                    placeholder = { Text(stringResource(R.string.places_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column {
                    Text(
                        stringResource(R.string.places_radius_label, radius.toInt()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = radius,
                        onValueChange = { radius = it },
                        valueRange = PlaceModel.MIN_RADIUS_M.toFloat()..PlaceModel.MAX_RADIUS_M.toFloat(),
                        steps = 8,
                    )
                }
                if (!state.error.isNullOrBlank()) {
                    Text(
                        state.error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(
                    enabled = !state.saving && name.isNotBlank(),
                    onClick = {
                        val mv = mapViewRef.map ?: return@Button
                        val center = mv.mapboxMap.cameraState.center
                        viewModel.createPlace(
                            name = name,
                            lat = center.latitude(),
                            lng = center.longitude(),
                            radius = radius.toInt(),
                            onSuccess = { onBack() },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.places_save))
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { mapViewRef.map = null }
    }
}

private class MapViewHolder2 {
    var map: MapView? = null
}
