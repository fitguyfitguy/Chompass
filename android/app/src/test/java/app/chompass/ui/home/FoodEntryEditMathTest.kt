package app.chompass.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Scaling/parsing math shared by the food entry edit sheets
 * ([FoodResultSheet], [EditFoodEntrySheet], [HomeDialogs]).
 *
 * Extracted after the Codeberg #10 serving-scaling bug class: the block was
 * copy-pasted verbatim between the two sheets, so a fix in one sheet could
 * miss the other. These tests pin the round-trip invariants the sheets rely
 * on (display value -> base value -> scaled value).
 */
class FoodEntryEditMathTest {
    private val math = FoodEntryEditMath(scale = 2.0, emDashText = "—")

    @Test
    fun scaledInt_roundsToWhole() {
        assertEquals(250, math.scaledInt(125))
        assertEquals(0, math.scaledInt(0))
        // Rounding: scale 1.5 makes 125 * 1.5 = 187.5 -> 188
        val half = FoodEntryEditMath(scale = 1.5, emDashText = "—")
        assertEquals(188, half.scaledInt(125))
    }

    @Test
    fun scaledMacro_multiplies() {
        assertEquals(20.0, math.scaledMacro(10.0), 0.0)
        assertEquals(0.0, math.scaledMacro(0.0), 0.0)
    }

    @Test
    fun scaledD_roundsToTenth() {
        assertEquals(21.3, math.scaledD(10.65)!!, 0.0)
        assertNull(math.scaledD(null))
    }

    @Test
    fun displayD_formatsAndFallsBackToEmDash() {
        assertEquals("21.3", math.displayD(21.3))
        assertEquals("—", math.displayD(null))
    }

    @Test
    fun editD_formatsAndFallsBackToEmpty() {
        assertEquals("21.3", math.editD(21.3))
        assertEquals("", math.editD(null))
    }

    @Test
    fun decimalValue_acceptsCommaAndDot() {
        assertEquals(12.5, math.decimalValue("12.5")!!, 0.0)
        assertEquals(12.5, math.decimalValue("12,5")!!, 0.0)
        assertEquals(12.5, math.decimalValue(" 12.5 ")!!, 0.0)
        assertNull(math.decimalValue(""))
        assertNull(math.decimalValue("abc"))
        assertNull(math.decimalValue("-3"))
    }

    @Test
    fun baseDoubleFromText_invertsScaling() {
        // Display shows the scaled value; editing it must write back base units.
        assertEquals(10.0, math.baseDoubleFromText("20.0"), 0.0)
        assertEquals(0.0, math.baseDoubleFromText(""), 0.0)
        assertEquals(0.0, math.baseDoubleFromText("junk"), 0.0)
    }

    @Test
    fun baseOptionalFromText_invertsScalingOrNull() {
        assertEquals(10.0, math.baseOptionalFromText("20.0")!!, 0.0)
        assertNull(math.baseOptionalFromText(""))
        assertNull(math.baseOptionalFromText("junk"))
    }

    @Test
    fun roundTrip_displayToBaseToScaled() {
        // The exact shape the sheets use: scaledD for display, then
        // baseOptionalFromText on the edited text, then scaledD again.
        val base = 7.3
        val displayed = math.displayD(math.scaledD(base))
        val editedBack = math.baseOptionalFromText(displayed)
        assertEquals(base, editedBack!!, 0.05) // 0.1 display rounding tolerance
        assertEquals(math.scaledD(base), math.scaledD(editedBack))
    }

    @Test
    fun zeroScale_doesNotDivideByZero() {
        // Original behavior: scale is coerced to 0.0001, so no crash; the
        // value is just huge. The invariant is "no ArithmeticException".
        val zero = FoodEntryEditMath(scale = 0.0, emDashText = "—")
        assertEquals(10.0 / 0.0001, zero.baseDoubleFromText("10.0"), 0.0)
        assertEquals(10.0 / 0.0001, zero.baseOptionalFromText("10.0")!!, 0.0)
    }

    @Test
    fun parseDecimalValue_topLevelMatchesInstance() {
        assertEquals(math.decimalValue("3,14"), parseDecimalValue("3.14"))
        assertNull(parseDecimalValue(""))
    }
}
