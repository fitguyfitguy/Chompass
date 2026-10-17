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

    /** The existing plan (enabled or about-to-revive) has no free profile slot. */
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
        if (plan != null) {
            // Reviving a disabled plan appends a fresh Base profile on top of
            // the phase profile; enabled plans upsert the phase in place.
            val adds = (if (plan.enabled) 0 else 1) +
                (if (phase.profileId != null && plan.profileById(phase.profileId!!) != null) 0 else 1)
            if (adds > 0 && plan.profiles.size + adds > MacroPlanEdit.MAX_PROFILES) {
                return PhaseEditError.NoProfileSlot
            }
        }
        return null
    }

    /**
     * Applies a phase to [profile]:
     *  - phase calories clamp to [CalorieSafety] floor/ceiling first — the
     *    stored [GoalPhase.calories] always equals the applied profile's
     *    calories, so list rows and the plan profile cannot disagree;
     *  - plan missing → fresh resolvable MANUAL plan: "Base" (current base
     *    targets, fresh stable UUID) + the phase profile, enabled, Base
     *    default — days outside phases keep today's goals;
     *  - plan present but disabled (keto pause, manual off) → revived in
     *    place: profiles, mode, weekday map and cycle pattern survive, a fresh
     *    Base becomes the default, `enabled` flips back on;
     *  - enabled plan → upsert the phase profile in place (reuses
     *    [GoalPhase.profileId]);
     *  - then stamps dayAssignments for every date in [startDate, endDate]
     *    clipped to today ± [MacroPlan.ASSIGNMENT_KEEP_DAYS]. Overlap
     *    ownership follows [GoalPhase.createdAtMillis]: a newer phase keeps
     *    days an older phase re-saves into; the phase's own stale stamps
     *    outside its (clipped) range are dropped. Removal is profile-scoped
     *    so other phases and manual days survive.
     */
    fun applyPhase(profile: UserProfile, phase: GoalPhase, today: LocalDate): UserProfile {
        val start = GoalJournal.parseDateOrNull(phase.startDate) ?: return profile
        val end = GoalJournal.parseDateOrNull(phase.endDate) ?: return profile
        val clamped = phase.copy(calories = CalorieSafety.clampAuto(phase.calories, profile.bmr, profile.tdee))
        val clipFrom = today.minusDays(MacroPlan.ASSIGNMENT_KEEP_DAYS)
        val clipTo = today.plusDays(MacroPlan.ASSIGNMENT_KEEP_DAYS)
        val effectiveStart = maxOf(start, clipFrom)
        val effectiveEnd = minOf(end, clipTo)

        val plan = profile.macroPlan
        if (plan == null) {
            // Fresh two-profile plan; replaces any inert (disabled) data.
            val base = MacroDayProfile(
                id = UUID.randomUUID().toString(),
                name = BASE_PROFILE_NAME,
                calories = profile.effectiveCalories,
                proteinG = profile.effectiveProtein,
                carbsG = profile.effectiveCarbs,
                fatG = profile.effectiveFat,
            )
            val nextPhase = clamped.copy(profileId = clamped.profileId ?: UUID.randomUUID().toString())
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

        val phaseProfileId = clamped.profileId ?: UUID.randomUUID().toString()
        val linked = clamped.copy(profileId = phaseProfileId)
        // Stamp first: the overlap guard compares real createdAtMillis, so the
        // row must carry its stamp before stampedFor runs.
        val goalPhases = upsertPhase(profile.goalPhases, linked)
        val stored = goalPhases.firstOrNull { it.id == linked.id } ?: linked
        if (!plan.enabled) {
            // Revive in place: keep every byte of the paused plan (profiles,
            // mode, weekday map, cycle pattern, assignments) and re-point the
            // default at a fresh Base mirroring today's targets. Keto-paused
            // Training/Rest data survives; non-phase days resolve to Base.
            val base = MacroDayProfile(
                id = UUID.randomUUID().toString(),
                name = BASE_PROFILE_NAME,
                calories = profile.effectiveCalories,
                proteinG = profile.effectiveProtein,
                carbsG = profile.effectiveCarbs,
                fatG = profile.effectiveFat,
            )
            val revived = plan.copy(
                profiles = plan.profiles.filterNot { it.id == phaseProfileId } + base +
                    phaseProfile(linked, phaseProfileId),
                enabled = true,
                defaultProfileId = base.id,
                dayAssignments = prunedWithin(plan.dayAssignments, phaseProfileId, effectiveStart, effectiveEnd) +
                    stampedFor(stored, effectiveStart, effectiveEnd, plan.dayAssignments, goalPhases),
            )
            return profile.copy(
                macroPlan = MacroPlanEdit.normalized(revived, today),
                goalPhases = goalPhases,
            )
        }

        // Enabled plan: upsert the phase profile, reusing its id.
        val updatedPlan = plan.copy(
            profiles = plan.profiles.filterNot { it.id == phaseProfileId } + phaseProfile(linked, phaseProfileId),
            dayAssignments = prunedWithin(plan.dayAssignments, phaseProfileId, effectiveStart, effectiveEnd) +
                stampedFor(stored, effectiveStart, effectiveEnd, plan.dayAssignments, goalPhases),
        )
        return profile.copy(
            macroPlan = MacroPlanEdit.normalized(updatedPlan, today),
            goalPhases = goalPhases,
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

    /**
     * Rolls phase stamps with the keep window (pure half of the journal
     * refresh): per phase, drops its own assignments that slid outside
     * `[max(start, clipFrom), min(end, clipTo)]` and gap-fills only missing
     * days, so far-future phases self-activate as their range enters
     * today ± [MacroPlan.ASSIGNMENT_KEEP_DAYS] and tails extend as the window
     * rolls. Fills newest-first ([GoalPhase.createdAtMillis]) so fresh fills
     * match [applyPhase] overlap ownership; existing entries pointing at
     * other profiles (manual day-type switches inside a phase window) are
     * never overwritten. Idempotent: returns [profile] unchanged when nothing
     * moved, so the refresh save loop converges.
     */
    fun restampAssignments(profile: UserProfile, today: LocalDate): UserProfile {
        val plan = profile.macroPlan ?: return profile
        if (!plan.enabled) return profile
        val phased = profile.goalPhases.filter { it.profileId != null }
        if (phased.isEmpty()) return profile
        val clipFrom = today.minusDays(MacroPlan.ASSIGNMENT_KEEP_DAYS)
        val clipTo = today.plusDays(MacroPlan.ASSIGNMENT_KEEP_DAYS)
        var assignments = plan.dayAssignments
        for (phase in phased.sortedByDescending { it.createdAtMillis }) {
            val id = phase.profileId!!
            val start = GoalJournal.parseDateOrNull(phase.startDate) ?: continue
            val end = GoalJournal.parseDateOrNull(phase.endDate) ?: continue
            val from = maxOf(start, clipFrom)
            val to = minOf(end, clipTo)
            if (from > to) continue
            assignments = prunedWithin(assignments, id, from, to)
            assignments = assignments + assignmentsFor(phase, from, to).filterKeys { it !in assignments }
        }
        if (assignments == plan.dayAssignments) return profile
        return profile.copy(
            macroPlan = MacroPlanEdit.normalized(plan.copy(dayAssignments = assignments), today),
        )
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

    /** Drops [id]'s assignments that fall outside `[from, to]`; others survive. */
    private fun prunedWithin(
        assignments: Map<String, String>,
        id: String,
        from: LocalDate,
        to: LocalDate,
    ): Map<String, String> = assignments.filterNot { (d, ref) ->
        ref == id && GoalJournal.parseDateOrNull(d)
            ?.let { it.isBefore(from) || it.isAfter(to) } == true
    }

    /**
     * [assignmentsFor] guarded by overlap ownership: a date whose existing
     * assignment belongs to a **newer** phase (created after [linked] and
     * covering the day) is skipped, so re-saving an older phase never reclaims
     * days a newer phase owns. Everything else stamps normally.
     */
    private fun stampedFor(
        linked: GoalPhase,
        from: LocalDate,
        to: LocalDate,
        existing: Map<String, String>,
        phases: List<GoalPhase>,
    ): Map<String, String> = assignmentsFor(linked, from, to).filterKeys { d ->
        val ref = existing[d] ?: return@filterKeys true
        val owner = phases.firstOrNull { it.profileId == ref && it.id != linked.id }
        val day = GoalJournal.parseDateOrNull(d)
        !(owner != null && owner.createdAtMillis > linked.createdAtMillis && day != null && day.toString() in owner)
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
