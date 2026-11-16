package app.chompass.models

import app.chompass.ui.components.splitDecimalParts
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit switches and serving edits must store the quantity quantized to the
 * tenth grid the quantity wheels display. An unquantized conversion (7.1428
 * oz from 200 g at 28 g/unit) is re-emitted by the wheels as their grid value
 * (7.1) on the next interaction and silently drifts stored grams (device pass
 * 2026-11-16: a 200 g constituent row became 198.8 g).
 */
class ServingQuantityConversionTest {
    @Test
    fun nonGramOption_roundsToTheWheelGrid() {
        val oz = ServingUnitOption(unit = "oz", gramsPerUnit = 28.0)
        assertEquals(7.1, ServingUnitOption.quantityForGrams(200.0, oz), 1e-9)
    }

    @Test
    fun roundingIsNearest_notTruncation() {
        val cup = ServingUnitOption(unit = "cup", gramsPerUnit = 240.0)
        // 400/240 = 1.6667 -> 1.7, matching splitDecimalParts (Codeberg #63).
        assertEquals(1.7, ServingUnitOption.quantityForGrams(400.0, cup), 1e-9)
        val slice = ServingUnitOption(unit = "slice", gramsPerUnit = 30.0)
        // 100/30 = 3.3333 -> 3.3.
        assertEquals(3.3, ServingUnitOption.quantityForGrams(100.0, slice), 1e-9)
    }

    @Test
    fun exactConversionsStayExact() {
        val cup = ServingUnitOption(unit = "cup", gramsPerUnit = 250.0)
        assertEquals(2.0, ServingUnitOption.quantityForGrams(500.0, cup), 1e-9)
    }

    @Test
    fun gramUnitStaysExact_noGridForcing() {
        val grams = ServingUnitOption(unit = "g", gramsPerUnit = 1.0)
        assertEquals(123.456, ServingUnitOption.quantityForGrams(123.456, grams), 1e-9)
    }

    @Test
    fun missingGramsPerUnitFallsBackUnquantized() {
        val unit = ServingUnitOption(unit = "bowl", gramsPerUnit = 0.0)
        assertEquals(42.5, ServingUnitOption.quantityForGrams(42.5, unit), 1e-9)
    }

    @Test
    fun resultReassemblesToTheWheelRepresentation() {
        // The invariant the drift violated: whatever the helper stores must be
        // exactly what SplitDecimalWheelPicker shows and would re-emit.
        val cases = listOf(
            200.0 to ServingUnitOption(unit = "oz", gramsPerUnit = 28.0),
            400.0 to ServingUnitOption(unit = "cup", gramsPerUnit = 240.0),
            150.0 to ServingUnitOption(unit = "piece", gramsPerUnit = 7.0),
            333.0 to ServingUnitOption(unit = "tbsp", gramsPerUnit = 9.0),
        )
        for ((grams, option) in cases) {
            val quantity = ServingUnitOption.quantityForGrams(grams, option)
            val (intPart, tenthsPart) = splitDecimalParts(quantity, 0, 100)
            assertEquals(quantity, intPart + tenthsPart / 10.0, 1e-9)
        }
    }
}
