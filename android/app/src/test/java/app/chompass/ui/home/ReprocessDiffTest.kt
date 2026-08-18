package app.chompass.ui.home

import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.MealType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ReprocessDiffTest {
    @Test
    fun buildReprocessDiff_highlightsChangedFieldsOnly() {
        val before = FoodEntry(
            name = "Pizza",
            calories = 800,
            protein = 30.0,
            carbs = 90.0,
            fat = 35.0,
            timestamp = Instant.parse("2024-01-01T12:00:00Z"),
            source = FoodSource.SNAP_FOOD,
            mealType = MealType.LUNCH,
            servingSizeGrams = 360.0,
        )
        val after = before.copy(
            name = "Pepperoni pizza",
            calories = 920,
            protein = 34.0,
            servingSizeGrams = 400.0,
        )
        val rows = buildReprocessDiff(before, after)
        assertEquals(4, rows.size)
        assertTrue(rows.any { it.label == "Name" && it.after == "Pepperoni pizza" })
        assertTrue(rows.any { it.label == "Calories" })
        assertTrue(rows.any { it.label == "Protein" })
        assertTrue(rows.any { it.label == "Serving" && it.after == "400 g" })
    }

    @Test
    fun buildReprocessDiff_emptyWhenUnchanged() {
        val entry = FoodEntry(
            name = "Apple",
            calories = 95,
            protein = 0.5,
            carbs = 25.0,
            fat = 0.3,
            timestamp = Instant.parse("2024-01-01T12:00:00Z"),
            source = FoodSource.TEXT_INPUT,
            mealType = MealType.SNACK,
            servingSizeGrams = 180.0,
        )
        assertTrue(buildReprocessDiff(entry, entry).isEmpty())
    }

    // Codeberg #20 phase 2: with the master AI switch off, a note edit must
    // never flip the edit sheet's primary button to "Correct with AI" — the
    // stored note stays editable and Save is the only primary action.
    @Test
    fun shouldOfferReprocess_falseWhenAiOff_evenWithEditedNote() {
        assertFalse(shouldOfferReprocess(aiFeaturesEnabled = false, noteText = "large bowl", savedNote = null))
        assertFalse(shouldOfferReprocess(aiFeaturesEnabled = false, noteText = "large bowl", savedNote = "small bowl"))
    }

    @Test
    fun shouldOfferReprocess_trueWhenAiOn_andNoteDiffers() {
        assertTrue(shouldOfferReprocess(aiFeaturesEnabled = true, noteText = "large bowl", savedNote = null))
        assertTrue(shouldOfferReprocess(aiFeaturesEnabled = true, noteText = " large bowl ", savedNote = "small bowl"))
    }

    @Test
    fun shouldOfferReprocess_falseWhenAiOn_butNoteUnchanged() {
        assertFalse(shouldOfferReprocess(aiFeaturesEnabled = true, noteText = "small bowl", savedNote = "small bowl"))
        assertFalse(shouldOfferReprocess(aiFeaturesEnabled = true, noteText = "  ", savedNote = null))
    }
}
