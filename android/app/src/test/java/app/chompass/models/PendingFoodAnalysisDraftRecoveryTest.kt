package app.chompass.models

import android.app.Application
import app.chompass.data.PreferencesStore
import app.chompass.services.ai.FoodAnalysis
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

/**
 * Recovered-review drafts (dismissed review sheet kept for the Home chip):
 * legacy drafts must keep decoding without the flag (auto-restore as before),
 * marked drafts must round-trip it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PendingFoodAnalysisDraftRecoveryTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun analysis(name: String = "Chicken rice") = FoodAnalysis(
        name = name,
        calories = 520,
        protein = 38.0,
        carbs = 55.0,
        fat = 12.0,
        servingSizeGrams = null,
    )

    fun legacyDraftWithoutFlagDecodesAsNotAwaitingReview() {
        val raw = """{"analysis":{"name":"Chicken rice","calories":520,"protein":38.0,"carbs":55.0,"fat":12.0,"servingSizeGrams":null},"imageFilename":"a.jpg","source":"snapFood","targetDate":"2026-09-04","createdAt":${Instant.parse("2026-09-04T10:00:00Z").toEpochMilli()}}"""
        val draft = json.decodeFromString(PendingFoodAnalysisDraft.serializer(), raw)
        assertEquals("Chicken rice", draft.analysis.name)
        assertFalse("legacy drafts must auto-restore, not chip", draft.awaitingReview)
    }

    @Test
    fun markedDraftRoundTripsAwaitingReview() {
        val draft = PendingFoodAnalysisDraft(
            analysis = analysis(),
            imageFilename = "a.jpg",
            source = FoodSource.SNAP_FOOD,
            targetDate = LocalDate.of(2026, 9, 4),
            createdAt = Instant.parse("2026-09-04T10:00:00Z"),
            awaitingReview = true,
        )
        val encoded = json.encodeToString(PendingFoodAnalysisDraft.serializer(), draft)
        val decoded = json.decodeFromString(PendingFoodAnalysisDraft.serializer(), encoded)
        assertTrue(decoded.awaitingReview)
        assertEquals("Chicken rice", decoded.analysis.name)
        assertEquals("a.jpg", decoded.imageFilename)
        assertEquals(FoodSource.SNAP_FOOD, decoded.source)
    }

    @Test
    fun freshDraftDefaultsToNotAwaitingReview() {
        val draft = PendingFoodAnalysisDraft(analysis = analysis())
        assertFalse(draft.awaitingReview)
    }

    // Startup image prune: the reference set must cover every photo of a
    // pending multi-photo input draft — photos 2..N used to be silently
    // pruned, and the restore then failed with "missing input".

    @Test
    fun referenceSet_includesEveryPhotoOfMultiPhotoInputDraft() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        prefs.setPendingFoodInputDraft(
            PendingFoodInputDraft(imageFilenames = listOf("a.jpg", "b.jpg", "c.jpg")),
        )

        val referenced = prefs.foodImageReferenceFilenames()

        assertEquals(setOf("a.jpg", "b.jpg", "c.jpg"), referenced)
    }

    @Test
    fun referenceSet_legacySingleNameDraft_stillCovered() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        prefs.setPendingFoodInputDraft(PendingFoodInputDraft(imageFilename = "legacy.jpg"))

        val referenced = prefs.foodImageReferenceFilenames()

        assertEquals(setOf("legacy.jpg"), referenced)
    }
}
