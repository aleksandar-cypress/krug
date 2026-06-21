package org.krug.app.core.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StringFormatTest {

    @Test fun `empty string stays empty`() {
        assertThat("".capitalizeFirstLetter()).isEqualTo("")
    }

    @Test fun `lowercase first letter becomes uppercase`() {
        assertThat("sad".capitalizeFirstLetter()).isEqualTo("Sad")
    }

    @Test fun `already uppercase stays as is`() {
        assertThat("Beograd".capitalizeFirstLetter()).isEqualTo("Beograd")
    }

    @Test fun `serbian latin č ć š ž đ capitalize`() {
        assertThat("ćao".capitalizeFirstLetter()).isEqualTo("Ćao")
        assertThat("šuma".capitalizeFirstLetter()).isEqualTo("Šuma")
        assertThat("đak".capitalizeFirstLetter()).isEqualTo("Đak")
    }

    @Test fun `non-letter first char stays as is`() {
        // Brojevi/simboli ne tretiramo — vraćamo kako je. Mogli bi da kapitalizujemo
        // sledeće slovo, ali to bi razrušilo handle "2sat" → "2Sat" što nije željeno.
        assertThat("1abc".capitalizeFirstLetter()).isEqualTo("1abc")
        assertThat(" pera".capitalizeFirstLetter()).isEqualTo(" pera")
    }

    @Test fun `single character lowercase becomes uppercase`() {
        assertThat("a".capitalizeFirstLetter()).isEqualTo("A")
    }

    @Test fun `preserves rest of string casing`() {
        // Akronim posle prvog slova ne dirati — "iOS" ostaje "IOS" nije naša briga.
        assertThat("iPhone".capitalizeFirstLetter()).isEqualTo("IPhone")
    }
}
