package app.chompass.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Serving-scaling rules (Codeberg #10 follow-up): an entry without a recorded
 * serving has macros that are absolute portion totals, so weight edits must
 * never scale them; correcting the weight records it without touching macros,
 * and leaving it alone keeps the entry serving-less.
 */
class ServingScaleTest {
    // --- servingScale ---------------------------------------------------------

    @Test
    fun scale_isOne_withoutRecordedServing_evenWhenWeightEdited() {
        // Imported entry: no recorded serving, user corrects the 100 g
        // placeholder to 150 g. Macros must stay as logged.
        assertEquals(1.0, ServingUnitOption.servingScale(null, servingGrams = 150.0, baseServingGrams = 100.0), 0.0)
    }

    @Test
    fun scale_isOne_withoutRecordedServing_atPlaceholderWeight() {
        assertEquals(1.0, ServingUnitOption.servingScale(null, servingGrams = 100.0, baseServingGrams = 100.0), 0.0)
    }

    @Test
    fun scale_isOne_whenBaseServingIsZero() {
        assertEquals(1.0, ServingUnitOption.servingScale(120.0, servingGrams = 120.0, baseServingGrams = 0.0), 0.0)
    }

    @Test
    fun scale_scalesFromRecordedServing() {
        assertEquals(1.0, ServingUnitOption.servingScale(120.0, servingGrams = 120.0, baseServingGrams = 120.0), 0.0)
        assertEquals(1.5, ServingUnitOption.servingScale(120.0, servingGrams = 180.0, baseServingGrams = 120.0), 0.0)
        assertEquals(0.5, ServingUnitOption.servingScale(200.0, servingGrams = 100.0, baseServingGrams = 200.0), 0.0)
    }

    // --- persistedServingGrams ------------------------------------------------

    @Test
    fun persist_keepsNull_whenNoRecordedServing_andWeightUntouched() {
        // Saving an imported entry without touching the weight must not bake in
        // the 100 g placeholder: the entry stays serving-less, so a later edit
        // cannot corrupt macros either.
        assertNull(ServingUnitOption.persistedServingGrams(null, servingTouched = false, servingGrams = 100.0))
    }

    @Test
    fun persist_recordsCorrectedWeight_whenNoRecordedServing_andWeightTouched() {
        assertEquals(
            150.0,
            ServingUnitOption.persistedServingGrams(null, servingTouched = true, servingGrams = 150.0)!!,
            0.0,
        )
    }

    @Test
    fun persist_keepsEditedWeight_whenRecordedServingExists() {
        assertEquals(
            180.0,
            ServingUnitOption.persistedServingGrams(120.0, servingTouched = true, servingGrams = 180.0)!!,
            0.0,
        )
        // Recorded serving persists even when untouched (status quo).
        assertEquals(
            120.0,
            ServingUnitOption.persistedServingGrams(120.0, servingTouched = false, servingGrams = 120.0)!!,
            0.0,
        )
    }
}
