package app.chompass.models

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Legacy single-image → multi-photo draft migration (Codeberg #53). */
class PendingFoodInputDraftMigrationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun legacySingleImageDraftDecodesAndResolves() {
        val raw = """{"imageFilename":"abc.jpg","note":"2 eggs","source":"snapFood"}"""
        val draft = json.decodeFromString(PendingFoodInputDraft.serializer(), raw)
        assertEquals(listOf("abc.jpg"), draft.resolvedImageFilenames)
        assertEquals("2 eggs", draft.note)
        assertNull(draft.queueEntryId)
    }

    @Test
    fun multiPhotoDraftRoundTrips() {
        val draft = PendingFoodInputDraft(
            imageFilenames = listOf("a.jpg", "b.jpg"),
            note = "pasta",
            source = FoodSource.SNAP_FOOD,
        )
        val encoded = json.encodeToString(PendingFoodInputDraft.serializer(), draft)
        val decoded = json.decodeFromString(PendingFoodInputDraft.serializer(), encoded)
        assertEquals(listOf("a.jpg", "b.jpg"), decoded.resolvedImageFilenames)
        assertEquals("pasta", decoded.note)
    }

    @Test
    fun emptyImageFilenamesFallsBackToLegacyField() {
        val draft = PendingFoodInputDraft(imageFilenames = emptyList())
        assertEquals(emptyList<String>(), draft.resolvedImageFilenames)
    }
}
