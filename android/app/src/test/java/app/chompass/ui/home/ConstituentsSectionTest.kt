package app.chompass.ui.home

import app.chompass.models.FoodConstituent
import app.chompass.models.ServingUnitOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConstituentsSectionTest {
    private val piece = ServingUnitOption(unit = "piece", gramsPerUnit = 50.0, quantity = 1.0)

    @Test
    fun applyConstituentQuantity_scalesMacrosAndMicros() {
        val row = FoodConstituent(
            name = "Bread",
            calories = 100,
            protein = 4.0,
            carbs = 20.0,
            fat = 1.0,
            servingSizeGrams = 50.0,
            servingUnitOptions = listOf(piece),
            sugar = 2.0,
            sodium = 150.0,
        )
        val out = applyConstituentQuantity(row, qty = 2.0, option = piece)
        assertEquals(100.0, out.servingSizeGrams, 0.001)
        assertEquals(200, out.calories)
        assertEquals(8.0, out.protein, 0.001)
        // #86 review fix: per-row micros follow the grams factor like every
        // other mass path (they used to stay stale while macros scaled).
        assertEquals(4.0, out.sugar!!, 0.001)
        assertEquals(300.0, out.sodium!!, 0.001)
        assertNull(out.calcium) // absent stays absent
        assertEquals("piece", out.selectedServingUnit)
        assertEquals(2.0, out.selectedServingQuantity!!, 0.001)
    }

    @Test
    fun applyConstituentQuantity_factorOneKeepsMicroPrecision() {
        val row = FoodConstituent(
            name = "Bread",
            calories = 100,
            protein = 4.0,
            carbs = 20.0,
            fat = 1.0,
            servingSizeGrams = 50.0,
            servingUnitOptions = listOf(piece),
            fiber = 0.45,
        )
        val out = applyConstituentQuantity(row, qty = 1.0, option = piece)
        // Identity at factor 1 (FoodConstituent.microsScaled parity with the
        // PWA twin): 0.45 survives, it must not round to 0.5.
        assertEquals(0.45, out.fiber!!, 0.0)
    }

    private fun gramsRow(name: String, grams: Double) = FoodConstituent(
        name = name,
        calories = grams.toInt(),
        protein = 1.0,
        carbs = 1.0,
        fat = 1.0,
        servingSizeGrams = grams,
    )

    @Test
    fun commit_scaleOne_updatesOnlyEditedRowAndMealSum() {
        val commit = commitConstituentDisplayEdit(
            listOf(gramsRow("A", 250.0), gramsRow("B", 150.0)),
            scale = 1.0,
        )
        assertEquals(250.0, commit.bases[0].servingSizeGrams, 0.001)
        assertEquals(150.0, commit.bases[1].servingSizeGrams, 0.001)
        assertEquals(400.0, commit.baseSum, 0.001)
        assertEquals(400.0, commit.displaySum, 0.001)
    }

    @Test
    fun commit_scaleTwo_unscalesEditedRow_othersStay() {
        val commit = commitConstituentDisplayEdit(
            listOf(gramsRow("A", 500.0), gramsRow("B", 300.0)),
            scale = 2.0,
        )
        assertEquals(250.0, commit.bases[0].servingSizeGrams, 0.001)
        assertEquals(150.0, commit.bases[1].servingSizeGrams, 0.001)
        assertEquals(400.0, commit.baseSum, 0.001)
        assertEquals(800.0, commit.displaySum, 0.001)
    }

    @Test
    fun commit_scaleTwo_decrease_doesNotLockDisplayIntoBase() {
        val commit = commitConstituentDisplayEdit(
            listOf(gramsRow("A", 200.0), gramsRow("B", 300.0)),
            scale = 2.0,
        )
        assertEquals(100.0, commit.bases[0].servingSizeGrams, 0.001)
        assertEquals(150.0, commit.bases[1].servingSizeGrams, 0.001)
        assertEquals(250.0, commit.baseSum, 0.001)
        assertEquals(500.0, commit.displaySum, 0.001)
    }
}
