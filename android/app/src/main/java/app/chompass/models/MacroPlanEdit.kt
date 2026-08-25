package app.chompass.models

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Pure macro day-plan write path (Codeberg #60, docs/local/
 * MACRO_PROFILES_DESIGN.md § Write-path invariants). Every editor mutation
 * funnels through here so the invariants hold for the Settings UI, seeds, and
 * (phase 4) AI/Adaptive writes alike:
 *
 *  - each profile's calories within [CalorieSafety] floor/ceiling (MACRO-CYCLE-C)
 *  - `enabled` requires 2..7 profiles and a resolvable mode config
 *  - deleting a profile scrubs or re-points every reference (assignments,
 *    weekday map, cycle pattern, default)
 *  - `dayAssignments` pruned to today ± [MacroPlan.ASSIGNMENT_KEEP_DAYS]
 *
 * All functions are pure; persistence + UI stay in SettingsViewModel.
 */
object MacroPlanEdit {
    const val MIN_PROFILES = 2
    const val MAX_PROFILES = 7

    /** Master toggle. Enabling seeds a resolvable mode config when pieces are missing. */
    fun setEnabled(plan: MacroPlan?, enabled: Boolean, today: LocalDate): MacroPlan? {
        if (plan == null) return null
        if (!enabled) return normalized(plan.copy(enabled = false), today)
        if (plan.profiles.size !in MIN_PROFILES..MAX_PROFILES) {
            return normalized(plan.copy(enabled = false), today)
        }
        return normalized(seeded(plan, today).copy(enabled = true), today)
    }

    /**
     * Add or update one profile. Calories clamp to the CalorieSafety
     * floor/ceiling (MACRO-CYCLE-C); the caller enforces distinct non-empty
     * names. Never flips [MacroPlan.enabled] — the toggle owns that.
     */
    fun upsertProfile(
        plan: MacroPlan?,
        profile: MacroDayProfile,
        bmr: Double,
        tdee: Double,
        today: LocalDate,
    ): MacroPlan {
        val current = plan ?: MacroPlan()
        val clamped = profile.copy(
            calories = CalorieSafety.clampAuto(profile.calories, bmr, tdee),
        )
        val profiles = current.profiles.filterNot { it.id == clamped.id }
        val next = if (profiles.size >= MAX_PROFILES && profiles.none { it.id == clamped.id }) {
            current // cap reached: refuse new adds, keep updates
        } else {
            current.copy(profiles = profiles + clamped)
        }
        return normalized(next, today)
    }

    /**
     * Delete a profile and scrub or re-point every reference.
     * [replacementId] (an id surviving the delete) re-points references;
     * null scrubs them. When the cycle pattern drops below 2 entries the
     * mode falls back to MANUAL (the plan stays resolvable); when fewer than
     * [MIN_PROFILES] profiles remain the plan disables — data otherwise kept.
     */
    fun deleteProfile(
        plan: MacroPlan,
        id: String,
        replacementId: String?,
        today: LocalDate,
    ): MacroPlan {
        val remaining = plan.profiles.filterNot { it.id == id }
        if (remaining.size == plan.profiles.size) return plan
        val replace = replacementId?.takeIf { rep ->
            rep != id && remaining.any { it.id == rep }
        }
        val repoint: (String?) -> String? = { ref ->
            when {
                ref != id -> ref
                replace != null -> replace
                else -> null
            }
        }
        val next = plan.copy(
            profiles = remaining,
            defaultProfileId = repoint(plan.defaultProfileId),
            weekdayProfileIds = plan.weekdayProfileIds.mapNotNull { (day, ref) ->
                repoint(ref)?.let { day to it }
            }.toMap(),
            cyclePattern = plan.cyclePattern.mapNotNull(repoint),
            dayAssignments = plan.dayAssignments.mapNotNull { (date, ref) ->
                repoint(ref)?.let { date to it }
            }.toMap(),
        )
        return normalized(next, today)
    }

    /** Reorder the profile list (display order + pattern-builder chip order). */
    fun reorder(plan: MacroPlan, orderedIds: List<String>): MacroPlan = plan.copy(
        profiles = orderedIds.mapNotNull { id -> plan.profiles.firstOrNull { it.id == id } }
            .ifEmpty { plan.profiles }
    )

