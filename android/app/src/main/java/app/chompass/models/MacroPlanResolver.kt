package app.chompass.models

import java.time.LocalDate
import kotlin.math.roundToInt

/** Plain per-day target numbers, no provenance. */
data class DayTargets(
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
)

/** One day's resolved targets plus which day-type profile produced them. */
data class ResolvedDayTargets(
    val targets: DayTargets,
    /** Day-type profile in effect; null = base targets (plan off, empty, or fallback). */
    val profileId: String?,
    val profileName: String?,
)

/**
 * Pure macro day-plan resolution (Codeberg #60, docs/local/MACRO_PROFILES_DESIGN.md).
 *
 * MACRO-CYCLE-A `resolve`: day assignment → mode (MANUAL / WEEKDAYS / CYCLE) →
 * default → base targets. Cycle math is epoch-day integers + floorMod only, so
 * the PWA mirror (`web/app/src/lib/chompass-core/macro-plan.js`) is exactly
 * equivalent — gated by `testdata/parity/macro-plan-expected.json`.
 *
 * MACRO-CYCLE-B `averageForward`: unweighted mean over a forward window
 * (pattern length for CYCLE, else 7 days) — the planning number for Adaptive
 * baseline / Coach "this week" / forecast.
 *
 * MACRO-CYCLE-D `journalAverage`: mean over journaled days in a past range;
 * gaps are skipped, never filled (gap-filling would smuggle today's config
 * into history).
 */
object MacroPlanResolver {
    /** MACRO-CYCLE-A. */
    fun resolve(plan: MacroPlan?, base: DayTargets, date: LocalDate): ResolvedDayTargets {
        if (plan == null || !plan.enabled || plan.profiles.isEmpty()) {
            return ResolvedDayTargets(base, null, null)
        }
        val assignmentId = plan.dayAssignments[date.toString()]
        val modeId = when (plan.mode) {
            MacroPlanMode.MANUAL -> plan.defaultProfileId
            MacroPlanMode.WEEKDAYS -> plan.weekdayProfileIds[date.dayOfWeek.name] ?: plan.defaultProfileId
            MacroPlanMode.CYCLE -> cycleProfileId(plan, date)
        }
        val profile = plan.profileById(assignmentId)
            ?: plan.profileById(modeId)
            ?: plan.profileById(plan.defaultProfileId)
            ?: return ResolvedDayTargets(base, null, null)
        return ResolvedDayTargets(
            targets = DayTargets(profile.calories, profile.proteinG, profile.carbsG, profile.fatG),
            profileId = profile.id,
            profileName = profile.name,
        )
    }

    private fun cycleProfileId(plan: MacroPlan, date: LocalDate): String? {
        if (plan.cyclePattern.isEmpty()) return plan.defaultProfileId
        val anchor = plan.cycleAnchorDay?.let { GoalJournal.parseDateOrNull(it) } ?: return plan.defaultProfileId
        val index = Math.floorMod((date.toEpochDay() - anchor.toEpochDay()).toInt(), plan.cyclePattern.size)
        return plan.cyclePattern[index]
    }

    // -- Convenience over UserProfile -----------------------------------------

    fun baseTargets(profile: UserProfile): DayTargets = DayTargets(
        calories = profile.effectiveCalories,
        proteinG = profile.effectiveProtein,
        carbsG = profile.effectiveCarbs,
        fatG = profile.effectiveFat,
    )

    fun targetsFor(profile: UserProfile, date: LocalDate): ResolvedDayTargets =
        resolve(profile.macroPlan, baseTargets(profile), date)

    /**
     * Journal-first read rule (#60 phase 3): past + today consult the goal
     * journal before the resolver — frozen actuals win even when the plan has
     * since been edited or disabled (history never rewrites); gaps and future
     * days resolve live from the plan. Mirrors `resolveDayJournaled` in the
     * PWA `macro-plan.js` (goldens: `resolveJournaled` scenarios).
     */
    fun resolveJournaled(
        journal: List<GoalJournalEntry>,
        plan: MacroPlan?,
        base: DayTargets,
        date: LocalDate,
        today: LocalDate,
    ): ResolvedDayTargets {
        if (!date.isAfter(today)) {
            val entry = journal.firstOrNull { it.date == date.toString() }
            if (entry != null) {
                return ResolvedDayTargets(
                    targets = DayTargets(entry.calories, entry.proteinG, entry.carbsG, entry.fatG),
                    profileId = entry.profileId,
                    profileName = entry.profileName,
                )
            }
        }
        return resolve(plan, base, date)
    }

    /**
     * [resolveJournaled] over a [UserProfile]: plan + base targets come from
     * the profile; a null profile resolves zero targets (the DiaryExporter
     * no-profile shape) unless a journaled entry covers the day.
     */
    fun targetsForJournaled(
        journal: List<GoalJournalEntry>,
        profile: UserProfile?,
        date: LocalDate,
        today: LocalDate = LocalDate.now(),
    ): ResolvedDayTargets =
        resolveJournaled(journal, profile?.macroPlan, profile?.let(::baseTargets) ?: DayTargets(0, 0, 0, 0), date, today)

    // -- Averages ---------------------------------------------------------------

    /** MACRO-CYCLE-B: forward window mean starting [today] (overrides count). */
    fun averageForward(
        plan: MacroPlan?,
        base: DayTargets,
        today: LocalDate,
        windowDays: Int? = null,
    ): DayTargets {
        if (plan == null || !plan.enabled || plan.profiles.isEmpty()) return base
        val window = windowDays
            ?: if (plan.mode == MacroPlanMode.CYCLE && plan.cyclePattern.isNotEmpty()) plan.cyclePattern.size else 7
        val days = (0 until window).map { offset -> resolve(plan, base, today.plusDays(offset.toLong())).targets }
        return mean(days)
    }

    /** MACRO-CYCLE-D: mean over journaled days in [from, to]; gaps skipped. Null when empty. */
    fun journalAverage(entries: List<GoalJournalEntry>, from: LocalDate, to: LocalDate): DayTargets? {
        val inRange = entries.mapNotNull { e ->
            GoalJournal.parseDateOrNull(e.date)?.takeIf { !it.isBefore(from) && !it.isAfter(to) }
                ?.let { DayTargets(e.calories, e.proteinG, e.carbsG, e.fatG) }
        }
        if (inRange.isEmpty()) return null
        return mean(inRange)
    }

    private fun mean(days: List<DayTargets>): DayTargets = DayTargets(
        calories = days.map { it.calories }.average().roundToInt(),
        proteinG = days.map { it.proteinG }.average().roundToInt(),
        carbsG = days.map { it.carbsG }.average().roundToInt(),
        fatG = days.map { it.fatG }.average().roundToInt(),
    )
}
