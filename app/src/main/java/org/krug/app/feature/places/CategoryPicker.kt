package org.krug.app.feature.places

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.krug.app.R
import org.krug.app.core.places.PlaceCategory

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPicker(
    selected: PlaceCategory,
    onSelect: (PlaceCategory) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PlaceCategory.values().forEach { cat ->
            FilterChip(
                selected = cat == selected,
                onClick = { onSelect(cat) },
                leadingIcon = {
                    Icon(
                        imageVector = iconFor(cat),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                label = { Text(stringResource(labelFor(cat))) },
            )
        }
    }
}

private fun iconFor(cat: PlaceCategory): ImageVector = when (cat) {
    PlaceCategory.HOME -> Icons.Outlined.Home
    PlaceCategory.SCHOOL -> Icons.Outlined.School
    PlaceCategory.WORK -> Icons.Outlined.Work
    PlaceCategory.GYM -> Icons.Outlined.FitnessCenter
    PlaceCategory.SHOP -> Icons.Outlined.ShoppingBag
    PlaceCategory.OTHER -> Icons.Outlined.MoreHoriz
}

private fun labelFor(cat: PlaceCategory): Int = when (cat) {
    PlaceCategory.HOME -> R.string.places_cat_home
    PlaceCategory.SCHOOL -> R.string.places_cat_school
    PlaceCategory.WORK -> R.string.places_cat_work
    PlaceCategory.GYM -> R.string.places_cat_gym
    PlaceCategory.SHOP -> R.string.places_cat_shop
    PlaceCategory.OTHER -> R.string.places_cat_other
}