    /** Switch mode, seeding the new mode's config so it resolves immediately. */
    fun setMode(plan: MacroPlan, mode: MacroPlanMode, today: LocalDate): MacroPlan {
        val base = if (mode == MacroPlanMode.CYCLE) {
            plan.copy(cycleAnchorDay = plan.cycleAnchorDay ?: today.toString())
        } else {
            plan
        }
        return normalized(seeded(base.copy(mode = mode), today), today)
    }

    fun setDefault(plan: MacroPlan, profileId: String?): MacroPlan =
        plan.copy(defaultProfileId = profileId)

    /** Weekday map write; null clears the entry (falls back to the default). */
    fun setWeekday(plan: MacroPlan, day: DayOfWeek, profileId: String?): MacroPlan =
        if (profileId == null) {
            plan.copy(weekdayProfileIds = plan.weekdayProfileIds - day.name)
        } else {
            plan.copy(weekdayProfileIds = plan.weekdayProfileIds + (day.name to profileId))
        }

    fun setCyclePattern(plan: MacroPlan, pattern: List<String>): MacroPlan =
        plan.copy(cyclePattern = pattern.filter { plan.profileById(it) != null })

    /** Re-anchor the cycle so the pattern starts over at [today]. */
    fun restartCycle(plan: MacroPlan, today: LocalDate): MacroPlan =
        plan.copy(cycleAnchorDay = today.toString())

    /** Per-day override write for ANY mode; null removes the override. */
    fun setDayAssignment(plan: MacroPlan, date: LocalDate, profileId: String?): MacroPlan =
        if (profileId == null) {
            plan.copy(dayAssignments = plan.dayAssignments - date.toString())
        } else {
            plan.copy(dayAssignments = plan.dayAssignments + (date.toString() to profileId))
        }

    /** Keto transition (settled Q4): pause the plan, keep every byte of data. */
    fun pausedForKeto(plan: MacroPlan?): MacroPlan? =
        plan?.copy(enabled = false)

    /** Whether any schedule piece (default, weekday map, pattern, assignments) points at [id]. */
    fun isProfileReferenced(plan: MacroPlan?, id: String?): Boolean {
        if (plan == null || id == null) return false
        return plan.defaultProfileId == id ||
            plan.weekdayProfileIds.values.any { it == id } ||
            plan.cyclePattern.any { it == id } ||
            plan.dayAssignments.values.any { it == id }
    }

    /**
     * Fill in a resolvable config: default profile when missing/unknown, and a
     * usable CYCLE pattern + anchor when mode is CYCLE.
     */
    private fun seeded(plan: MacroPlan, today: LocalDate): MacroPlan {
        var next = plan
        val validDefault = next.defaultProfileId?.let { id -> next.profileById(id) != null } == true
        if (!validDefault) {
            next = next.copy(defaultProfileId = next.profiles.firstOrNull()?.id)
        }
        if (next.mode == MacroPlanMode.CYCLE) {
            val pattern = next.cyclePattern.filter { next.profileById(it) != null }
            if (pattern.size < MIN_PROFILES) {
                next = next.copy(
                    cyclePattern = next.profiles.take(MAX_PROFILES).map { it.id },
                    cycleAnchorDay = next.cycleAnchorDay ?: today.toString(),
                )
            }
        }
        return next
    }

    /**
     * The shared write-path guard: prune assignments to the keep window, drop
     * references to unknown profiles (defensive — deletes scrub, sync may not),
     * fall back to MANUAL when a CYCLE config stops resolving (pattern below 2
     * or missing anchor), and disable when the profile count leaves 2..7.
     */
    internal fun normalized(plan: MacroPlan, today: LocalDate): MacroPlan {
        val cycleBroken = plan.mode == MacroPlanMode.CYCLE &&
            (plan.cyclePattern.size < MIN_PROFILES || plan.cycleAnchorDay == null)
        return plan.copy(
            enabled = plan.enabled && plan.profiles.size in MIN_PROFILES..MAX_PROFILES,
            mode = if (cycleBroken) MacroPlanMode.MANUAL else plan.mode,
            defaultProfileId = plan.defaultProfileId?.takeIf { plan.profileById(it) != null },
            weekdayProfileIds = plan.weekdayProfileIds.filterValues { plan.profileById(it) != null },
            cyclePattern = plan.cyclePattern.filter { plan.profileById(it) != null },
            dayAssignments = plan.prunedAssignments(today).filterValues { plan.profileById(it) != null },
        )
    }
}
