package app.chompass.models

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

/**
 * Persisted "input-only" draft for camera + note flows that failed before
 * analysis completed (e.g. provider/network errors).
 */
@Serializable
data class PendingFoodInputDraft(
    val imageFilename: String,
    val note: String = "",
    /** Optional user-confirmed total edible grams; null when absent or legacy drafts. */
    val confirmedPortionGrams: Double? = null,
    val source: FoodSource = FoodSource.SNAP_FOOD,
    /**
     * Diary day the input sheet was opened for; restored with the draft so a
     * later Log lands on the intended day after process death (see
     * [PendingFoodAnalysisDraft.targetDate]). Defaults to now for legacy drafts.
     */
    @Serializable(with = LocalDateSerializer::class)
    val targetDate: LocalDate = LocalDate.now(),
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant = Instant.now()
)
