package app.chompass.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.TimeZone
import java.util.UUID

class GoalPhaseEditTest {
    private val today = LocalDate.of(2026, 8, 10)

    @Before
    fun utcZone() {
        // Day-bucketing and assignment windows use the system zone; pin it so
        // the assertions are deterministic.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    private fun profile(plan: MacroPlan? = null) = UserProfile(
        weightKg = 80.0,
        goalWeightKg = 74.0,
        customCalories = 2200,
        customProtein = 150,
        customCarbs = 220,
        customFat = 70,
        macroPlan = plan,
    )

    private fun phase(
        name: String = "Shred",
        intent: PhaseIntent = PhaseIntent.CUT,
        start: LocalDate = today.plusDays(1),
        end: LocalDate = today.plusDays(14),
        profileId: String? = null,
    ) = GoalPhase(
        name = name,
        intent = intent,
        startDate = start.toString(),
        endDate = end.toString(),
        calories = 1800,
        proteinG = 160,
        carbsG = 150,
        fatG = 55,
        profileId = profileId,
    )

    private fun rawPhase(start: String, end: String) = GoalPhase(
        name = "Raw",
        intent = PhaseIntent.CUT,
        startDate = start,
        endDate = end,
        calories = 1800,
        proteinG = 160,
        carbsG = 150,
        fatG = 55,
    )

    private fun assignmentTargets(plan: MacroPlan, id: String) =
        plan.dayAssignments.filterValues { it == id }.keys.sorted()

    @Test
    fun validateRejectsBlankNameInvertedRangeAndOversize() {
        val p = profile()
        assertEquals(PhaseEditError.BlankName, GoalPhaseEdit.validate(p, phase(name = "  ")))
        assertEquals(
            PhaseEditError.BadRange,
            GoalPhaseEdit.validate(p, phase(start = today.plusDays(10), end = today.plusDays(5))),
        )
        // Unparseable start date → BadRange.
        assertEquals(
            PhaseEditError.BadRange,
            GoalPhaseEdit.validate(p, rawPhase(start = "not-a-date", end = today.plusDays(5).toString())),
        )
        assertEquals(
            PhaseEditError.TooLong,
            GoalPhaseEdit.validate(
                p,
                phase(start = today, end = today.plusDays(MacroPlan.ASSIGNMENT_KEEP_DAYS)),
            ),
        )
        // Exactly the keep window is fine.
        assertNull(
            GoalPhaseEdit.validate(
                p,
                phase(start = today, end = today.plusDays(MacroPlan.ASSIGNMENT_KEEP_DAYS - 1)),
            ),
        )
    }

    @Test
    fun validateRejectsNewPhaseWhenEnabledPlanIsFull() {
        val profiles = (1..MacroPlanEdit.MAX_PROFILES).map {
            MacroDayProfile(id = "p$it", name = "T$it", calories = 2000, proteinG = 1, carbsG = 1, fatG = 1)
        }
        val full = profile(MacroPlan(enabled = true, profiles = profiles, mode = MacroPlanMode.MANUAL))
        assertEquals(PhaseEditError.NoProfileSlot, GoalPhaseEdit.validate(full, phase()))
        // An existing phase profile (update path) still validates.
        val existing = phase(profileId = "p1")
        assertNull(GoalPhaseEdit.validate(full, existing))
        // Disabled or absent plans never trip the capacity check.
        assertNull(GoalPhaseEdit.validate(profile(MacroPlan(enabled = false, profiles = profiles)), phase()))
        assertNull(GoalPhaseEdit.validate(profile(), phase()))
    }

    @Test
    fun applyOnNoPlanCreatesBasePlusPhaseAndAssignsClippedRange() {
        val p = profile()
        val start = today.minusDays(5) // clipped to today - 366 → in window
        val end = today.plusDays(400) // clipped to today + 366
        val next = GoalPhaseEdit.applyPhase(p, phase(start = start, end = end), today)
        val plan = next.macroPlan
        assertNotNull(plan)
        assertTrue(plan!!.enabled)
        assertEquals(MacroPlanMode.MANUAL, plan.mode)
        assertEquals(2, plan.profiles.size)
        val base = plan.profiles.first { it.name == GoalPhaseEdit.BASE_PROFILE_NAME }
        assertEquals(plan.defaultProfileId, base.id)
        // Base mirrors the profile's effective targets.
        val effective = MacroPlanResolver.baseTargets(p)
        assertEquals(effective.calories, base.calories)
        assertEquals(effective.proteinG, base.proteinG)
        val stored = next.goalPhases.single()
        assertNotNull(stored.profileId)
        val assigned = assignmentTargets(plan, stored.profileId!!)
        // Start sits inside the keep window → unclipped; the 400-day end clips
        // to today + ASSIGNMENT_KEEP_DAYS.
        assertEquals(start.toString(), assigned.first())
        assertEquals(today.plusDays(MacroPlan.ASSIGNMENT_KEEP_DAYS).toString(), assigned.last())
    }

