package org.krug.app.feature.circle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.krug.app.R
import org.krug.app.core.circle.CirclePresets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCircleScreen(
    onBack: () -> Unit,
    onCreated: (circleId: String) -> Unit,
    viewModel: CreateCircleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.createdCircleId) {
        state.createdCircleId?.let(onCreated)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.create_circle_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.size(16.dp))
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text(stringResource(R.string.create_circle_name_label)) },
                placeholder = { Text(stringResource(R.string.create_circle_name_placeholder)) },
                isError = state.nameError || state.duplicateError,
                supportingText = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = when {
                                state.duplicateError -> stringResource(R.string.create_circle_error_duplicate)
                                state.nameError -> stringResource(R.string.create_circle_error_empty)
                                else -> ""
                            },
                        )
                        Text("${state.name.length}/${CreateCircleViewModel.NAME_MAX_LENGTH}")
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.size(24.dp))

            Text(
                text = stringResource(R.string.create_circle_color_label),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.size(12.dp))
            ColorPicker(
                selected = state.selectedColor,
                onSelect = viewModel::setColor,
            )

            Spacer(Modifier.size(24.dp))

            Text(
                text = stringResource(R.string.create_circle_icon_label),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.size(12.dp))
            IconPicker(
                selected = state.selectedIcon,
                accentColor = Color(android.graphics.Color.parseColor(state.selectedColor)),
                onSelect = viewModel::setIcon,
            )

            Spacer(Modifier.weight(1f))

            Button(
                onClick = viewModel::submit,
                enabled = !state.creating,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            ) {
                if (state.creating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.size(12.dp))
                }
                Text(stringResource(R.string.create_circle_create_cta))
            }
        }
    }
}

@Composable
private fun ColorPicker(
    selected: String,
    onSelect: (String) -> Unit,
) {
    // Row + SpaceBetween — krugovi se ravnomerno raspoređu ivica-do-ivice, nema viška
    // praznog prostora ni overflow-a na užim ekranima.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CirclePresets.colors.forEach { hex ->
            val isSelected = hex == selected
            val color = Color(android.graphics.Color.parseColor(hex))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color)
                    .then(
                        if (isSelected) {
                            Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                        } else Modifier,
                    )
                    .clickable { onSelect(hex) },
                contentAlignment = Alignment.Center,
            ) { /* selection ring is the border */ }
        }
    }
}

@Composable
private fun IconPicker(
    selected: String,
    accentColor: Color,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        CirclePresets.icons.forEach { key ->
            val isSelected = key == selected
            val icon = CircleIconAssets.forKey(key)
            val label = CircleIconAssets.labelForKey(key)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onSelect(key) }
                    .padding(vertical = 4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) accentColor
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                        )
                        .then(
                            if (isSelected) Modifier.border(2.dp, accentColor, CircleShape)
                            else Modifier,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (isSelected) Color.White
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Spacer(Modifier.size(6.dp))
                Text(
                    text = label,
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
