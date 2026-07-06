package org.krug.app.ui.brand

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Krug-styled Material3 Switch wrapper. Default Material3 Switch ima OFF state koji
 * izgleda "cheap": uncheckedTrackColor = surfaceContainerHighest (dark grey ~E0E0E0),
 * uncheckedThumbColor = outline (medium grey), uncheckedBorderColor = outline (vidljiva
 * ivica koja daje boxy izgled).
 *
 * Ovaj wrapper daje čistiji izgled:
 * - ON: LogoBlue track (brand), white thumb — sasvim jasno "aktivno".
 * - OFF: light surface track bez border-a, srednji sivi thumb — miran, ne "napuknut".
 */
@Composable
fun KrugSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            uncheckedBorderColor = Color.Transparent,
            disabledCheckedThumbColor = Color.White.copy(alpha = 0.6f),
            disabledCheckedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            disabledCheckedBorderColor = Color.Transparent,
            disabledUncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            disabledUncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f),
            disabledUncheckedBorderColor = Color.Transparent,
        ),
    )
}
