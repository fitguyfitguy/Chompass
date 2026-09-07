package app.chompass.ui.home

import app.chompass.models.FoodConstituent
import app.chompass.models.microsCompositionSignature
import app.chompass.models.microsStaleFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MicrosCompositionSignatureTest {
    private fun row(name: String, grams: Double) = FoodConstituent(
        name = name,
        calories = 100,
        protein = 1.0,
        carbs = 1.0,
        fat = 1.0,
        servingSizeGrams = grams,
    )

    @Test
    fun emptyConstituents_signatureIsNull() {
        assertNull(microsCompositionSignature(emptyList()))
    }

    @Test
    fun uniformScale_doesNotChangeSignature() {
        val base = listOf(row("Rice", 100.0), row("Chicken", 150.0))
        val scaled = listOf(row("Rice", 200.0), row("Chicken", 300.0))
        assertEquals(microsCompositionSignature(base), microsCompositionSignature(scaled))
    }

    @Test
    fun reorder_doesNotChangeSignature() {
        val a = listOf(row("Rice", 100.0), row("Chicken", 150.0))
        val b = listOf(row("Chicken", 150.0), row("Rice", 100.0))
        assertEquals(microsCompositionSignature(a), microsCompositionSignature(b))
    }

    @Test
    fun quantityChange_changesSignature() {
        val base = listOf(row("Rice", 100.0), row("Chicken", 150.0))
        val halved = listOf(row("Rice", 50.0), row("Chicken", 150.0))
        assertNotEquals(microsCompositionSignature(base), microsCompositionSignature(halved))
    }

    @Test
    fun add_changesSignature() {
        val base = listOf(row("Rice", 100.0))
        val added = listOf(row("Rice", 100.0), row("Sauce", 20.0))
        assertNotEquals(microsCompositionSignature(base), microsCompositionSignature(added))
    }

    @Test
    fun remove_changesSignature() {
        val base = listOf(row("Rice", 100.0), row("Chicken", 150.0))
        val removed = listOf(row("Rice", 100.0))
        assertNotEquals(microsCompositionSignature(base), microsCompositionSignature(removed))
    }

    @Test
    fun rename_changesSignature() {
        val base = listOf(row("Rice", 100.0))
        val renamed = listOf(row("Brown Rice", 100.0))
        assertNotEquals(microsCompositionSignature(base), microsCompositionSignature(renamed))
    }

    @Test
    fun microsStaleFor_truthTable() {
        val riceChicken = listOf(row("Rice", 100.0), row("Chicken", 150.0))
        val stamped = microsCompositionSignature(riceChicken)
        val halved = listOf(row("Rice", 50.0), row("Chicken", 150.0))
        val scaled = listOf(row("Rice", 200.0), row("Chicken", 300.0))

        assertFalse(microsStaleFor(null, riceChicken))
        assertFalse(microsStaleFor(null, halved))
        assertFalse(microsStaleFor(stamped, riceChicken))
        assertFalse(microsStaleFor(stamped, scaled))
        assertTrue(microsStaleFor(stamped, halved))
        assertTrue(microsStaleFor(stamped, emptyList()))
    }
}
