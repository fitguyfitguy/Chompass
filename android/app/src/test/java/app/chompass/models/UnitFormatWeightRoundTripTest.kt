package app.chompass.models

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * Codeberg #82/#73: weight is stored in kg, but the imperial wheel picks tenths
 * of a lb. The old 0.1 kg storage grid snapped the kg value to a tenth and the
 * lbs display drifted (275.0 lbs -> 124.7 kg -> 274.9 lbs). The storage grid is
 * now fine enough (0.01 kg) that every one-decimal lbs pick round-trips exactly.
 */
class UnitFormatWeightRoundTripTest {
    /** Reporter values: 275 from #82; 151.6, 171.6, 161.6 from the #73 thread. */
    private val reporterLbsValues = listOf(275.0, 151.6, 171.6, 161.6)

    @Test
    fun lbsPickSurvivesStorageAndDisplay() {
        for (lbs in reporterLbsValues) {
            val stored = UnitFormat.roundKgToHundredths(UnitFormat.lbsToKg(lbs))
            val shown = Math.round(UnitFormat.kgToLbs(stored) * 10.0) / 10.0
            assertEquals("entered $lbs lbs must display back as $lbs", lbs, shown, 0.0)
        }
    }

    @Test
    fun lbsDisplayStringMatchesEntry() {
        val expected = mapOf(275.0 to "275.0 lbs", 151.6 to "151.6 lbs", 171.6 to "171.6 lbs", 161.6 to "161.6 lbs")
        for ((lbs, text) in expected) {
            val stored = UnitFormat.roundKgToHundredths(UnitFormat.lbsToKg(lbs))
            assertEquals(text, UnitFormat.weight(stored, useMetric = false, locale = Locale.US))
        }
    }

    @Test
    fun storedKgValuesAreOnTheHundredthsGrid() {
        // 275.0 lbs = 124.73805 kg; the old grid stored 124.7.
        val expectedStorage = mapOf(275.0 to 124.74, 151.6 to 68.76, 171.6 to 77.84, 161.6 to 73.3)
        for ((lbs, kg) in expectedStorage) {
            assertEquals(kg, UnitFormat.roundKgToHundredths(UnitFormat.lbsToKg(lbs)), 0.0)
        }
    }

    @Test
    fun kgWheelValuesPassThroughUnchanged() {
        // Metric picks sit on the 0.1 kg wheel and must survive bit-identically.
        for (kg in listOf(80.1, 80.2, 80.3, 124.7, 55.5, 90.0)) {
            assertEquals(kg, UnitFormat.roundKgToHundredths(kg), 0.0)
        }
    }
}
