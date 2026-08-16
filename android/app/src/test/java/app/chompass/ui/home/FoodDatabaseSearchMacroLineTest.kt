package app.chompass.ui.home

import app.chompass.models.NutrientSourceKind
import app.chompass.services.grounding.DatabaseSearchResult
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Render-safety of the search result macro line (Codeberg #26). OFF hits
 * routinely carry only one or two of protein/carbs/fat (that is what
 * [DatabaseSearchResult.incompleteEnergy] tracks), and the old fixed-index
 * `macros[2]` threw IndexOutOfBoundsException on 2-element lists while the
 * LazyColumn drew the row, closing the app to the launcher with no dialog.
 */
class FoodDatabaseSearchMacroLineTest {
    private fun result(
        protein: Double? = null,
        carbs: Double? = null,
        fat: Double? = null,
    ) = DatabaseSearchResult(
        sourceKind = NutrientSourceKind.OPEN_FOOD_FACTS,
        sourceId = "9300645111125",
        name = "Bolognese",
        caloriesPerServing = 150.0,
        proteinPerServing = protein,
        carbsPerServing = carbs,
        fatPerServing = fat,
        incompleteEnergy = protein == null || carbs == null || fat == null,
    )

    @Test
    fun twoOfThreeMacros_noCrash_fillsMissingWithDash() {
        // The exact crash shape: length-2 list, index 2 accessed.
        assertEquals("P 12 · C 4 · F —", resultMacroLine(result(protein = 11.6, carbs = 4.2)))
        assertEquals("P 12 · C — · F 8", resultMacroLine(result(protein = 11.6, fat = 8.1)))
        assertEquals("P — · C 4 · F 8", resultMacroLine(result(carbs = 4.2, fat = 8.1)))
    }

    @Test
    fun oneOfThreeMacros_noCrash() {
        assertEquals("P 12 · C — · F —", resultMacroLine(result(protein = 11.6)))
        assertEquals("P — · C — · F 8", resultMacroLine(result(fat = 8.1)))
    }

    @Test
    fun allThreeMacros_rendersEveryValue() {
        assertEquals(
            "P 12 · C 4 · F 8",
            resultMacroLine(result(protein = 11.6, carbs = 4.2, fat = 8.1)),
        )
    }

    @Test
    fun noMacrosAtAll_fallsBackToLabels() {
        assertEquals("P · C · F", resultMacroLine(result()))
    }
}
