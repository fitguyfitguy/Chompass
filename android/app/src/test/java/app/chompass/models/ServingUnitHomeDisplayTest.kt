package app.chompass.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Diary-card serving echo (Codeberg #65): [ServingUnitOption.homeDisplaySelection]
 * resolves the stored serving selection for display, or returns null so the
 * row keeps its grams line. Stored quantity wins over re-dividing grams;
 * stale / gram / invalid ids fall back silently.
 */
class ServingUnitHomeDisplayTest {
    private val oz = ServingUnitOption(unit = "oz", gramsPerUnit = 28.35)
    private val slice = ServingUnitOption(unit = "slice", gramsPerUnit = 30.0)

    @Test
    fun homeDisplaySelection_prefersStoredQuantity() {
        // Re-dividing would give float noise (56.7 g / 28.35); the stored 2 wins.
        val result = ServingUnitOption.homeDisplaySelection(
            selectedUnit = "oz",
            selectedQuantity = 2.0,
            totalGrams = 56.7,
            options = listOf(oz),
        )
        assertEquals(2.0, result?.first ?: -1.0, 1e-9)
        assertEquals(oz.id, result?.second?.id)
    }

    @Test
    fun homeDisplaySelection_normalizesCaseAndWhitespace() {
        val result = ServingUnitOption.homeDisplaySelection(
            selectedUnit = "  OZ ",
            selectedQuantity = 2.0,
            totalGrams = 56.7,
            options = listOf(slice, oz),
        )
        assertEquals(2.0, result?.first ?: -1.0, 1e-9)
        assertEquals("oz", result?.second?.id)
    }

    @Test
    fun homeDisplaySelection_nullBlankOrGramSelectionReturnsNull() {
        assertNull(ServingUnitOption.homeDisplaySelection(null, 2.0, 56.7, listOf(oz)))
        assertNull(ServingUnitOption.homeDisplaySelection("", 2.0, 56.7, listOf(oz)))
        assertNull(ServingUnitOption.homeDisplaySelection("   ", 2.0, 56.7, listOf(oz)))
        assertNull(ServingUnitOption.homeDisplaySelection("g", 1.0, 56.7, listOf(oz)))
        assertNull(ServingUnitOption.homeDisplaySelection("grams", 3.0, 300.0, listOf(oz)))
    }

    @Test
    fun homeDisplaySelection_staleIdReturnsNull() {
        // Option deleted via trash, or synced from a newer build: fall back to grams.
        assertNull(ServingUnitOption.homeDisplaySelection("bowl", 1.0, 300.0, listOf(oz, slice)))
    }

    @Test
    fun homeDisplaySelection_derivesQuantityWhenStoredMissing() {
        val result = ServingUnitOption.homeDisplaySelection(
            selectedUnit = "oz",
            selectedQuantity = null,
            totalGrams = 56.7,
            options = listOf(oz),
        )
        assertEquals(56.7 / 28.35, result?.first ?: -1.0, 1e-9)
    }

    @Test
    fun homeDisplaySelection_nonPositiveStoredQuantityFallsBackToDerivation() {
        assertEquals(56.7 / 28.35, ServingUnitOption.homeDisplaySelection("oz", 0.0, 56.7, listOf(oz))?.first ?: -1.0, 1e-9)
        assertEquals(56.7 / 28.35, ServingUnitOption.homeDisplaySelection("oz", -3.0, 56.7, listOf(oz))?.first ?: -1.0, 1e-9)
        // No usable stored quantity and no total grams either -> not resolvable.
        assertNull(ServingUnitOption.homeDisplaySelection("oz", 0.0, null, listOf(oz)))
    }

    @Test
    fun homeDisplaySelection_invalidOptionByIdReturnsNull() {
        assertNull(
            ServingUnitOption.homeDisplaySelection(
                "cup",
                1.0,
                240.0,
                listOf(ServingUnitOption(unit = "cup", gramsPerUnit = 0.0)),
            )
        )
        assertNull(
            ServingUnitOption.homeDisplaySelection(
                "cup",
                null,
                240.0,
                listOf(ServingUnitOption(unit = "cup", gramsPerUnit = -5.0)),
            )
        )
    }

    @Test
    fun homeDisplaySelection_followsPencilRename() {
        // Pencil rename (#59) changes the id to the new name; the old id no
        // longer resolves and the new one does with the same stored quantity.
        val wedge = ServingUnitOption(unit = "wedge", gramsPerUnit = 30.0)
        assertNull(ServingUnitOption.homeDisplaySelection("slice", 1.0, 60.0, listOf(wedge)))
        val result = ServingUnitOption.homeDisplaySelection("wedge", 2.0, 60.0, listOf(wedge))
        assertEquals(2.0, result?.first ?: -1.0, 1e-9)
        assertEquals(wedge.normalizedUnit, result?.second?.id)
    }
}
