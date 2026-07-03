package org.krug.app.feature.places

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.krug.app.R
import org.krug.app.core.places.PlaceModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacesScreen(
    onBack: () -> Unit,
    onAddPlace: () -> Unit,
    onShowOnMap: () -> Unit,
    viewModel: PlacesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recentEvents by viewModel.recentEvents.collectAsStateWithLifecycle()
    val presenceByPlace by viewModel.presenceByPlace.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<PlaceModel?>(null) }
    val limitReached = state.places.size >= PlaceModel.FREE_TIER_MAX_PER_CIRCLE

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.places_section_title)) },
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
        floatingActionButton = {
            if (!limitReached) {
                ExtendedFloatingActionButton(
                    onClick = onAddPlace,
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.places_add_cta)) },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
        ) {
            if (state.places.isEmpty()) {
                EmptyState()
            } else {
                // Kompaktne statistike — broj mesta i događaja danas (0h-24h).
                val todayEventCount = remember(recentEvents) {
                    val startOfDay = java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.HOUR_OF_DAY, 0)
                        set(java.util.Calendar.MINUTE, 0)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    recentEvents.count { (it.timestamp?.time ?: 0L) >= startOfDay }
                }
                Text(
                    text = stringResource(
                        R.string.places_stats_line,
                        state.places.size,
                        todayEventCount,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                if (limitReached) {
                    Text(
                        text = stringResource(
                            R.string.places_limit_reached,
                            PlaceModel.FREE_TIER_MAX_PER_CIRCLE,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.places, key = { it.id }) { place ->
                        PlaceRow(
                            place = place,
                            presence = presenceByPlace[place.id].orEmpty(),
                            onClick = {
                                org.krug.app.core.places.PlaceFocusBus.request(
                                    lat = place.lat, lng = place.lng,
                                    name = place.name, radius = place.radius,
                                )
                                onShowOnMap()
                            },
                            onEdit = { viewModel.openEditSheet(place) },
                            onDelete = { pendingDelete = place },
                        )
                    }
                    if (recentEvents.isNotEmpty()) {
                        item {
                            Spacer(Modifier.size(20.dp))
                            Text(
                                stringResource(R.string.places_activity_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        items(recentEvents, key = { it.id }) { event ->
                            EventRow(event)
                        }
                    }
                }
            }
        }
    }

    if (state.sheetOpen) {
        AddEditPlaceSheet(
            editing = state.editingPlace,
            currentLat = state.currentLat,
            currentLng = state.currentLng,
            saving = state.saving,
            error = state.error,
            onDismiss = { viewModel.closeSheet() },
            onSave = { name, lat, lng, radius, category ->
                viewModel.createPlace(name, lat, lng, radius, category) {}
            },
            onUpdate = { placeId, name, radius, category ->
                viewModel.updatePlace(placeId, name, radius, category) {}
            },
        )
    }

    pendingDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.places_delete_confirm_title)) },
            text = { Text(stringResource(R.string.places_delete_confirm_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlace(p.id)
                    pendingDelete = null
                }) {
                    Text(stringResource(R.string.places_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Otkaži") }
            },
        )
    }
}

@Composable
private fun EventRow(event: org.krug.app.core.places.PlaceEventModel) {
    val name = event.userName.ifBlank { "?" }
    val verb = if (event.type == org.krug.app.core.places.PlaceEventModel.TYPE_ENTER) {
        stringResource(R.string.places_activity_verb_enter)
    } else {
        stringResource(R.string.places_activity_verb_exit)
    }
    val timeAgo = event.timestamp?.let { humanTimeAgo(it) } ?: "-"
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Place,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "$name $verb ${event.placeName}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                timeAgo,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun humanTimeAgo(date: java.util.Date): String {
    val diff = System.currentTimeMillis() - date.time
    val mins = diff / 60_000L
    return when {
        mins < 1 -> "sada"
        mins < 60 -> "pre ${mins}min"
        mins < 24 * 60 -> "pre ${mins / 60}h"
        else -> "pre ${mins / 60 / 24}d"
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.Place,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.size(16.dp))
        Text(
            stringResource(R.string.places_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            stringResource(R.string.places_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun PlaceRow(
    place: PlaceModel,
    presence: List<String>,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Place,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    place.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${place.radius} m",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (presence.isNotEmpty()) {
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = stringResource(
                            R.string.places_presence_line,
                            presence.joinToString(", "),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = androidx.compose.ui.graphics.Color(0xFF10B981),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, contentDescription = null)
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
