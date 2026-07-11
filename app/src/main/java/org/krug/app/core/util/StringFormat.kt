package org.krug.app.core.util

import java.util.Locale

/**
 * Kapitalizuje prvo slovo (locale-aware za srpski). Ostatak ostavlja kao što jeste —
 * korisnik može da napiše akronim/inicijale i ne želimo to da rušimo.
 */
fun String.capitalizeFirstLetter(): String {
    if (isEmpty()) return this
    val first = this[0]
    if (!first.isLetter() || first.isUpperCase()) return this
    return first.titlecase(Locale.forLanguageTag("sr")) + substring(1)
}

/**
 * Skraćuje string na `maxLength` sa ellipsis-om. Bez ovog, `.take(N)` seče na sredini
 * reči tako da izgleda kao nedovršeno ime („Dusica Bajčeta" → „Dusica Baj"). Sa
 * ellipsis-om („Dusica Ba…") user jasno vidi da je tekst kraćen.
 */
fun String.truncateWithEllipsis(maxLength: Int): String {
    if (maxLength <= 1) return take(maxLength)
    return if (length > maxLength) take(maxLength - 1) + "…" else this
}
