package app.chompass.models

import app.chompass.services.ai.FoodAnalysis
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Lifecycle of an analysis-queue entry (Codeberg #53). */
@Serializable
enum class QueueStatus {
    /** Waiting in the queue: runnable and editable, never auto-pruned. */
    PENDING,
    /** Ran successfully; kept in history with the AI result until retention prunes it. */
    DONE,
    /** Ran and failed; kept in history with the error until retention prunes it. */
    FAILED,
}

/**
 * One persisted analysis-queue / prompt-history entry (Codeberg #53):
 * time + photos + description stored so a failed AI call loses nothing and
 * any prompt can be re-run later (e.g. against a home-PC local HTTP model).
 *
 * Photos live as JPEG files in `filesDir/fudai-queue-images/` referenced by
 * [imageFilenames] — never inside the JSON. History entries are pruned after
 * [app.chompass.data.AnalysisQueueStore.HISTORY_RETENTION_DAYS]; PENDING
 * entries are the user's intended work and are only removed manually.
 */
@Serializable
data class QueuedAnalysis(
    @Serializable(with = UuidSerializer::class)
    val id: UUID,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
    /** Diary day a run result should log to (editable; mirrors draft targetDate). */
    @Serializable(with = LocalDateSerializer::class)
    val targetDate: LocalDate,
    val imageFilenames: List<String> = emptyList(),
    /** Free-form description / note shown to the model; null when absent. */
    val note: String? = null,
    /** User-confirmed total edible grams (controlled ground truth, separate from the note). */
    val confirmedPortionGrams: Double? = null,
    /** Progressive-meal single-ingredient prompt variant. */
    val singleIngredient: Boolean = false,
    val source: FoodSource = FoodSource.SNAP_FOOD,
    val status: QueueStatus = QueueStatus.PENDING,
    /** Last failure message (user language); set while the entry stays runnable. */
    val error: String? = null,
    /** AI result of the last successful run (history inspection + re-log). */
    val result: FoodAnalysis? = null,
)
