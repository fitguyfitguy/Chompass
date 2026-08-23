package app.chompass.models

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Persisted "input-only" draft for camera + note flows that failed before
 * analysis completed (e.g. provider/network errors). Multi-photo since
 * Codeberg #53: every staged photo + note survives a failed AI call and the
 * failure dialog's Retry covers all photo cases, not just single-image ones.
 */
@Serializable
data class PendingFoodInputDraft(
    /**
     * Legacy single-image drafts (pre-#53) decode here; new drafts write
     * [imageFilenames]. Empty on new drafts — see [resolvedImageFilenames].
     */
    val imageFilename: String = "",
    val imageFilenames: List<String> = emptyList(),
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
    val createdAt: Instant = Instant.now(),
    /**
     * Auto-saved analysis-queue entry for this input (Codeberg #53). Retries
     * update the same queue entry instead of duplicating it; a successful
     * retry marks it DONE. Null when no failure auto-save happened yet.
     */
    @Serializable(with = UuidSerializer::class)
    val queueEntryId: UUID? = null,
) {
    /** New multi-photo field, falling back to the legacy single filename. */
    val resolvedImageFilenames: List<String>
        get() = imageFilenames.ifEmpty {
            listOfNotNull(imageFilename.takeIf { it.isNotBlank() })
        }
}
