package org.krug.app.ui.brand

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Krug-styled Material3 RadioButton. Default Material3 selected state koristi primary
 * color što je OK, ali dodaje `disabledColor` fallbacks koji izgledaju bledo. Ovaj
 * wrapper daje jasan brand look:
 * - Selected: LogoBlue (primary brand) sa dovoljno kontrasta u light/dark.
 * - Unselected: outline color (neutralna siva), bez disabled state fade-a.
 */
@Composable
fun KrugRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    RadioButton(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = RadioButtonDefaults.colors(
            selectedColor = MaterialTheme.colorScheme.primary,
            unselectedColor = MaterialTheme.colorScheme.outline,
            disabledSelectedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            disabledUnselectedColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
        ),
    )
}
