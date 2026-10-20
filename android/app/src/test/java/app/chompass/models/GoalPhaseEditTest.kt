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
        // Disabled plans revive: a fresh Base appends on top of the phase
        // profile, so a full plan refuses too.
        assertEquals(
            PhaseEditError.NoProfileSlot,
            GoalPhaseEdit.validate(profile(MacroPlan(enabled = false, profiles = profiles)), phase()),
        )
        val roomy = profiles.take(MacroPlanEdit.MAX_PROFILES - 2)
        assertNull(GoalPhaseEdit.validate(profile(MacroPlan(enabled = false, profiles = roomy)), phase()))
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
    fun applyPhaseRebalancesMacrosToStoredCalories() {
        val next = GoalPhaseEdit.applyPhase(profile(), phase(), today)
        val stored = next.goalPhases.single()
        val clamped = CalorieSafety.clampAuto(1800, profile().bmr, profile().tdee)
        assertEquals(clamped, stored.calories)
        val planProfile = next.macroPlan!!.profiles.first { it.id == stored.profileId }
        // Stored phase and plan profile carry the same balanced grams.
        assertEquals(stored.proteinG, planProfile.proteinG)
        assertEquals(stored.carbsG, planProfile.carbsG)
        assertEquals(stored.fatG, planProfile.fatG)
        // STANDARD keep-protein/fat: macros add up to the stored total
        // (carb truncation leaves ≤3 kcal of rounding slack).
        val macroKcal = 4 * stored.proteinG + 4 * stored.carbsG + 9 * stored.fatG
        assertTrue("macro kcal $macroKcal vs $clamped", macroKcal in (clamped - 3)..clamped)
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
        // createdAtMillis stable. Calories clamp to the safety floor, so the
        // new value rides just above it.
        val newKcal = CalorieSafety.floorKcal(profile(plan).bmr) + 100
        val edited = stored.copy(calories = newKcal, createdAtMillis = 12345L)
        val second = GoalPhaseEdit.applyPhase(first, edited, today)
        val plan2 = second.macroPlan!!
        assertEquals(2, plan2.profiles.size)
        assertEquals(newKcal, plan2.profileById(stored.profileId)!!.calories)
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

    @Test
    fun shrinkClearsStaleAssignments() {
        val start = today.plusDays(1)
        val applied = GoalPhaseEdit.applyPhase(profile(), phase(start = start, end = today.plusDays(30)), today)
        val stored = applied.goalPhases.single()
        // Shrink the end date: everything past it must lose the stamp.
        val shrunk = GoalPhaseEdit.applyPhase(applied, stored.copy(endDate = today.plusDays(10).toString()), today)
        val targets = assignmentTargets(shrunk.macroPlan!!, stored.profileId!!)
        assertEquals(today.plusDays(10).toString(), targets.last())
        assertFalse(today.plusDays(11).toString() in targets)
        assertEquals(
            start.toString(),
            targets.first(),
        )
    }

    @Test
    fun removePhaseAfterShrinkLeavesNoTrace() {
        val applied = GoalPhaseEdit.applyPhase(profile(), phase(start = today.plusDays(1), end = today.plusDays(30)), today)
        val stored = applied.goalPhases.single()
        val shrunk = GoalPhaseEdit.applyPhase(applied, stored.copy(endDate = today.plusDays(10).toString()), today)
        val removed = GoalPhaseEdit.removePhase(shrunk, stored.id, today)
        assertTrue(removed.goalPhases.isEmpty())
        assertTrue(assignmentTargets(removed.macroPlan!!, stored.profileId!!).isEmpty())
        assertNull(removed.macroPlan!!.profileById(stored.profileId))
    }

    @Test
    fun resavingOlderPhaseDoesNotReclaimNewerOwnedDays() {
        val cut = phase(name = "Cut", start = today.plusDays(1), end = today.plusDays(20))
            .copy(createdAtMillis = 1_000L)
        val first = GoalPhaseEdit.applyPhase(profile(), cut, today)
        val bulk = phase(name = "Bulk", intent = PhaseIntent.BULK, start = today.plusDays(10), end = today.plusDays(30))
            .copy(createdAtMillis = 2_000L)
        val both = GoalPhaseEdit.applyPhase(first, bulk, today)
        // Re-save the older cut (edited targets): newer bulk keeps the overlap.
        val cutRow = both.goalPhases.first { it.name == "Cut" }
        val newKcal = CalorieSafety.floorKcal(profile().bmr) + 50
        val reSaved = GoalPhaseEdit.applyPhase(both, cutRow.copy(calories = newKcal), today)
        val map = reSaved.macroPlan!!.dayAssignments
        val bulkRow = reSaved.goalPhases.first { it.name == "Bulk" }
        assertEquals(bulkRow.profileId, map[today.plusDays(15).toString()])
        val cutRowAfter = reSaved.goalPhases.first { it.name == "Cut" }
        assertEquals(cutRowAfter.profileId, map[today.plusDays(5).toString()])
        // Targets still updated on the shared profile.
        assertEquals(newKcal, reSaved.macroPlan!!.profileById(cutRowAfter.profileId!!)!!.calories)
    }

    @Test
    fun applyOnDisabledPlanRevivesKeepingSchedule() {
        val paused = MacroPlan(
            enabled = false,
            profiles = listOf(
                MacroDayProfile(id = "training", name = "Training", calories = 2800, proteinG = 1, carbsG = 1, fatG = 1),
                MacroDayProfile(id = "rest", name = "Rest", calories = 2200, proteinG = 1, carbsG = 1, fatG = 1),
            ),
            mode = MacroPlanMode.WEEKDAYS,
            defaultProfileId = "rest",
            weekdayProfileIds = mapOf("MONDAY" to "training"),
        )
        val next = GoalPhaseEdit.applyPhase(profile(paused), phase(), today)
        val plan = next.macroPlan!!
        assertTrue(plan.enabled)
        assertEquals(MacroPlanMode.WEEKDAYS, plan.mode)
        assertEquals("training", plan.weekdayProfileIds["MONDAY"])
        val ids = plan.profiles.map { it.id }.toSet()
        assertTrue("training" in ids && "rest" in ids)
        val base = plan.profiles.first { it.name == GoalPhaseEdit.BASE_PROFILE_NAME }
        assertEquals(base.id, plan.defaultProfileId)
        // Base mirrors the profile's effective targets, not the phase's.
        assertEquals(MacroPlanResolver.baseTargets(profile(paused)).calories, base.calories)
        // The phase row links to its profile and stamps its range.
        val stored = next.goalPhases.single()
        assertNotNull(stored.profileId)
        assertFalse(assignmentTargets(plan, stored.profileId!!).isEmpty())
    }

    @Test
    fun applyClampsPhaseCaloriesToSafetyFloor() {
        val p = profile()
        val floor = CalorieSafety.floorKcal(p.bmr)
        val next = GoalPhaseEdit.applyPhase(p, phase().copy(calories = 800), today)
        val stored = next.goalPhases.single()
        assertEquals(floor, stored.calories)
        assertEquals(floor, next.macroPlan!!.profileById(stored.profileId!!)!!.calories)
        // Stored phase and plan profile cannot disagree.
        assertEquals(stored.calories, next.macroPlan!!.profileById(stored.profileId!!)!!.calories)
    }

    private fun baseAndPhaseProfiles(phaseId: String, phaseCalories: Int = 1800): List<MacroDayProfile> = listOf(
        MacroDayProfile(id = "base", name = GoalPhaseEdit.BASE_PROFILE_NAME, calories = 2200, proteinG = 1, carbsG = 1, fatG = 1),
        MacroDayProfile(id = phaseId, name = "Phased", calories = phaseCalories, proteinG = 1, carbsG = 1, fatG = 1),
    )

    private fun stamped(range: ClosedRange<LocalDate>, id: String): Map<String, String> =
        generateSequence(range.start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(range.endInclusive) }
            .associate { it.toString() to id }

    @Test
    fun restampAssignmentsFillsTailOnlyAsWindowRolls() {
        val phaseId = "farPhase"
        val start = today.plusDays(300)
        val end = today.plusDays(665) // 366-day span, tail beyond today + keep.
        val far = phase(name = "Far", start = start, end = end).copy(profileId = phaseId, createdAtMillis = 1_000L)
        val plan = MacroPlan(
            enabled = true,
            profiles = baseAndPhaseProfiles(phaseId),
            mode = MacroPlanMode.MANUAL,
            defaultProfileId = "base",
            dayAssignments = stamped(start..today.plusDays(MacroPlan.ASSIGNMENT_KEEP_DAYS), phaseId),
        )
        val p = profile(plan).copy(goalPhases = listOf(far))
        // Today: the tail sits outside the keep window → unchanged.
        assertEquals(p, GoalPhaseEdit.restampAssignments(p, today))
        // Tomorrow: today + 367 enters the window → filled.
        val rolled = GoalPhaseEdit.restampAssignments(p, today.plusDays(1))
        assertNull(p.macroPlan!!.dayAssignments[today.plusDays(MacroPlan.ASSIGNMENT_KEEP_DAYS + 1).toString()])
        assertEquals(phaseId, rolled.macroPlan!!.dayAssignments[today.plusDays(MacroPlan.ASSIGNMENT_KEEP_DAYS + 1).toString()])
        assertEquals(phaseId, rolled.macroPlan!!.dayAssignments[start.toString()])
    }

    @Test
    fun restampAssignmentsFillsNewestFirstOnOverlap() {
        val cutId = "cutP"
        val bulkId = "bulkP"
        val cut = phase(name = "Cut", start = today, end = today.plusDays(20))
            .copy(profileId = cutId, createdAtMillis = 1_000L)
        val bulk = phase(name = "Bulk", intent = PhaseIntent.BULK, start = today.plusDays(10), end = today.plusDays(30))
            .copy(profileId = bulkId, createdAtMillis = 2_000L)
        val plan = MacroPlan(
            enabled = true,
            profiles = baseAndPhaseProfiles(cutId) +
                MacroDayProfile(id = bulkId, name = "Bulk", calories = 2900, proteinG = 1, carbsG = 1, fatG = 1),
            mode = MacroPlanMode.MANUAL,
            defaultProfileId = "base",
            dayAssignments = mapOf(
                today.plusDays(15).toString() to bulkId,
                today.plusDays(16).toString() to bulkId,
            ),
        )
        val map = GoalPhaseEdit.restampAssignments(
            profile(plan).copy(goalPhases = listOf(cut, bulk)),
            today,
        ).macroPlan!!.dayAssignments
        // Overlap keeps the newer bulk; cut fills only the leftovers.
        assertEquals(bulkId, map[today.plusDays(15).toString()])
        assertEquals(bulkId, map[today.plusDays(25).toString()])
        assertEquals(cutId, map[today.toString()])
        assertEquals(cutId, map[today.plusDays(9).toString()])
    }

    @Test
    fun restampAssignmentsPreservesManualAssignmentInsidePhase() {
        val phaseId = "cutP"
        val manualDay = today.plusDays(5)
        val plan = MacroPlan(
            enabled = true,
            profiles = baseAndPhaseProfiles(phaseId) +
                MacroDayProfile(id = "extra", name = "Extra", calories = 1900, proteinG = 1, carbsG = 1, fatG = 1),
            mode = MacroPlanMode.MANUAL,
            defaultProfileId = "base",
            dayAssignments = mapOf(manualDay.toString() to "extra"),
        )
        val manualPhase = phase(name = "Phased", start = today, end = today.plusDays(10))
            .copy(profileId = phaseId, createdAtMillis = 1_000L)
        val map = GoalPhaseEdit.restampAssignments(
            profile(plan).copy(goalPhases = listOf(manualPhase)),
            today,
        ).macroPlan!!.dayAssignments
        assertEquals("extra", map[manualDay.toString()])
        assertEquals(phaseId, map[today.plusDays(1).toString()])
        assertEquals(phaseId, map[today.plusDays(10).toString()])
    }

    @Test
    fun restampAssignmentsIsIdempotent() {
        val applied = GoalPhaseEdit.applyPhase(profile(), phase(start = today, end = today.plusDays(30)), today)
        val once = GoalPhaseEdit.restampAssignments(applied, today)
        val twice = GoalPhaseEdit.restampAssignments(once, today)
        assertEquals(once, twice)
        // And it actually did work the first time (window already covers it).
        assertEquals(
            assignmentTargets(applied.macroPlan!!, applied.goalPhases.single().profileId!!),
            assignmentTargets(once.macroPlan!!, applied.goalPhases.single().profileId!!),
        )
    }
}
