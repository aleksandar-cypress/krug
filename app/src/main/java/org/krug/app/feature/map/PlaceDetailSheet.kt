package org.krug.app.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import java.util.Date
import org.krug.app.R
import org.krug.app.core.places.PlaceCategory
import org.krug.app.core.places.PlaceEventModel
import org.krug.app.core.places.PlaceModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceDetailSheet(
    place: PlaceModel,
    creatorName: String,
    lastEvent: PlaceEventModel?,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val (colorHex, glyph) = MapMarkers.categoryStyle(place.category)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(colorHex.toColorInt())),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        glyph,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        place.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(labelResFor(place.category)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            InfoRow(
                label = stringResource(R.string.places_detail_radius),
                value = "${place.radius} m",
            )
            InfoRow(
                label = stringResource(R.string.places_detail_creator),
                value = creatorName.ifBlank { "-" },
            )
            InfoRow(
                label = stringResource(R.string.places_detail_last_event),
                value = lastEvent?.let { formatEvent(it) } ?: "-",
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.places_detail_edit))
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        stringResource(R.string.places_detail_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun labelResFor(cat: String): Int = when (cat) {
    PlaceCategory.HOME.name -> R.string.places_cat_home
    PlaceCategory.SCHOOL.name -> R.string.places_cat_school
    PlaceCategory.WORK.name -> R.string.places_cat_work
    PlaceCategory.GYM.name -> R.string.places_cat_gym
    PlaceCategory.SHOP.name -> R.string.places_cat_shop
    else -> R.string.places_cat_other
}

private fun formatEvent(evt: PlaceEventModel): String {
    val name = evt.userName.ifBlank { "?" }
    val when_ = evt.timestamp?.let { humanTimeAgo(it) } ?: "-"
    val verb = if (evt.type == PlaceEventModel.TYPE_ENTER) "stigao/la" else "otišao/la"
    return "$name $verb ($when_)"
}

private fun humanTimeAgo(date: Date): String {
    val diff = System.currentTimeMillis() - date.time
    val mins = diff / 60_000L
    return when {
        mins < 1 -> "sada"
        mins < 60 -> "pre ${mins}min"
        mins < 24 * 60 -> "pre ${mins / 60}h"
        else -> "pre ${mins / 60 / 24}d"
    }
}
