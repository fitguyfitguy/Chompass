package app.chompass.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fill-missing scan truth table (WP2 Batch 1):
 *  - macro gap ⟺ entry value == 0 (entry-level zeros are the Mealie /
 *    logged-by-name signature; legit zeros like black coffee count as gaps
 *    by design — the scan never auto-fills);
 *  - micro gap ⟺ field is null;
 *  - a constituent row is fillable ⟺ non-blank name AND all four macros zero
 *    (partial zeros are legit keto-style rows; blank rows can never match).
 */
class FoodEntryGapScanTest {

    private fun constituent(
        name: String,
        calories: Int = 0,
        protein: Double = 0.0,
        carbs: Double = 0.0,
        fat: Double = 0.0,
    ) = FoodConstituent(
        name = name,
        calories = calories,
        protein = protein,
        carbs = carbs,
        fat = fat,
        servingSizeGrams = 100.0,
    )

    private fun entry(
        calories: Int = 0,
        protein: Double = 0.0,
        carbs: Double = 0.0,
        fat: Double = 0.0,
        fiber: Double? = null,
        sugar: Double? = null,
        constituents: List<FoodConstituent> = emptyList(),
    ) = FoodEntry(
        name = "Test meal",
        calories = calories,
        protein = protein,
        carbs = carbs,
        fat = fat,
        fiber = fiber,
        sugar = sugar,
        source = FoodSource.MANUAL,
        constituents = constituents,
    )

    @Test
    fun mealieShape_allZeroMacros_nullMicros_allZeroRowsCounted() {
        val report = entry(
            constituents = listOf(
                constituent("200 g flour"),
                constituent("2 eggs"),
            ),
        ).gapReport()

        assertEquals(
            setOf(
                GapMacroField.CALORIES,
                GapMacroField.PROTEIN,
                GapMacroField.CARBS,
                GapMacroField.FAT,
            ),
            report.missingMacros,
        )
        // Every micro field null → all 23 catalog fields are gaps.
        assertEquals(MicronutrientField.entries.toSet(), report.missingMicros)
        assertEquals(listOf("200 g flour", "2 eggs"), report.zeroMacroConstituents.map { it.name })
        assertTrue(report.hasGaps)
    }

    @Test
    fun fullyFilledEntry_hasNoGaps() {
        // Every macro non-zero AND every micro field non-null — that is the
        // only shape with no gaps.
        val allMicros = MicronutrientField.entries.fold(MicronutrientValues()) { values, field ->
            values.with(field, 1.0)
        }
        val report = allMicros.applyTo(
            entry(calories = 540, protein = 42.0, carbs = 60.0, fat = 14.0),
        ).gapReport()

        assertTrue(report.missingMacros.isEmpty())
        assertTrue(report.missingMicros.isEmpty())
        assertTrue(report.zeroMacroConstituents.isEmpty())
        assertFalse(report.hasGaps)
    }

    @Test
    fun legitZeroMacros_stillCountAsGaps_byDesign() {
        // Black coffee: no protein/carbs/fat worth recording — still gaps,
        // because 0 and "unknown" are display-equivalent and the user drives
        // every fill from the edit sheet.
        val report = entry(calories = 2).gapReport()

        assertEquals(
            setOf(GapMacroField.PROTEIN, GapMacroField.CARBS, GapMacroField.FAT),
            report.missingMacros,
        )
        assertTrue(report.hasGaps)
    }

    @Test
    fun microGap_exactlyTheNullFields() {
        val report = entry(calories = 100, protein = 3.0, carbs = 20.0, fat = 1.0, fiber = 2.0).gapReport()

        assertFalse(GapMacroField.entries.any { it in report.missingMacros })
        assertTrue(MicronutrientField.FIBER !in report.missingMicros)
        assertTrue(MicronutrientField.SUGAR in report.missingMicros)
        assertEquals(MicronutrientField.entries.size - 1, report.missingMicros.size)
    }

    @Test
    fun partialZeroConstituent_notCounted() {
        // Keto-style row: carbs legitimately zero, rest known → not fillable.
        val report = entry(
            calories = 300,
            protein = 25.0,
            carbs = 2.0,
            fat = 20.0,
            constituents = listOf(constituent("bacon", calories = 300, protein = 25.0, carbs = 0.0, fat = 20.0)),
        ).gapReport()

        assertTrue(report.zeroMacroConstituents.isEmpty())
    }

    @Test
    fun blankNamedAllZeroRow_notCounted() {
        val report = entry(constituents = listOf(constituent("   "))).gapReport()

        assertTrue(report.zeroMacroConstituents.isEmpty())
    }

    @Test
    fun mixedRows_onlyAllZeroNamedRowsCounted() {
        val report = entry(
            constituents = listOf(
                constituent("rice"),
                constituent("olive oil", calories = 120, protein = 0.0, carbs = 0.0, fat = 14.0),
                constituent(""),
            ),
        ).gapReport()

        assertEquals(listOf("rice"), report.zeroMacroConstituents.map { it.name })
    }
}
