package app.chompass.services.ai

import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.GapGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun missingKeys_constituentAsk_onlyWhenSchemaCarriesRows() {
        // Macros/micros all answered; the all-zero ingredient rows are the
        // only gap (the cocktail shape). The ask must name the rows so the
        // fill is not a no-op, and only when the reply schema carries
        // constituents.
        val entry = FoodEntry(
            name = "Whiskey Sour",
            calories = 160,
            protein = 0.1,
            carbs = 14.5,
            fat = 0.0,
            source = FoodSource.MANUAL,
            gapAnsweredFields = setOf("fat"),
            constituents = listOf(
                app.chompass.models.FoodConstituent("Whiskey", 0, 0.0, 0.0, 0.0, servingSizeGrams = 40.0),
            ),
        )

        for (kind in listOf(EntryConstituentPromptKind.MACROS, EntryConstituentPromptKind.MICROS)) {
            val keys = fillEntryMissingKeys(entry, kind)
            assertTrue(
                "$kind must ask for the ingredient rows: $keys",
                keys.contains("macros for each logged ingredient row"),
            )
            assertTrue(fillAsksConstituentMacros(entry, kind))
        }
        for (kind in listOf(EntryConstituentPromptKind.NONE, EntryConstituentPromptKind.LEAN)) {
            val keys = fillEntryMissingKeys(entry, kind)
            assertFalse(
                "$kind must not ask for the rows: $keys",
                keys.contains("macros for each logged ingredient row"),
            )
            assertFalse(fillAsksConstituentMacros(entry, kind))
        }
    }

    @Test
    fun missingKeys_focusNarrowsToFocusedGroups() {
        // gappedEntry: calories/protein/carbs missing, fat answered (12.5),
        // every micro field null.
        assertEquals(
            "protein",
            fillEntryMissingKeys(gappedEntry(), EntryConstituentPromptKind.NONE, focus = setOf(GapGroup.PROTEIN)),
        )
        // Micro keys flow only while MICROS is focused.
        val microsOnly = fillEntryMissingKeys(gappedEntry(), EntryConstituentPromptKind.NONE, focus = setOf(GapGroup.MICROS))
        assertTrue("micro ask missing: $microsOnly", microsOnly.contains("fiber"))
        assertFalse("macro ask leaked into MICROS focus: $microsOnly", microsOnly.contains("calories"))
        // Unfocused missing macros drop out of the stable-order list.
        val caloriesAndCarbs =
            fillEntryMissingKeys(gappedEntry(), EntryConstituentPromptKind.NONE, focus = setOf(GapGroup.CALORIES, GapGroup.CARBS))
        assertEquals("calories, carbs", caloriesAndCarbs)
    }

    @Test
    fun constituentAsk_onlyWhenAllMacroGroupsFocused() {
        // The cocktail shape: macros answered, the all-zero rows are the only
        // macro-level gap. A focus missing even one macro group must not ask
        // for the rows (each row carries all four macros).
        val entry = FoodEntry(
            name = "Whiskey Sour",
            calories = 160,
            protein = 0.1,
            carbs = 14.5,
            fat = 0.0,
            source = FoodSource.MANUAL,
            gapAnsweredFields = setOf("fat"),
            constituents = listOf(
                app.chompass.models.FoodConstituent("Whiskey", 0, 0.0, 0.0, 0.0, servingSizeGrams = 40.0),
            ),
        )

        assertFalse(
            fillAsksConstituentMacros(entry, EntryConstituentPromptKind.MACROS, focus = setOf(GapGroup.PROTEIN)),
        )
        val narrowed = fillEntryMissingKeys(entry, EntryConstituentPromptKind.MACROS, focus = setOf(GapGroup.PROTEIN))
        assertFalse("row ask leaked into narrow focus: $narrowed", narrowed.contains("macros for each logged ingredient row"))
        assertTrue(
            fillAsksConstituentMacros(entry, EntryConstituentPromptKind.MACROS, focus = GapGroup.MACRO_GROUPS),
        )
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
