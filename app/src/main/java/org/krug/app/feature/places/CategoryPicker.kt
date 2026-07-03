package org.krug.app.feature.places

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
                label = { Text(stringResource(labelFor(cat))) },
            )
        }
    }
}

private fun labelFor(cat: PlaceCategory): Int = when (cat) {
    PlaceCategory.HOME -> R.string.places_cat_home
    PlaceCategory.SCHOOL -> R.string.places_cat_school
    PlaceCategory.WORK -> R.string.places_cat_work
    PlaceCategory.GYM -> R.string.places_cat_gym
    PlaceCategory.SHOP -> R.string.places_cat_shop
    PlaceCategory.OTHER -> R.string.places_cat_other
}
