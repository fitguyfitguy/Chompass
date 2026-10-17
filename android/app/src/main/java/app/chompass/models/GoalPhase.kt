package app.chompass.models

import kotlinx.serialization.Serializable
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * What a goal phase is trying to do. Drives the chip tone in the phases
 * screen (CUT error, MAINTAIN neutral, BULK success) and the adherence read.
 */
@Serializable
enum class PhaseIntent { CUT, MAINTAIN, BULK }

/**
 * A dated goal phase (Progress v2): explicit targets applied to every day in
 * [startDate, endDate] (ISO, inclusive) through a [MacroDayProfile] +
 * `MacroPlan.dayAssignments` — no new journal semantics. Rides the synced
 * [UserProfile] payload: the default-empty list decodes old payloads and the
 * sync-1.3 profile envelope stays opaque.
 */
@Serializable
data class GoalPhase(
    @Serializable(with = UuidSerializer::class)
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val intent: PhaseIntent = PhaseIntent.MAINTAIN,
    /** ISO inclusive dates ("2026-10-01"). */
    val startDate: String,
    val endDate: String,
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    /** [MacroDayProfile] id this phase applies through; linked on first apply. */
    val profileId: String? = null,
    /** Set once on first save; keeps apply-order stable across edits. */
    val createdAtMillis: Long = 0L,
) {
    /** Inclusive day count; 0 when either date fails to parse. */
    val spanDays: Long
        get() {
            val start = GoalJournal.parseDateOrNull(startDate) ?: return 0L
            val end = GoalJournal.parseDateOrNull(endDate) ?: return 0L
            return ChronoUnit.DAYS.between(start, end) + 1
        }

    /** True when [date] (ISO string) falls inside the phase window. ISO dates compare lexicographically. */
    operator fun contains(date: String): Boolean = date in startDate..endDate
}
