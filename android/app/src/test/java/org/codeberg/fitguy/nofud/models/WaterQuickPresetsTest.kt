package org.codeberg.fitguy.nofud.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterQuickPresetsTest {

    @Test
    fun defaultPresetsAreValid() {
        assertTrue(WaterQuickPresets.Default.isValid)
        assertEquals(listOf(250, 500, 750), WaterQuickPresets.Default.amountsMl)
    }

    @Test
    fun fromStorageParsesCommaSeparatedMl() {
        val presets = WaterQuickPresets.fromStorage("237,355,473")
        assertEquals(listOf(237, 355, 473), presets.amountsMl)
    }

    @Test
    fun fromStorageRejectsInvalidAndFallsBack() {
        assertEquals(WaterQuickPresets.Default, WaterQuickPresets.fromStorage("250"))
        assertEquals(WaterQuickPresets.Default, WaterQuickPresets.fromStorage("250,250,500"))
        assertEquals(WaterQuickPresets.Default, WaterQuickPresets.fromStorage(null))
    }

    @Test
    fun toStorageRoundTrips() {
        val custom = WaterQuickPresets(listOf(200, 400, 600, 800))
        val raw = WaterQuickPresets.toStorage(custom)
        assertEquals(custom, WaterQuickPresets.fromStorage(raw))
    }

    @Test
    fun flOzConversionIsReasonable() {
        assertEquals(8, WaterAmountFormat.flOzFromMl(237))
        assertTrue(WaterAmountFormat.mlFromFlOz(8) in 230..245)
    }
}
