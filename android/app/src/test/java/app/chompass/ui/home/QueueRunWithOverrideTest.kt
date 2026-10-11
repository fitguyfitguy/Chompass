package app.chompass.ui.home

import app.chompass.data.AnalysisQueueStore
import app.chompass.models.AIProvider
import app.chompass.models.AiProfile
import app.chompass.models.FoodSource
import app.chompass.models.QueuedAnalysis
import app.chompass.models.QueueStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Queue "Run with…" (AI-profiles Step 4): the attempt record written before a
 * queued run (profile name + model resolved exactly as dispatch resolves it)
 * and legacy queue JSON compatibility after [QueuedAnalysis] gained the two
 * last-attempt fields.
 */
class QueueRunWithOverrideTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun profile(
        name: String = "LM Studio",
        model: String? = null,
        visionModel: String? = null,
    ) = AiProfile(
        id = UUID.randomUUID(),
        name = name,
        provider = AIProvider.GEMINI,
        model = model,
        visionModel = visionModel,
        createdAt = Instant.now(),
    )

    private fun item(hasImages: Boolean = false) = QueuedAnalysis(
        id = UUID.randomUUID(),
        createdAt = Instant.now(),
        targetDate = LocalDate.now(),
        imageFilenames = if (hasImages) listOf("a.jpg") else emptyList(),
        note = "apple pie",
        source = FoodSource.SNAP_FOOD,
    )

    @Test
    fun attemptRecordUsesPrimaryModelForTextOnlyRuns() {
        val p = profile(model = "gemini-3.8-flash", visionModel = "gemini-3.5-flash")
        val recorded = queuedEntryWithAttemptRecord(item(hasImages = false), p, hasImages = false)
        assertEquals("LM Studio", recorded.lastAttemptProfile)
        assertEquals("gemini-3.8-flash", recorded.lastAttemptModel)
    }

    @Test
    fun attemptRecordUsesVisionModelForImageRuns() {
        val p = profile(model = "gemini-3.8-flash", visionModel = "gemini-3.5-flash")
        val recorded = queuedEntryWithAttemptRecord(item(hasImages = true), p, hasImages = true)
        assertEquals("gemini-3.5-flash", recorded.lastAttemptModel)
    }

    @Test
    fun attemptRecordFallsBackToProviderDefaultWhenModelUnset() {
        val p = profile(model = null, visionModel = null)
        val recorded = queuedEntryWithAttemptRecord(item(hasImages = false), p, hasImages = false)
        assertEquals(AIProvider.GEMINI.defaultModel, recorded.lastAttemptModel)
    }

    /** Old queue files (no attempt fields) keep loading; the new fields decode to null. */
    @Test
    fun legacyQueueJsonWithoutAttemptFieldsParses() = runBlocking {
        val root = tmp.newFolder("legacy")
        val id = "11111111-2222-3333-4444-555555555555"
        File(root, AnalysisQueueStore.FILE_NAME).writeText(
            """[{"id":"$id","createdAt":${Instant.now().toEpochMilli()},""" +
                """"targetDate":"2026-01-01","imageFilenames":[],"note":"old prompt",""" +
                """"source":"snapFood","status":"PENDING"}]""",
        )
        val s = AnalysisQueueStore(root)
        s.ensureLoaded()
        val entry = s.item(UUID.fromString(id))!!
        assertEquals("old prompt", entry.note)
        assertNull(entry.lastAttemptProfile)
        assertNull(entry.lastAttemptModel)
    }

    /**
     * The failed-run path keeps the entry runnable and both attempt fields —
     * verified through a fresh store over the same folder, i.e. on disk.
     */
    @Test
    fun attemptFieldsSurviveMarkFailedAndDiskRoundTrip() = runBlocking {
        val root = tmp.newFolder("failed")
        val s = AnalysisQueueStore(root)
        val a = item()
        s.upsert(a.copy(lastAttemptProfile = "LM Studio", lastAttemptModel = "qwen2-vl-7b"))
        s.markFailed(a.id, "connection refused")
        val failed = s.item(a.id)!!
        assertEquals(QueueStatus.PENDING, failed.status)
        assertEquals("connection refused", failed.error)
        assertEquals("LM Studio", failed.lastAttemptProfile)
        assertEquals("qwen2-vl-7b", failed.lastAttemptModel)
        val reloaded = AnalysisQueueStore(root)
        reloaded.ensureLoaded()
        val fromDisk = reloaded.item(a.id)!!
        assertEquals("LM Studio", fromDisk.lastAttemptProfile)
        assertEquals("qwen2-vl-7b", fromDisk.lastAttemptModel)
    }
}
