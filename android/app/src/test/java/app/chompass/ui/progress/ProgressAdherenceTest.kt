package app.chompass.ui.progress

import app.chompass.models.GoalPhase
import app.chompass.models.PhaseIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ProgressAdherenceTest {
    private val start = LocalDate.of(2026, 8, 1)
    private val today = LocalDate.of(2026, 8, 11)

    private fun slots(vararg dayKcal: Pair<Int, Int?>): List<CalorieSlot> =
        dayKcal.mapIndexed { i, (offset, kcal) ->
            CalorieSlot(day = start.plusDays(offset.toLong()), kcal = kcal, untracked = false)
        }

    private fun phase(
        name: String,
        intent: PhaseIntent,
        from: LocalDate,
        to: LocalDate,
    ) = GoalPhase(
        name = name,
        intent = intent,
        startDate = from.toString(),
        endDate = to.toString(),
        calories = 2000,
        proteinG = 150,
        carbsG = 200,
        fatG = 65,
    )

    @Test
    fun emptyInputYieldsNulls() {
        val summary = ProgressAdherence.computeAdherence(
            slots = emptyList(),
            goals = emptyMap(),
            dayTypeByDay = emptyMap(),
            untrackedDays = emptySet(),
            phases = emptyList(),
            range = start..start.plusDays(9),
            today = today,
        )
        assertEquals(0, summary.loggedDays)
        assertNull(summary.avgIntake)
        assertNull(summary.avgGoal)
        assertNull(summary.avgDelta)
        assertNull(summary.netKcal)
        assertNull(summary.withinBandPct)
        assertEquals(0, summary.overDays)
        assertEquals(0, summary.underDays)
    }

    @Test
    fun averagesBandsAndNetOverGoalBearingDays() {
        // Days 0..3 logged with goals: deltas +50, -50, +150, -150.
        val slots = slots(
            0 to 2050, 1 to 1950, 2 to 2150, 3 to 1850,
            // Day 4 logged but outside the analyzed range end.
            9 to 3000,
        )
        val goals = mapOf(
            start to 2000,
            start.plusDays(1) to 2000,
            start.plusDays(2) to 2000,
            start.plusDays(3) to 2000,
        )
        val summary = ProgressAdherence.computeAdherence(
            slots = slots,
            goals = goals,
            dayTypeByDay = emptyMap(),
            untrackedDays = emptySet(),
            phases = emptyList(),
            range = start..start.plusDays(7),
            today = today,
        )
        assertEquals(4, summary.loggedDays)
        assertEquals(2000, summary.avgIntake)
        assertEquals(2000, summary.avgGoal)
        assertEquals(0, summary.avgDelta)
        assertEquals(0L, summary.netKcal)
        assertEquals(50, summary.withinBandPct) // 2 of 4 inside ±100
        assertEquals(1, summary.overDays)
        assertEquals(1, summary.underDays)
    }

    @Test
    fun loggedDaysWithoutGoalsYieldNullGoalAggregates() {
        // Logged days, but none carries a goal → goal-derived aggregates stay
        // null instead of averaging an empty list (NaN → 0 fabrications).
        val summary = ProgressAdherence.computeAdherence(
            slots = slots(0 to 2050, 1 to 1950, 2 to 2100),
            goals = emptyMap(),
            dayTypeByDay = emptyMap(),
            untrackedDays = emptySet(),
            phases = emptyList(),
            range = start..start.plusDays(7),
            today = today,
        )
        assertEquals(3, summary.loggedDays)
        assertEquals(2033, summary.avgIntake)
        assertNull(summary.avgGoal)
        assertNull(summary.avgDelta)
        assertNull(summary.netKcal)
        assertNull(summary.withinBandPct)
        assertEquals(0, summary.overDays)
        assertEquals(0, summary.underDays)
    }

    @Test
    fun excludesTodayUntrackedAndMissingEntries() {
        val today2 = start.plusDays(3)
        val slots = listOf(
            CalorieSlot(start, 2000, false),
            CalorieSlot(start.plusDays(1), null, false), // nothing logged
            CalorieSlot(start.plusDays(2), 2500, true), // untracked slot
            CalorieSlot(today2, 9999, false), // today
        )
        val goals = mapOf(
            start to 2000,
            start.plusDays(1) to 2000,
            start.plusDays(2) to 2000,
            today2 to 2000,
        )
        val summary = ProgressAdherence.computeAdherence(
            slots = slots,
            goals = goals,
            dayTypeByDay = emptyMap(),
            untrackedDays = setOf(start.plusDays(2).toString()),
            phases = emptyList(),
            range = start..start.plusDays(3),
            today = today2,
        )
        assertEquals(1, summary.loggedDays)
        assertEquals(2000, summary.avgIntake)
        assertEquals(0, summary.avgDelta)
    }

    @Test
    fun dayTypeGroupingOnlyCountsGoalBearingDays() {
        val slots = slots(0 to 2200, 1 to 1800, 2 to 2000)
        val goals = mapOf(
            start to 2000,
            start.plusDays(1) to 2000,
            // Day 2 has no goal row → excluded from day-type stats.
        )
        val dayTypeByDay = mapOf(
            start.toString() to "training",
            start.plusDays(1).toString() to "rest",
            start.plusDays(2).toString() to "training",
        )
        val summary = ProgressAdherence.computeAdherence(
            slots = slots,
            goals = goals,
            dayTypeByDay = dayTypeByDay,
            untrackedDays = emptySet(),
            phases = emptyList(),
            range = start..start.plusDays(2),
            today = today,
        )
        val byType = summary.byDayType.associateBy { it.profileId }
        assertEquals(2, byType.size)
        assertEquals(1, byType.getValue("training").days)
        assertEquals(+200, byType.getValue("training").avgDelta)
        assertEquals(-200, byType.getValue("rest").avgDelta)
    }

    @Test
    fun journalNameMapRescuesPlanMissedProfileId() {
        // Profile deleted from the plan: only the journal's write-time name
        // snapshot can label the row (the reported UUID-id bug).
        val missedId = "94f0267e-1a2b-3c4d-5e6f-0a1b2c3d4e5f"
        val slots = slots(0 to 2100, 1 to 1900)
        val goals = mapOf(
            start to 2000,
            start.plusDays(1) to 2000,
        )
        val dayTypeByDay = mapOf(
            start.toString() to missedId,
            start.plusDays(1).toString() to missedId,
        )
        val summary = ProgressAdherence.computeAdherence(
            slots = slots,
            goals = goals,
            dayTypeByDay = dayTypeByDay,
            dayTypeNameByDay = mapOf(
                start.toString() to "Cut (old)",
                start.plusDays(1).toString() to "Cut (old)",
            ),
            untrackedDays = emptySet(),
            phases = emptyList(),
            range = start..start.plusDays(1),
            today = today,
        )
        val row = summary.byDayType.single()
        assertEquals(missedId, row.profileId)
        assertEquals("Cut (old)", row.profileName)

        // No snapshot journaled → the name stays null and the UI falls back
        // to the raw id.
        val unnamed = ProgressAdherence.computeAdherence(
            slots = slots,
            goals = goals,
            dayTypeByDay = dayTypeByDay,
            untrackedDays = emptySet(),
            phases = emptyList(),
            range = start..start.plusDays(1),
            today = today,
        )
        assertNull(unnamed.byDayType.single().profileName)
    }

    @Test
    fun phaseOverlapMathAndEmptyPhasesCarryThrough() {
        val slots = slots(0 to 1600, 1 to 1700, 2 to 2100, 3 to 1500)
        val goals = (0..3L).associate { start.plusDays(it) to 2000 }
        val cut = phase("Cut", PhaseIntent.CUT, start, start.plusDays(1))
        val future = phase("Later", PhaseIntent.BULK, start.plusDays(5), start.plusDays(8))
        val summary = ProgressAdherence.computeAdherence(
            slots = slots,
            goals = goals,
            dayTypeByDay = emptyMap(),
            untrackedDays = emptySet(),
            phases = listOf(cut, future),
            range = start..start.plusDays(3),
            today = today,
        )
        val byPhase = summary.byPhase.associateBy { it.phase.name }
        // Cut overlaps days 0-1: deltas -400, -300 → avg -350, net -700.
        val cutRow = byPhase.getValue("Cut")
        assertEquals(2, cutRow.overlapDays)
        assertEquals(-350, cutRow.avgDelta)
        assertEquals(-700L, cutRow.netKcal)
        // Later phase starts outside the analyzed window with data → empty.
        val laterRow = byPhase.getValue("Later")
        assertEquals(0, laterRow.overlapDays)
        assertNull(laterRow.avgDelta)
        assertNull(laterRow.netKcal)
    }
}
