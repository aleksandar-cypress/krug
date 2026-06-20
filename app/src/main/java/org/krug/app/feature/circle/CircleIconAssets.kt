package org.krug.app.feature.circle

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

    fun labelForKey(key: String): String = when (key) {
        "family" -> "Porodica"
        "friends" -> "Drustvo"
        "work" -> "Posao"
        "school" -> "Škola"
        "home" -> "Komšiluk"
        "sports" -> "Sport"
        "travel" -> "Putovanje"
        "event" -> "Događaj"
        else -> key.replaceFirstChar { it.uppercaseChar() }
    }
}
