package org.krug.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = BrandIndigo500,
    onPrimary = NeutralWhite,
    primaryContainer = BrandIndigo50,
    onPrimaryContainer = BrandIndigo600,
    secondary = BrandCoral500,
    onSecondary = NeutralWhite,
    secondaryContainer = BrandCoral50,
    onSecondaryContainer = BrandCoral500,
    background = NeutralBg,
    onBackground = NeutralText,
    surface = NeutralSurface,
    onSurface = NeutralText,
    surfaceVariant = NeutralBg,
    onSurfaceVariant = NeutralTextMuted,
    outline = NeutralBorder,
)

@Composable
fun KrugTheme(
    content: @Composable () -> Unit,
) {
    // Krug is always light-themed — branding is fixed regardless of system dark mode.
    MaterialTheme(
        colorScheme = LightColors,
        typography = KrugTypography,
        content = content,
    )
}
