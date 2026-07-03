package org.krug.app.feature.places

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.krug.app.R
import org.krug.app.core.places.PlaceModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditPlaceSheet(
    editing: PlaceModel?,
    currentLat: Double?,
    currentLng: Double?,
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (name: String, lat: Double, lng: Double, radius: Int) -> Unit,
    onUpdate: (placeId: String, name: String, radius: Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var radius by remember { mutableStateOf((editing?.radius ?: PlaceModel.DEFAULT_RADIUS_M).toFloat()) }
    var pickedLat by remember { mutableStateOf(editing?.lat) }
    var pickedLng by remember { mutableStateOf(editing?.lng) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(
                    if (editing != null) R.string.places_edit_title else R.string.places_new_title,
                ),
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
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
                    text = stringResource(R.string.places_radius_label, radius.toInt()),
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = radius,
                    onValueChange = { radius = it },
                    valueRange = PlaceModel.MIN_RADIUS_M.toFloat()..PlaceModel.MAX_RADIUS_M.toFloat(),
                    steps = 8,
                )
            }
            if (editing == null) {
                OutlinedButton(
                    onClick = {
                        if (currentLat != null && currentLng != null) {
                            pickedLat = currentLat
                            pickedLng = currentLng
                        }
                    },
                    enabled = currentLat != null && currentLng != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val label = if (pickedLat != null) {
                        "✓ ${stringResource(R.string.places_use_current_location)}"
                    } else {
                        stringResource(R.string.places_use_current_location)
                    }
                    Text(label)
                }
            }
            if (!error.isNullOrBlank()) {
                Text(
                    text = error,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss, enabled = !saving) {
                    Text("Otkaži")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = !saving && name.isNotBlank() &&
                        (editing != null || (pickedLat != null && pickedLng != null)),
                    onClick = {
                        if (editing != null) {
                            onUpdate(editing.id, name, radius.toInt())
                        } else {
                            onSave(name, pickedLat!!, pickedLng!!, radius.toInt())
                        }
                    },
                ) {
                    Text(stringResource(R.string.places_save))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