    @Test
    fun applyOnExistingPlanUpdatesInPlaceAndKeepsManualDays() {
        val extra = MacroDayProfile(id = "extra", name = "Extra", calories = 1900, proteinG = 1, carbsG = 1, fatG = 1)
        val manualDay = today.plusDays(35) // outside the phase range
        val plan = MacroPlan(
            enabled = true,
            profiles = listOf(extra),
            mode = MacroPlanMode.MANUAL,
            defaultProfileId = extra.id,
            dayAssignments = mapOf(manualDay.toString() to extra.id),
        )
        val start = today.plusDays(10)
        val end = today.plusDays(30)
        val first = GoalPhaseEdit.applyPhase(profile(plan), phase(start = start, end = end), today)
        val stored = first.goalPhases.single()
        val plan1 = first.macroPlan!!
        assertTrue(plan1.enabled)
        assertEquals(2, plan1.profiles.size)
        val expectedDays = generateSequence(start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(end) }
            .map { it.toString() }
            .sorted()
            .toList()
        assertEquals(expectedDays, assignmentTargets(plan1, stored.profileId!!))
        // Manual day outside the phase survives.
        assertEquals(extra.id, plan1.dayAssignments[manualDay.toString()])
        // Default profile untouched (days outside phases keep the plan default).
        assertEquals(extra.id, plan1.defaultProfileId)

        // Re-apply with different targets: updates the same profile + phase id,
        // createdAtMillis stable.
        val edited = stored.copy(calories = 1600, createdAtMillis = 12345L)
        val second = GoalPhaseEdit.applyPhase(first, edited, today)
        val plan2 = second.macroPlan!!
        assertEquals(2, plan2.profiles.size)
        assertEquals(1600, plan2.profileById(stored.profileId)!!.calories)
        assertEquals(12345L, second.goalPhases.single().createdAtMillis)
        assertEquals(stored.id, second.goalPhases.single().id)
    }

    @Test
    fun overlapOverwriteLaterWriteWins() {
        val cut = GoalPhaseEdit.applyPhase(
            profile(),
            phase(name = "Cut", start = today.plusDays(1), end = today.plusDays(20)),
            today,
        )
        val bulk = phase(name = "Bulk", intent = PhaseIntent.BULK, start = today.plusDays(10), end = today.plusDays(30))
        val both = GoalPhaseEdit.applyPhase(cut, bulk, today)
        val overlapDay = today.plusDays(15)
        // The bulk (applied later) owns the overlap day.
        val bulkRow = both.goalPhases.first { it.name == "Bulk" }
        assertEquals(bulkRow.profileId, both.macroPlan!!.dayAssignments[overlapDay.toString()])
        // Cut-only days still point at the cut profile.
        val cutRow = both.goalPhases.first { it.name == "Cut" }
        assertEquals(cutRow.profileId, both.macroPlan!!.dayAssignments[today.plusDays(5).toString()])
        assertEquals(2, both.goalPhases.size)
    }

    @Test
    fun removePhaseCleansOnlyOwnAssignmentsAndProfile() {
        val extra = MacroDayProfile(id = "extra", name = "Extra", calories = 1900, proteinG = 1, carbsG = 1, fatG = 1)
        val basePlan = MacroPlan(
            enabled = true,
            profiles = listOf(extra),
            mode = MacroPlanMode.MANUAL,
            defaultProfileId = extra.id,
        )
        val applied = GoalPhaseEdit.applyPhase(
            profile(basePlan),
            phase(start = today.plusDays(1), end = today.plusDays(10)),
            today,
        )
        val phaseRow = applied.goalPhases.single()
        val planAfterApply = applied.macroPlan!!
        assertEquals(phaseRow.profileId, planAfterApply.dayAssignments[today.plusDays(5).toString()])
        // Manual switch inside the range to another profile, written AFTER the
        // phase — removal must not clobber it.
        val withManual = applied.copy(
            macroPlan = MacroPlanEdit.setDayAssignment(planAfterApply, today.plusDays(3), extra.id),
        )
        val removed = GoalPhaseEdit.removePhase(withManual, phaseRow.id, today)
        assertNull(removed.macroPlan!!.dayAssignments[today.plusDays(5).toString()])
        assertEquals(extra.id, removed.macroPlan!!.dayAssignments[today.plusDays(3).toString()])
        assertTrue(removed.goalPhases.isEmpty())
        // The phase profile lost its last reference → deleted; the plan drops
        // below the 2-profile minimum and disables (data otherwise kept).
        assertFalse(removed.macroPlan!!.enabled)
        assertEquals(listOf("extra"), removed.macroPlan!!.profiles.map { it.id })
    }

    @Test
    fun removePhaseOnAutoPlanDisablesAndKeepsBaseData() {
        val applied = GoalPhaseEdit.applyPhase(profile(), phase(), today)
        val phaseRow = applied.goalPhases.single()
        val removed = GoalPhaseEdit.removePhase(applied, phaseRow.id, today)
        // Two-profile plan drops below the 2-profile minimum → disabled, data kept.
        val plan = removed.macroPlan!!
        assertFalse(plan.enabled)
        assertEquals(1, plan.profiles.size)
        assertTrue(plan.dayAssignments.isEmpty())
        assertTrue(removed.goalPhases.isEmpty())
    }

    @Test
    fun removeUnknownPhaseIsNoOp() {
        val p = profile()
        assertEquals(p, GoalPhaseEdit.removePhase(p, UUID.randomUUID(), today))
    }

    @Test
    fun phaseContainsUsesInclusiveIsoRange() {
        val p = phase(start = LocalDate.of(2026, 8, 1), end = LocalDate.of(2026, 8, 10))
        assertTrue("2026-08-01" in p)
        assertTrue("2026-08-10" in p)
        assertFalse("2026-08-11" in p)
        assertEquals(10L, p.spanDays)
    }
}
