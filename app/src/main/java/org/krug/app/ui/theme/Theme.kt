package org.krug.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    // Primary = LogoBlue (gornja figura logoa) — sve Material Button-i,
    // primary text-ovi, icon tint-ovi sad slede brand identity logoa.
    primary = LogoBlue,
    onPrimary = NeutralWhite,
    primaryContainer = LogoBlue50,
    onPrimaryContainer = LogoBlue,
    // Secondary = LogoPink (leva figura logoa) — child mode banner, peer markers,
    // theme-driven secondary container i akcenti.
    secondary = LogoPink,
    onSecondary = NeutralWhite,
    secondaryContainer = LogoPink50,
    onSecondaryContainer = LogoPink,
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
