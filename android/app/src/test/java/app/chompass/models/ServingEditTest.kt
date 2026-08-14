package app.chompass.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServingEditTest {
    private fun edit(
        selectedUnitId: String,
        selectedOption: ServingUnitOption,
        unitOptions: List<ServingUnitOption>,
        name: String,
        grams: Double,
    ): ServingEditResult? =
        ServingUnitOption.servingEdit(selectedUnitId, selectedOption, unitOptions, name, grams)

    @Test
    fun nonGramUnit_renameAndWeightInPlace() {
        val slice = ServingUnitOption(unit = "slice", gramsPerUnit = 120.0)
        val result = edit("slice", slice, listOf(slice), "big slice", 180.0)!!
        assertEquals("big slice", result.updated.unit)
        assertEquals(180.0, result.updated.gramsPerUnit, 0.001)
        assertEquals(listOf("big slice"), result.options.map { it.id })
    }

    @Test
    fun nonGramUnit_otherOptionsStay() {
        val slice = ServingUnitOption(unit = "slice", gramsPerUnit = 120.0)
        val cup = ServingUnitOption(unit = "cup", gramsPerUnit = 250.0)
        val result = edit("slice", slice, listOf(slice, cup), "big slice", 180.0)!!
        assertEquals(listOf("big slice", "cup"), result.options.map { it.id })
        assertEquals(180.0, result.options.first().gramsPerUnit, 0.001)
        assertEquals(250.0, result.options[1].gramsPerUnit, 0.001)
    }

    @Test
    fun plainGrams_addsCustomServingAlongside() {
        val grams = ServingUnitOption.grams
        val result = edit("g", grams, emptyList(), "bowl", 300.0)!!
        assertEquals("bowl", result.updated.unit)
        assertEquals(300.0, result.updated.gramsPerUnit, 0.001)
        assertEquals(listOf("bowl"), result.options.map { it.id })
        // The grams unit itself stays untouched (1 g identity preserved).
        assertEquals(1.0, ServingUnitOption.grams.gramsPerUnit, 0.001)
    }

    @Test
    fun plainGrams_withExistingOptions_customServingAppended() {
        val grams = ServingUnitOption.grams
        val slice = ServingUnitOption(unit = "slice", gramsPerUnit = 120.0)
        val result = edit("g", grams, listOf(slice), "bowl", 300.0)!!
        assertEquals(listOf("slice", "bowl"), result.options.map { it.id })
    }

    @Test
    fun plainGrams_dropStoredGramOptions_beforeAddingCustom() {
        val grams = ServingUnitOption(unit = "grams", gramsPerUnit = 1.0)
        val result = edit("grams", grams, listOf(grams), "bowl", 300.0)!!
        assertEquals(listOf("bowl"), result.options.map { it.id })
    }

    @Test
    fun plainGrams_customNameMatchingExistingUnit_overwritesIt() {
        val grams = ServingUnitOption.grams
        val cup = ServingUnitOption(unit = "cup", gramsPerUnit = 250.0)
        val result = edit("g", grams, listOf(cup), "cup", 300.0)!!
        assertEquals(listOf("cup"), result.options.map { it.id })
        assertEquals(300.0, result.options.first().gramsPerUnit, 0.001)
    }

    @Test
    fun plainGrams_withoutRename_rejected() {
        val grams = ServingUnitOption.grams
        assertNull(edit("g", grams, emptyList(), "g", 300.0))
        assertNull(edit("g", grams, emptyList(), "  grams ", 300.0))
        assertNull(edit("g", grams, emptyList(), "GRAM", 300.0))
    }

    @Test
    fun emptyName_keepsOldUnitName() {
        val slice = ServingUnitOption(unit = "slice", gramsPerUnit = 120.0)
        val result = edit("slice", slice, listOf(slice), "   ", 180.0)!!
        assertEquals("slice", result.updated.unit)
        assertEquals("slice", result.updated.id)
    }

    @Test
    fun invalidGrams_rejected() {
        val slice = ServingUnitOption(unit = "slice", gramsPerUnit = 120.0)
        assertNull(edit("slice", slice, listOf(slice), "big slice", 0.0))
        assertNull(edit("slice", slice, listOf(slice), "big slice", -10.0))
    }
}
