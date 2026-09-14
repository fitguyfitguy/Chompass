package app.chompass.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class EnergyFormatTest {
    @Test
    fun conversionVectors() {
        assertEquals(0, EnergyFormat.kcalToKj(0))
        assertEquals(4, EnergyFormat.kcalToKj(1))
        assertEquals(8368, EnergyFormat.kcalToKj(2000))
        assertEquals(5021, EnergyFormat.kcalToKj(1200))
        assertEquals(1000, EnergyFormat.kcalToKj(239))
    }

    @Test
    fun kcalQuantityIsIdentity() {
        assertEquals(2000, EnergyFormat.quantity(2000, EnergyUnit.KCAL))
        assertEquals(2000, EnergyFormat.toKcal(2000, EnergyUnit.KCAL))
    }

    @Test
    fun kjRoundTripKnownVector() {
        assertEquals(8368, EnergyFormat.quantity(2000, EnergyUnit.KJ))
        assertEquals(2000, EnergyFormat.toKcal(8368, EnergyUnit.KJ))
    }

    @Test
    fun fromStorageDefaultsToKcal() {
        assertEquals(EnergyUnit.KCAL, EnergyUnit.fromStorage(null))
        assertEquals(EnergyUnit.KCAL, EnergyUnit.fromStorage(""))
        assertEquals(EnergyUnit.KCAL, EnergyUnit.fromStorage("kcal"))
        assertEquals(EnergyUnit.KJ, EnergyUnit.fromStorage("kj"))
        assertEquals(EnergyUnit.KJ, EnergyUnit.fromStorage("KJ"))
        assertEquals("kcal", EnergyUnit.toStorage(EnergyUnit.KCAL))
        assertEquals("kj", EnergyUnit.toStorage(EnergyUnit.KJ))
    }

    @Test
    fun kcalToKjThenToKcalMatchesExceptCollisions() {
        for (n in 0..6000) {
            val back = EnergyFormat.toKcal(EnergyFormat.kcalToKj(n), EnergyUnit.KJ)
            // Two adjacent kcal can map to one kJ; round-trip stays within 1 kcal.
            assertTrue("n=$n back=$back", abs(back - n) <= 1)
        }
    }

    @Test
    fun quantityOfToKcalStaysWithinHalfStep() {
        // Arbitrary kJ (wheel picks) snap to the integer-kcal grid (~4.184 kJ).
        // Half-step is ~2 kJ; the plan's "within 1" bound is tighter than the grid.
        for (kj in 0..25_000) {
            val shown = EnergyFormat.quantity(EnergyFormat.toKcal(kj, EnergyUnit.KJ), EnergyUnit.KJ)
            assertTrue("kj=$kj shown=$shown", abs(shown - kj) <= 2)
        }
    }

    @Test
    fun wheelStepKcalUnchanged() {
        assertEquals(1, EnergyFormat.wheelStep(1, EnergyUnit.KCAL))
        assertEquals(10, EnergyFormat.wheelStep(10, EnergyUnit.KCAL))
        assertEquals(50, EnergyFormat.wheelStep(50, EnergyUnit.KCAL))
    }

    @Test
    fun wheelStepKjRoundedTens() {
        assertEquals(10, EnergyFormat.wheelStep(1, EnergyUnit.KJ))
        assertEquals(10, EnergyFormat.wheelStep(2, EnergyUnit.KJ))
        assertEquals(50, EnergyFormat.wheelStep(10, EnergyUnit.KJ))
        assertEquals(200, EnergyFormat.wheelStep(50, EnergyUnit.KJ))
    }
}
