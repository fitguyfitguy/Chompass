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

    @Test
    fun displayUnit_localizesCulinaryUnits() {
        val labels = mapOf(
            "cup" to ("Tasse" to "Tassen"),
            "tbsp" to ("EL" to "EL"),
            "tsp" to ("TL" to "TL"),
        )
        val cup = ServingUnitOption(unit = "cup", gramsPerUnit = 240.0)
        assertEquals("Tasse", cup.displayUnit(1.0, culinaryLabels = labels))
        assertEquals("Tassen", cup.displayUnit(2.1, culinaryLabels = labels))
        assertEquals("Tasse", cup.displayUnit(null, culinaryLabels = labels))

        val tblsp = ServingUnitOption(unit = "tblsp", gramsPerUnit = 15.0)
        assertEquals("EL", tblsp.displayUnit(2.0, culinaryLabels = labels))

        val tsp = ServingUnitOption(unit = "teaspoon", gramsPerUnit = 5.0)
        assertEquals("TL", tsp.displayUnit(3.0, culinaryLabels = labels))
    }

    @Test
    fun displayUnit_fallsBackToRawEnglishWithoutCulinaryLabels() {
        val cup = ServingUnitOption(unit = "cup", gramsPerUnit = 240.0)
        assertEquals("cup", cup.displayUnit(1.0))
        assertEquals("cups", cup.displayUnit(2.0))
    }

    @Test
    fun culinaryUnitKey_mapsAliases() {
        assertEquals("cup", ServingUnitOption.culinaryUnitKey("cups"))
        assertEquals("tbsp", ServingUnitOption.culinaryUnitKey("tblsp"))
        assertEquals("tbsp", ServingUnitOption.culinaryUnitKey("tablespoon"))
        assertEquals("tsp", ServingUnitOption.culinaryUnitKey("teaspoons"))
        assertEquals(null, ServingUnitOption.culinaryUnitKey("slice"))
    }
}
