package org.krug.app.core.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DeviceNamesTest {

    @Test fun `empty input returns empty`() {
        assertThat(DeviceNames.friendly("")).isEqualTo("")
    }

    @Test fun `unknown model returns input unchanged`() {
        assertThat(DeviceNames.friendly("SOMEUNKNOWNDEVICE")).isEqualTo("SOMEUNKNOWNDEVICE")
        assertThat(DeviceNames.friendly("OnePlus 12")).isEqualTo("OnePlus 12")
    }

    @Test fun `samsung galaxy s24 ultra resolved`() {
        // SM-S928B → Galaxy S24 Ultra (model deo se ekstraktuje posle space-a).
        assertThat(DeviceNames.friendly("samsung SM-S928B")).isEqualTo("samsung Galaxy S24 Ultra")
    }

    @Test fun `samsung galaxy a37 resolved`() {
        assertThat(DeviceNames.friendly("Samsung SM-A376B")).isEqualTo("Samsung Galaxy A37 5G")
    }

    @Test fun `bare model code without brand prefix gets samsung default`() {
        // Bez brand prefiksa, koristi "Samsung" kao brand.
        assertThat(DeviceNames.friendly("SM-S921U")).isEqualTo("Samsung Galaxy S24")
    }

    @Test fun `xiaomi mi 11 resolved without brand duplication`() {
        // Build.MANUFACTURER za Xiaomi je "Xiaomi". Friendly name "Mi 11" → "Xiaomi Mi 11"
        // (ne "Xiaomi Xiaomi Mi 11").
        assertThat(DeviceNames.friendly("Xiaomi 21081111RG")).isEqualTo("Xiaomi Mi 11")
    }

    @Test fun `redmi note 11 resolved`() {
        // Redmi name već uključuje brand — ne duplira-mo.
        assertThat(DeviceNames.friendly("Xiaomi 2201117TG")).isEqualTo("Redmi Note 11")
    }

    @Test fun `poco f3 resolved`() {
        assertThat(DeviceNames.friendly("Xiaomi 2104290C")).isEqualTo("POCO F3")
    }

    @Test fun `case insensitive prefix match`() {
        assertThat(DeviceNames.friendly("samsung sm-s928")).isEqualTo("samsung Galaxy S24 Ultra")
    }

    @Test fun `trims whitespace before matching`() {
        assertThat(DeviceNames.friendly("  Samsung SM-A376B  ")).isEqualTo("Samsung Galaxy A37 5G")
    }
}
