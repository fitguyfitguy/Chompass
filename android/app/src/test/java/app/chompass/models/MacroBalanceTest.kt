package app.chompass.models

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure rebalance semantics behind every calorie-target write path. Golden
 * cross-app table lives in `MacroBalanceParityTest`; this file pins the
 * invariants the plan calls out (sum identity, floors, locked-macro safety).
 */
class MacroBalanceTest {

    @Test
    fun standardCut_carbsAbsorb_proteinAndFatKept() {
        val g = MacroBalance.rebalance(
            calories = 1500, protein = 150, carbs = 215, fat = 60,
            mode = MacroBalance.Mode.STANDARD, locked = emptySet(), fatFloorG = 48,
        )
        assertEquals(150, g.protein)
        assertEquals(90, g.carbs)
        assertEquals(60, g.fat)
        assertEquals(1500, g.kcal)
    }

    @Test
    fun standardInfeasible_proteinYieldsButFloorKept() {
        val g = MacroBalance.rebalance(
            calories = 1000, protein = 150, carbs = 0, fat = 60,
            mode = MacroBalance.Mode.STANDARD, locked = emptySet(), fatFloorG = 48,
        )
        assertEquals(142, g.protein)
        assertEquals(0, g.carbs)
        assertEquals(48, g.fat)
        assertEquals(1000, g.kcal)
    }

    @Test
    fun ketoFill_fatAbsorbs() {
        val g = MacroBalance.rebalance(
            calories = 1500, protein = 150, carbs = 30, fat = 70,
            mode = MacroBalance.Mode.KETO, locked = emptySet(), fatFloorG = 57,
        )
        assertEquals(150, g.protein)
        assertEquals(30, g.carbs)
        assertEquals(86, g.fat)
    }

    @Test
    fun ketoInfeasible_proteinYields_floorKept() {
        val g = MacroBalance.rebalance(
            calories = 1000, protein = 150, carbs = 30, fat = 70,
            mode = MacroBalance.Mode.KETO, locked = emptySet(), fatFloorG = 57,
        )
        assertEquals(91, g.protein)
        assertEquals(30, g.carbs)
        assertEquals(57, g.fat)
        assertEquals(997, g.kcal)
    }

    @Test
    fun lockedProteinNeverChanges_floorBreaksLast() {
        val g = MacroBalance.rebalance(
            calories = 1000, protein = 150, carbs = 0, fat = 60,
            mode = MacroBalance.Mode.STANDARD, locked = setOf(AutoBalanceMacro.PROTEIN), fatFloorG = 48,
        )
        assertEquals(150, g.protein)
        assertEquals(1, g.carbs)
        assertEquals(44, g.fat)
        assertEquals(1000, g.kcal)
    }

    @Test
    fun allLocked_targetUnfitReturnsInputsUnchanged() {
        val locked = setOf(AutoBalanceMacro.PROTEIN, AutoBalanceMacro.CARBS, AutoBalanceMacro.FAT)
        val g = MacroBalance.rebalance(
            calories = 1000, protein = 150, carbs = 300, fat = 60,
            mode = MacroBalance.Mode.STANDARD, locked = locked, fatFloorG = 48,
        )
        assertEquals(150, g.protein)
        assertEquals(300, g.carbs)
        assertEquals(60, g.fat)
    }

    @Test
    fun zeroCalories_zeroesUnlockedMacros() {
        val g = MacroBalance.rebalance(
            calories = 0, protein = 150, carbs = 215, fat = 60,
            mode = MacroBalance.Mode.STANDARD, locked = emptySet(), fatFloorG = 48,
        )
        assertEquals(0, g.kcal)
    }

    @Test
    fun negativeInputsClampedToZero() {
        val g = MacroBalance.rebalance(
            calories = -5, protein = -10, carbs = -10, fat = -10,
            mode = MacroBalance.Mode.STANDARD, locked = emptySet(), fatFloorG = 0,
        )
        assertEquals(0, g.kcal)
    }
}
