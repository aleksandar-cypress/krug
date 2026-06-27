package org.krug.app.feature.circle

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.krug.app.R
import org.krug.app.core.circle.CirclePresets
import org.krug.app.ui.brand.pressScaleClickable

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
            Spacer(Modifier.size(8.dp))

            // Live preview — krug u tačno boji + ikoni koju user trenutno bira. Daje
            // instant feedback umesto da user pogađa kako će izgledati pre create.
            CirclePreview(
                color = Color(android.graphics.Color.parseColor(state.selectedColor)),
                iconKey = state.selectedIcon,
                name = state.name,
            )

            Spacer(Modifier.size(24.dp))

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text(stringResource(R.string.create_circle_name_label)) },
                placeholder = { Text(stringResource(R.string.create_circle_name_placeholder)) },
                isError = state.nameError || state.duplicateError || state.genericError,
                supportingText = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = when {
                                state.duplicateError -> stringResource(R.string.create_circle_error_duplicate)
                                state.nameError -> stringResource(R.string.create_circle_error_empty)
                                state.genericError -> stringResource(R.string.create_circle_error_generic)
                                else -> ""
                            },
                        )
                        Text("${state.name.length}/${CreateCircleViewModel.NAME_MAX_LENGTH}")
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.size(20.dp))

            Text(
                text = stringResource(R.string.create_circle_color_label),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
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
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.size(12.dp))
            IconPicker(
                selected = state.selectedIcon,
                accentColor = Color(android.graphics.Color.parseColor(state.selectedColor)),
                onSelect = viewModel::setIcon,
            )

            Spacer(Modifier.weight(1f))

            CreateButton(
                loading = state.creating,
                onClick = viewModel::submit,
            )
            Spacer(Modifier.size(16.dp))
        }
    }
}

/**
 * Veliki krug preview-a — boja + ikona iz state-a, naziv ispod.
 * Spring animacija na promenu boje/ikone daje "živ" osećaj.
 */
@Composable
private fun CirclePreview(
    color: Color,
    iconKey: String,
    name: String,
) {
    // Spring scale na svakoj promeni boje ili ikone — kratak "bump" daje user-u potvrdu
    // da je tap registrovan. Bez ovog preview izgleda statički.
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "preview-scale",
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(scale)
                .shadow(
                    elevation = 18.dp,
                    shape = CircleShape,
                    clip = false,
                    ambientColor = color,
                    spotColor = color,
                )
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = CircleIconAssets.forKey(iconKey),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(56.dp),
            )
        }
        Spacer(Modifier.size(12.dp))
        Text(
            text = name.ifBlank { stringResource(R.string.create_circle_name_placeholder) },
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = if (name.isBlank()) FontWeight.Normal else FontWeight.Bold,
            ),
            color = if (name.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
        val colorA11y = stringResource(R.string.create_circle_color_a11y)
        CirclePresets.colors.forEach { hex ->
            val isSelected = hex == selected
            val color = Color(android.graphics.Color.parseColor(hex))
            // Bez scale animacije — bila je vizuelno glitchy unutar Row-a sa SpaceBetween-om.
            // Selected state signaliziran border-om što je dovoljno jasno.
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(color)
                    .then(
                        if (isSelected) {
                            Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                        } else Modifier,
                    )
                    .semantics {
                        role = Role.RadioButton
                        this.selected = isSelected
                        contentDescription = colorA11y
                    }
                    .clickable { onSelect(hex) },
            )
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
            val label = stringResource(CircleIconAssets.labelResForKey(key))
            val iconA11y = stringResource(R.string.create_circle_icon_a11y_label, label)
            // Bez scale animacije + bez clip-a na Column-u — scale-up na 1.08x je
            // overflow-ovao Column-ov clip i sekao ivice ikone. Selected state se sada
            // signalizira samo bojom + border-om, što je dovoljno jasno.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .semantics(mergeDescendants = true) {
                        role = Role.RadioButton
                        this.selected = isSelected
                        contentDescription = iconA11y
                    }
                    .clickable { onSelect(key) }
                    .padding(vertical = 4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) accentColor
                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                        // Border je uvek tu (selected: accent, unselected: subtle outline)
                        // tako da neselektovani krugovi nemaju ivice koje se gube u beloj
                        // pozadini ekrana — više ne deluje kao da je krug "isečen".
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) accentColor
                                else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape,
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

/**
 * Create CTA — pill u brand LogoBlue boji (konzistentna sa drugim primary CTA-ima poput
 * CreateCircleFab). Boja kruga se vidi u preview-u gore — button ostaje brand-stable.
 */
@Composable
private fun CreateButton(
    loading: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(org.krug.app.ui.theme.LogoBlue)
            .pressScaleClickable(enabled = !loading, onClick = onClick)
            .height(56.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = Color.White,
            )
            Spacer(Modifier.size(12.dp))
        }
        Text(
            text = stringResource(R.string.create_circle_create_cta),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
        )
    }
}
