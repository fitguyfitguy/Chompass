package app.chompass.sync

import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.util.UUID

class ReattachLocalFieldsTest {
    private fun entry(id: UUID, imageFilename: String?, emoji: String?): FoodEntry = FoodEntry(
        id = id,
        name = "Oats",
        calories = 200,
        protein = 7.0,
        carbs = 35.0,
        fat = 3.0,
        timestamp = Instant.parse("2026-08-17T10:00:00Z"),
        source = FoodSource.MANUAL,
        imageFilename = imageFilename,
        emoji = emoji,
    )

    @Test
    fun reattachesPhotoAndEmojiWhenWireRowHasNeither() {
        val id = UUID.randomUUID()
        val wire = entry(id, imageFilename = null, emoji = null)
        val local = entry(id, imageFilename = "$id.jpg", emoji = "🥣")
        val restored = reattachLocalFields(wire, mapOf(id to local))
        assertEquals("$id.jpg", restored.imageFilename)
        assertEquals("🥣", restored.emoji)
    }

    @Test
    fun reattachesOnlyMissingField() {
        val id = UUID.randomUUID()
        // Wire from a new-version export: emoji carried, photo never.
        val wire = entry(id, imageFilename = null, emoji = "🍎")
        val local = entry(id, imageFilename = "$id.jpg", emoji = "🥣")
        val restored = reattachLocalFields(wire, mapOf(id to local))
        assertEquals("$id.jpg", restored.imageFilename)
        assertEquals("🍎", restored.emoji) // wire emoji wins, local photo kept
    }

    @Test
    fun keepsWireValuesWhenPresent() {
        val id = UUID.randomUUID()
        val wire = entry(id, imageFilename = "wire.jpg", emoji = "wire-emoji")
        val local = entry(id, imageFilename = "local.jpg", emoji = "local-emoji")
        val restored = reattachLocalFields(wire, mapOf(id to local))
        assertEquals("wire.jpg", restored.imageFilename)
        assertEquals("wire-emoji", restored.emoji)
    }

    @Test
    fun leavesFieldsNullWhenNoLocalRowExists() {
        val id = UUID.randomUUID()
        val wire = entry(id, imageFilename = null, emoji = null)
        assertNull(reattachLocalFields(wire, emptyMap()).imageFilename)
        assertNull(reattachLocalFields(wire, emptyMap()).emoji)
    }

    @Test
    fun leavesOtherFieldsUntouched() {
        val id = UUID.randomUUID()
        val wire = entry(id, imageFilename = null, emoji = null)
        val restored = reattachLocalFields(wire, mapOf(id to entry(id, "$id.jpg", "🥣")))
        assertEquals("Oats", restored.name)
        assertEquals(200, restored.calories)
        assertEquals(7.0, restored.protein, 0.0)
        assertEquals(id, restored.id)
    }
}
