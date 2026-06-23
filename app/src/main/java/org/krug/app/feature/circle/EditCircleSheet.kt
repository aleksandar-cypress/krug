package org.krug.app.feature.circle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import org.krug.app.R
import org.krug.app.core.circle.CirclePresets

@Composable
internal fun EditCircleSheet(
    initialName: String,
    initialColor: String,
    initialIcon: String,
    saving: Boolean,
    duplicateError: Boolean,
    onSave: (name: String, colorHex: String, iconKey: String) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableStateOf(initialColor) }
    var icon by remember { mutableStateOf(initialIcon) }
    val nameError = name.isBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.edit_circle_title),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 12.dp),
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(20) },
            label = { Text(stringResource(R.string.edit_circle_name_label)) },
            singleLine = true,
            isError = nameError || duplicateError,
            supportingText = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = when {
                            duplicateError -> stringResource(R.string.edit_circle_error_duplicate)
                            nameError -> stringResource(R.string.edit_circle_error_empty)
                            else -> ""
                        },
                    )
                    Text("${name.length}/20")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.size(20.dp))

        Text(stringResource(R.string.edit_circle_color_label), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(12.dp))
        SheetColorRow(selected = color, onSelect = { color = it })

        Spacer(Modifier.size(20.dp))
        Text(stringResource(R.string.edit_circle_icon_label), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(12.dp))
        SheetIconRow(
            selected = icon,
            accentColor = Color(android.graphics.Color.parseColor(color)),
            onSelect = { icon = it },
        )

        Spacer(Modifier.size(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onCancel,
                enabled = !saving,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.action_cancel))
            }
            Button(
                onClick = { onSave(name, color, icon) },
                enabled = !saving && !nameError,
                modifier = Modifier.weight(1f),
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@Composable
private fun SheetColorRow(selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CirclePresets.colors.forEach { hex ->
            val isSelected = hex == selected
            val c = Color(android.graphics.Color.parseColor(hex))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(c)
                    .then(
                        if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                        else Modifier,
                    )
                    .clickable { onSelect(hex) },
            )
        }
    }
}

@Composable
private fun SheetIconRow(selected: String, accentColor: Color, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        CirclePresets.icons.forEach { key ->
            val isSelected = key == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSelect(key) }
                    .padding(vertical = 4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) accentColor
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = CircleIconAssets.forKey(key),
                        contentDescription = stringResource(CircleIconAssets.labelResForKey(key)),
                        tint = if (isSelected) Color.White
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.size(4.dp))
                Text(
                    text = stringResource(CircleIconAssets.labelResForKey(key)),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
