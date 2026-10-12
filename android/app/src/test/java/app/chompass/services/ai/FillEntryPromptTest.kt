package app.chompass.services.ai

import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure fill-missing helpers: micro asks are scoped to the reply schema kind
 * (MACROS/LEAN leaves them unanswerable on the wire), and a grams-less reply
 * adopts the logged entry's grams instead of the parser's 100 g default
 * (which would scale a 400 g entry's fills ×4 in fillMissingFrom).
 */
class FillEntryPromptTest {
    private fun gappedEntry() = FoodEntry(
        name = "Chicken bowl",
        calories = 0,
        protein = 0.0,
        carbs = 0.0,
        fat = 12.5,
        source = FoodSource.MANUAL,
    )

    private fun parsedGrams(grams: Double?) = FoodAnalysis(
        name = "Chicken bowl",
        calories = 0,
        protein = 0.0,
        carbs = 0.0,
        fat = 0.0,
        servingSizeGrams = grams,
    )

    @Test
    fun missingKeys_microsListedOnlyWhenSchemaCarriesThem() {
        val macrosOnly = "calories, protein, carbs"
        assertEquals(macrosOnly, fillEntryMissingKeys(gappedEntry(), EntryConstituentPromptKind.MACROS))
        assertEquals(macrosOnly, fillEntryMissingKeys(gappedEntry(), EntryConstituentPromptKind.LEAN))
        for (kind in listOf(EntryConstituentPromptKind.NONE, EntryConstituentPromptKind.MICROS)) {
            val keys = fillEntryMissingKeys(gappedEntry(), kind)
            assertTrue("micro ask missing for $kind: $keys", keys.contains("fiber"))
            assertTrue("$kind must keep the macro prefix: $keys", keys.startsWith("$macrosOnly, "))
        }
    }

    @Test
    fun adaptFillServingGrams_replyWithoutGrams_adoptsEntryGrams() {
        // servingSizeGrams 100.0 is what the parser defaults a grams-less reply to.
        val adapted = adaptFillServingGrams(parsedGrams(100.0), replyHasGrams = false, entryGrams = 400.0)
        assertEquals(400.0, adapted.servingSizeGrams!!, 1e-9)
    }

    @Test
    fun adaptFillServingGrams_replyWithoutGrams_entryNull_staysNull() {
        assertNull(
            adaptFillServingGrams(parsedGrams(100.0), replyHasGrams = false, entryGrams = null).servingSizeGrams,
        )
    }

    @Test
    fun adaptFillServingGrams_replyWithGrams_unchanged() {
        val parsed = parsedGrams(250.0)
        assertSame(parsed, adaptFillServingGrams(parsed, replyHasGrams = true, entryGrams = 400.0))
    }
}
