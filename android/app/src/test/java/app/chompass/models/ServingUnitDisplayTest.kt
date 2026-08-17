package app.chompass.models

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Display mapping for the app-generated "serving" unit (OFF barcode lookup /
 * AI fallback): the raw unit string is English, so the UI passes localized
 * labels and [ServingUnitOption.displayUnit] renders them instead.
 */
class ServingUnitDisplayTest {
    private val serving = ServingUnitOption(unit = "serving", gramsPerUnit = 250.0, quantity = 1.0)

    @Test
    fun displayUnit_localizesAppGeneratedServingUnit() {
        // Localized labels win over the raw English unit.
        assertEquals("Portion", serving.displayUnit(1.0, "Portion", "Portionen"))
        assertEquals("Portionen", serving.displayUnit(2.0, "Portion", "Portionen"))
        assertEquals("Portionen", serving.displayUnit(1.5, "Portion", "Portionen"))
        // Null quantity (non-selected dropdown item) reads as singular.
        assertEquals("Portion", serving.displayUnit(null, "Portion", "Portionen"))
    }

    @Test
    fun displayUnit_mapsServingsIdToo() {
        val servings = ServingUnitOption(unit = "servings", gramsPerUnit = 250.0)
        assertEquals("Portionen", servings.displayUnit(3.0, "Portion", "Portionen"))
    }

    @Test
    fun displayUnit_fallsBackToRawEnglishWithoutLabels() {
        assertEquals("serving", serving.displayUnit(1.0))
        assertEquals("servings", serving.displayUnit(2.0))
    }

    @Test
    fun displayUnit_ignoresLabelsForNonServingUnits() {
        val slice = ServingUnitOption(unit = "slice", gramsPerUnit = 30.0)
        assertEquals("slices", slice.displayUnit(2.0, "Portion", "Portionen"))
        val grams = ServingUnitOption.grams
        assertEquals("g", grams.displayUnit(2.0, "Portion", "Portionen"))
    }
}
