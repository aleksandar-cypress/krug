package org.krug.app.ui.theme

import androidx.compose.ui.graphics.Color

// Brand: lighter, friendlier indigo + warmer coral.
val BrandIndigo500 = Color(0xFF818CF8) // primary (indigo-400)
val BrandIndigo600 = Color(0xFF6366F1) // pressed / darker tint
val BrandIndigo50 = Color(0xFFEEF2FF)
val BrandCoral500 = Color(0xFFFB7185) // softer rose-400 for other members
val BrandCoral50 = Color(0xFFFFE4E6)

// Logo-extracted boje (iz krug_logo.png) — koriste se kao brand identity
// kroz ceo app (theme primary + svi primary CTA gradient pill-ovi).
val LogoBlue = Color(0xFF3A86C8) // gornja figura, primarni brand
val LogoBlueLight = Color(0xFF5BA0DC) // svetlija varijanta za gradient parove
val LogoBlue50 = Color(0xFFE5F0FA) // svetla pozadina (primaryContainer)
val LogoPink = Color(0xFFE56B8F) // leva — theme secondary (child banner, peer markers)
val LogoPink50 = Color(0xFFFCE7EE) // svetla pozadina (secondaryContainer)
val LogoTeal = Color(0xFF48B09B) // desna — battery ≥50%, online status
val LogoOrange = Color(0xFFF3B250) // donja — battery 20-49%, charging accent

// Neutrals
val NeutralWhite = Color(0xFFFFFFFF)
val NeutralBg = Color(0xFFF8FAFC)
val NeutralSurface = Color(0xFFFFFFFF)
val NeutralBorder = Color(0xFFE2E8F0)
val NeutralText = Color(0xFF0F172A)
val NeutralTextMuted = Color(0xFF64748B)

// Dark — neutral grays (no blue tint).
val DarkBg = Color(0xFF121212)
val DarkSurface = Color(0xFF1E1E1E)
val DarkSurfaceVariant = Color(0xFF2A2A2A)
val DarkText = Color(0xFFE5E7EB)
val DarkTextMuted = Color(0xFFA3A3A3)
