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
