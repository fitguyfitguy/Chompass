package app.chompass.ui.progress

import app.chompass.models.GoalPhase
import java.time.LocalDate

/** One day's intake-vs-goal pairing for adherence math. */
data class DayAdherence(
    val date: LocalDate,
    val intakeKcal: Int?,
    val goalKcal: Int?,
    /** Day-type profile id in effect that day (null = base targets). */
    val profileId: String?,
    /** Day-type name snapshot from the journal that day (null = unknown). */
    val profileName: String? = null,
    val untracked: Boolean,
)

/** Per-day-type rollup; the UI resolves [profileId] → name + color via the plan. */
data class DayTypeAdherence(
    val profileId: String,
    /** Journal name snapshot, shown when the plan no longer knows [profileId]. */
    val profileName: String? = null,
    val days: Int,
    val avgIntake: Int?,
    val avgGoal: Int?,
    val avgDelta: Int?,
)

/** One phase's overlap with the analyzed range. */
data class PhaseAdherence(
    val phase: GoalPhase,
    val overlapDays: Int,
    val avgDelta: Int?,
    val netKcal: Long?,
)

/**
 * Range adherence summary for the Progress tab (Progress v2): intake vs
 * journaled goals, a ±100 kcal on-target band (same band as DailySummaryPolicy),
 * and per-day-type / per-phase rollups. Phases map onto day types, so a phase's
 * days also appear under their day-type row.
 */
data class AdherenceSummary(
    val loggedDays: Int,
    val avgIntake: Int?,
    val avgGoal: Int?,
    val avgDelta: Int?,
    /** Sum of (intake − goal) over goal-bearing days. */
    val netKcal: Long?,
    /** % of goal-bearing days inside the ±100 kcal band. */
    val withinBandPct: Int?,
    val overDays: Int,
    val underDays: Int,
    val byDayType: List<DayTypeAdherence>,
    val byPhase: List<PhaseAdherence>,
)

/**
 * Pure adherence math over the unbucketed calorie slots (the same series the
 * calorie chart plots). Excludes untracked days, days with nothing logged,
 * and today — matching the calorieAverage today-exclusion rule.
 */
object ProgressAdherence {
    /** Same on-target band as services/DailySummaryPolicy. */
    const val TARGET_BAND_KCAL = 100

    fun computeAdherence(
        slots: List<CalorieSlot>,
        goals: Map<LocalDate, Int>,
        dayTypeByDay: Map<String, String>,
        dayTypeNameByDay: Map<String, String> = emptyMap(),
        untrackedDays: Set<String>,
        phases: List<GoalPhase>,
        range: ClosedRange<LocalDate>,
        today: LocalDate = LocalDate.now(),
    ): AdherenceSummary {
        val days = slots.asSequence()
            .filter { it.day in range }
            .filter { !it.untracked && it.day.toString() !in untrackedDays }
            .filter { it.day.isBefore(today) }
            .filter { it.kcal != null }
            .map {
                DayAdherence(
                    it.day,
                    it.kcal,
                    goals[it.day],
                    dayTypeByDay[it.day.toString()],
                    dayTypeNameByDay[it.day.toString()],
                    it.untracked,
                )
            }
            .filter { it.intakeKcal != null }
            .sortedBy { it.date }
            .toList()
        if (days.isEmpty()) {
            return AdherenceSummary(
                loggedDays = 0, avgIntake = null, avgGoal = null, avgDelta = null,
                netKcal = null, withinBandPct = null, overDays = 0, underDays = 0,
                byDayType = emptyList(),
                byPhase = phases.map { PhaseAdherence(it, 0, null, null) },
            )
        }
        val withGoal = days.filter { it.goalKcal != null }
        val avgIntake = days.map { it.intakeKcal!! }.average().toInt()
        // No logged day carries a goal → the goal-derived aggregates stay
        // null (mirrors withinBandPct) instead of averaging an empty list.
        val avgGoal = if (withGoal.isEmpty()) null else withGoal.map { it.goalKcal!! }.average().toInt()
        val avgDelta = if (withGoal.isEmpty()) null else withGoal.map { it.intakeKcal!! - it.goalKcal!! }.average().toInt()
        val netKcal = if (withGoal.isEmpty()) null else withGoal.sumOf { (it.intakeKcal!! - it.goalKcal!!).toLong() }
        val withinBandPct = if (withGoal.isEmpty()) null else {
            val deltas = withGoal.map { it.intakeKcal!! - it.goalKcal!! }
            val within = deltas.count { kotlin.math.abs(it) <= TARGET_BAND_KCAL }
            (within * 100) / withGoal.size
        }
        val overDays = if (withGoal.isEmpty()) 0 else withGoal.count { it.intakeKcal!! - it.goalKcal!! > TARGET_BAND_KCAL }
        val underDays = if (withGoal.isEmpty()) 0 else withGoal.count { it.intakeKcal!! - it.goalKcal!! < -TARGET_BAND_KCAL }

        val byDayType = days.filter { it.profileId != null && it.goalKcal != null }
            .groupBy { it.profileId!! }
            .map { (profileId, group) ->
                DayTypeAdherence(
                    profileId = profileId,
                    profileName = group.firstNotNullOfOrNull { it.profileName },
                    days = group.size,
                    avgIntake = group.map { it.intakeKcal!! }.average().toInt(),
                    avgGoal = group.map { it.goalKcal!! }.average().toInt(),
                    avgDelta = group.map { it.intakeKcal!! - it.goalKcal!! }.average().toInt(),
                )
            }
            .sortedBy { it.profileId }

        val byPhase = phases.map { phase ->
            val inPhase = withGoal.filter { phase.contains(it.date.toString()) }
            if (inPhase.isEmpty()) {
                PhaseAdherence(phase, 0, null, null)
            } else {
                val phaseDeltas = inPhase.map { it.intakeKcal!! - it.goalKcal!! }
                PhaseAdherence(
                    phase = phase,
                    overlapDays = inPhase.size,
                    avgDelta = phaseDeltas.average().toInt(),
                    netKcal = phaseDeltas.sumOf { it.toLong() },
                )
            }
        }

        return AdherenceSummary(
            loggedDays = days.size,
            avgIntake = avgIntake,
            avgGoal = avgGoal,
            avgDelta = avgDelta,
            netKcal = netKcal,
            withinBandPct = withinBandPct,
            overDays = overDays,
            underDays = underDays,
            byDayType = byDayType,
            byPhase = byPhase,
        )
    }
}
