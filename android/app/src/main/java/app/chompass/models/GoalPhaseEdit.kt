package app.chompass.models

import java.time.LocalDate
import java.util.UUID

/**
 * Typed validation failures for phase writes, consumed by the editor sheet.
 */
sealed class PhaseEditError {
    data object BlankName : PhaseEditError()
    data object BadRange : PhaseEditError()
    data object TooLong : PhaseEditError()

    /** The existing enabled plan has no free profile slot for the phase. */
    data object NoProfileSlot : PhaseEditError()
}

/**
 * Pure goal-phase write path (Progress v2). Phases apply through the existing
 * day-type mechanics: a phase maps onto one [MacroDayProfile] and stamps
 * `MacroPlan.dayAssignments` across its (clipped) date range. No new journal
 * code — profile saves re-trigger GoalJournalService.observe, future days
 * resolve at day-time from the assignments, unjournaled past days fall back to
 * the resolver's assignment lookup.
 *
 * All functions are pure; persistence stays in SettingsViewModel.
 */
object GoalPhaseEdit {
    /** Matches [MacroPlan.ASSIGNMENT_KEEP_DAYS]; longer spans clip (helper text, not an error). */
    const val MAX_SPAN_DAYS = MacroPlan.ASSIGNMENT_KEEP_DAYS

    /** Name of the auto-created day-type profile that mirrors the base targets. */
    const val BASE_PROFILE_NAME = "Base"

    /** Validates [phase] against the plan capacity in [profile]. Null = ok. */
    fun validate(profile: UserProfile, phase: GoalPhase): PhaseEditError? {
        if (phase.name.isBlank()) return PhaseEditError.BlankName
        val start = GoalJournal.parseDateOrNull(phase.startDate) ?: return PhaseEditError.BadRange
        val end = GoalJournal.parseDateOrNull(phase.endDate) ?: return PhaseEditError.BadRange
        if (end.isBefore(start)) return PhaseEditError.BadRange
        if (ChronoUnitDays(start, end) + 1 > MAX_SPAN_DAYS) return PhaseEditError.TooLong
        val plan = profile.macroPlan
        if (plan != null && plan.enabled &&
            phase.profileId !in plan.profiles.map { it.id } &&
            plan.profiles.size >= MacroPlanEdit.MAX_PROFILES
        ) {
            return PhaseEditError.NoProfileSlot
        }
        return null
    }

    /**
     * Applies a phase to [profile]:
     *  - plan missing/disabled → fresh resolvable MANUAL plan: "Base" (current
     *    base targets, fresh stable UUID) + the phase profile, enabled, Base
     *    default — days outside phases keep today's goals;
     *  - enabled plan → upsert the phase profile in place (reuses
     *    [GoalPhase.profileId]);
     *  - then stamps dayAssignments for every date in [startDate, endDate]
     *    clipped to today ± [MacroPlan.ASSIGNMENT_KEEP_DAYS]. Overlaps
     *    overwrite (later write wins by write order); removal is
     *    profile-scoped so other phases and manual days survive.
     */
    fun applyPhase(profile: UserProfile, phase: GoalPhase, today: LocalDate): UserProfile {
        val start = GoalJournal.parseDateOrNull(phase.startDate) ?: return profile
        val end = GoalJournal.parseDateOrNull(phase.endDate) ?: return profile
        val clipFrom = today.minusDays(MacroPlan.ASSIGNMENT_KEEP_DAYS)
        val clipTo = today.plusDays(MacroPlan.ASSIGNMENT_KEEP_DAYS)
        val effectiveStart = maxOf(start, clipFrom)
        val effectiveEnd = minOf(end, clipTo)

        val plan = profile.macroPlan
        if (plan == null || !plan.enabled) {
            // Fresh two-profile plan; replaces any inert (disabled) data.
            val base = MacroDayProfile(
                id = UUID.randomUUID().toString(),
                name = BASE_PROFILE_NAME,
                calories = profile.effectiveCalories,
                proteinG = profile.effectiveProtein,
                carbsG = profile.effectiveCarbs,
                fatG = profile.effectiveFat,
            )
            val nextPhase = phase.copy(profileId = phase.profileId ?: UUID.randomUUID().toString())
            val freshPlan = MacroPlan(
                enabled = true,
                profiles = listOf(base, phaseProfile(nextPhase, nextPhase.profileId!!)),
                mode = MacroPlanMode.MANUAL,
                defaultProfileId = base.id,
                dayAssignments = assignmentsFor(nextPhase, effectiveStart, effectiveEnd),
            )
            return profile.copy(
                macroPlan = MacroPlanEdit.normalized(freshPlan, today),
                goalPhases = upsertPhase(profile.goalPhases, nextPhase),
            )
        }

        // Enabled plan: upsert the phase profile, reusing its id.
        val phaseProfileId = phase.profileId ?: UUID.randomUUID().toString()
        val linked = phase.copy(profileId = phaseProfileId)
        val updatedPlan = plan.copy(
            profiles = plan.profiles.filterNot { it.id == phaseProfileId } + phaseProfile(linked, phaseProfileId),
            dayAssignments = plan.dayAssignments + assignmentsFor(linked, effectiveStart, effectiveEnd),
        )
        return profile.copy(
            macroPlan = MacroPlanEdit.normalized(updatedPlan, today),
            goalPhases = upsertPhase(profile.goalPhases, linked),
        )
    }

