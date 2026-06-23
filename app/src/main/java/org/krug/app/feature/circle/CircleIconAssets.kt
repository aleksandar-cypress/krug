package org.krug.app.feature.circle

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SportsBasketball
import androidx.compose.material.icons.outlined.Work
import androidx.compose.ui.graphics.vector.ImageVector
import org.krug.app.R

/** Mapira iconKey iz CirclePresets na konkretnu Material ikonu. */
object CircleIconAssets {

    fun forKey(key: String): ImageVector = when (key) {
        "family" -> Icons.Outlined.FamilyRestroom
        "friends" -> Icons.Outlined.Groups
        "work" -> Icons.Outlined.Work
        "school" -> Icons.Outlined.School
        "home" -> Icons.Outlined.Home
        "sports" -> Icons.Outlined.SportsBasketball
        "travel" -> Icons.Outlined.Flight
        "event" -> Icons.Outlined.Event
        else -> Icons.Outlined.Groups
    }

    /**
     * Vraća resource ID labele — caller resolvuje sa stringResource() ili context.getString().
     * Promenjeno sa hardcoded String-a u @StringRes za multi-locale support.
     */
    @StringRes
    fun labelResForKey(key: String): Int = when (key) {
        "family" -> R.string.icon_label_family
        "friends" -> R.string.icon_label_friends
        "work" -> R.string.icon_label_work
        "school" -> R.string.icon_label_school
        "home" -> R.string.icon_label_home
        "sports" -> R.string.icon_label_sports
        "travel" -> R.string.icon_label_travel
        "event" -> R.string.icon_label_event
        else -> R.string.icon_label_family // fallback
    }
}
