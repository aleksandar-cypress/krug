package org.krug.app.feature.driving

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Locale
import org.krug.app.R
import org.krug.app.core.driving.TripModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrivingReportsScreen(
    onBack: () -> Unit,
    viewModel: DrivingReportsViewModel = hiltViewModel(),
) {
    val trips by viewModel.trips.collectAsStateWithLifecycle()
    val range by viewModel.range.collectAsStateWithLifecycle()
    val summary = trips.summarize()
    val title = if (viewModel.displayName.isBlank()) {
        stringResource(R.string.driving_reports_title)
    } else {
        "${stringResource(R.string.driving_reports_title)} — ${viewModel.displayName}"
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RangeChip(
                        label = stringResource(R.string.driving_reports_range_today),
                        selected = range == DrivingRange.TODAY,
                        onClick = { viewModel.setRange(DrivingRange.TODAY) },
                    )
                    RangeChip(
                        label = stringResource(R.string.driving_reports_range_week),
                        selected = range == DrivingRange.WEEK,
                        onClick = { viewModel.setRange(DrivingRange.WEEK) },
                    )
                    RangeChip(
                        label = stringResource(R.string.driving_reports_range_month),
                        selected = range == DrivingRange.MONTH,
                        onClick = { viewModel.setRange(DrivingRange.MONTH) },
                    )
                }
            }
            item { SummaryCard(summary) }
            if (trips.isEmpty()) {
                item {
                    EmptyState()
                }
            } else {
                items(trips, key = { it.id.ifBlank { "${it.startAt?.time ?: 0}" } }) { trip ->
                    TripRow(trip)
                }
            }
        }
    }
}

@Composable
private fun RangeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(),
    )
}

@Composable
private fun SummaryCard(summary: DrivingReportsSummary) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.driving_reports_summary_trips, summary.tripCount),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.driving_reports_summary_distance,
                    String.format(Locale.getDefault(), "%.1f", summary.totalKm),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.driving_reports_summary_max, summary.maxKmh),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun TripRow(trip: TripModel) {
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFmt = remember { SimpleDateFormat("d. MMM", Locale.getDefault()) }
    val start = trip.startAt
    val end = trip.endAt
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    val startText = start?.let { timeFmt.format(it) }.orEmpty()
                    val endText = end?.let { timeFmt.format(it) }.orEmpty()
                    val dateText = start?.let { dateFmt.format(it) }.orEmpty()
                    Text(
                        text = "$dateText, $startText — $endText",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
                Text(
                    text = stringResource(
                        R.string.driving_reports_row_distance,
                        String.format(Locale.getDefault(), "%.1f", trip.distanceKm),
                    ),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(4.dp))
            Row {
                Text(
                    text = stringResource(R.string.driving_reports_row_max, trip.maxSpeedKmh.toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    text = stringResource(R.string.driving_reports_row_duration, (trip.durationSec / 60L).toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.driving_reports_empty_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.driving_reports_empty_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