    /**
     * Removes a phase: drops it from the list, clears the dayAssignments
     * **inside its range that point at its profile** (manual switches to other
     * profiles survive), and deletes the phase profile only when nothing else
     * references it. With the two-profile auto plan this disables the plan —
     * Base equals the base targets, so days outside phases resolve unchanged.
     */
    fun removePhase(profile: UserProfile, phaseId: UUID, today: LocalDate): UserProfile {
        val phase = profile.goalPhases.firstOrNull { it.id == phaseId } ?: return profile
        val remainingPhases = profile.goalPhases.filterNot { it.id == phaseId }
        val plan = profile.macroPlan
        val profileId = phase.profileId
        if (plan == null || profileId == null || plan.profileById(profileId) == null) {
            return profile.copy(goalPhases = remainingPhases)
        }
        val cleared = plan.copy(
            // Remove only the phase-window assignments pointing at the phase
            // profile; manual switches elsewhere (or to other profiles) survive.
            dayAssignments = plan.dayAssignments.filter { (date, ref) ->
                !(date in phase && ref == profileId)
            },
        )
        val nextPlan = if (MacroPlanEdit.isProfileReferenced(cleared, profileId)) {
            cleared
        } else {
            MacroPlanEdit.deleteProfile(cleared, profileId, replacementId = null, today)
        }
        return profile.copy(macroPlan = nextPlan, goalPhases = remainingPhases)
    }

    // -- helpers ---------------------------------------------------------------

    private fun phaseProfile(phase: GoalPhase, profileId: String) = MacroDayProfile(
        id = profileId,
        name = phase.name,
        calories = phase.calories,
        proteinG = phase.proteinG,
        carbsG = phase.carbsG,
        fatG = phase.fatG,
    )

    private fun assignmentsFor(phase: GoalPhase, start: LocalDate, end: LocalDate): Map<String, String> {
        val id = phase.profileId ?: return emptyMap()
        val out = HashMap<String, String>()
        var day = start
        while (!day.isAfter(end)) {
            out[day.toString()] = id
            day = day.plusDays(1)
        }
        return out
    }

    /** Replace by id; keep a non-zero createdAtMillis stable across edits. */
    private fun upsertPhase(phases: List<GoalPhase>, next: GoalPhase): List<GoalPhase> {
        val existing = phases.firstOrNull { it.id == next.id }
        val stamped = if (next.createdAtMillis != 0L) next else next.copy(
            createdAtMillis = existing?.createdAtMillis?.takeIf { it != 0L } ?: System.currentTimeMillis(),
        )
        return phases.filterNot { it.id == next.id } + stamped
    }

    private fun ChronoUnitDays(start: LocalDate, end: LocalDate): Long =
        java.time.temporal.ChronoUnit.DAYS.between(start, end)
}
