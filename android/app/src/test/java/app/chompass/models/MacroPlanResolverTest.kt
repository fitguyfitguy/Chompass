package app.chompass.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Unit coverage for the macro day-plan resolver + goal journal
 * (Codeberg #60, docs/local/MACRO_PROFILES_DESIGN.md — MACRO-CYCLE-A/B/D).
 * Fixture-driven parity cases live in app.chompass.parity.MacroPlanParityTest;
 * this file covers rule-level behavior and store-side pure helpers.
 */
class MacroPlanResolverTest {
    private val base = DayTargets(calories = 2400, proteinG = 150, carbsG = 250, fatG = 70)
    private val training = MacroDayProfile("t", "Training day", 2800, 170, 350, 78)
    private val rest = MacroDayProfile("r", "Rest day", 2100, 150, 160, 78)

    private fun plan(
        mode: MacroPlanMode = MacroPlanMode.CYCLE,
        default: String? = "r",
        pattern: List<String> = listOf("t", "t", "r"),
        anchor: String? = "2026-09-01",
        assignments: Map<String, String> = emptyMap(),
        weekdays: Map<String, String> = emptyMap(),
        enabled: Boolean = true,
    ) = MacroPlan(
        enabled = enabled,
        profiles = listOf(training, rest),
        mode = mode,
        defaultProfileId = default,
        weekdayProfileIds = weekdays,
        cyclePattern = pattern,
        cycleAnchorDay = anchor,
        dayAssignments = assignments,
    )

    // -- MACRO-CYCLE-A ---------------------------------------------------------

    @Test
    fun `cycle resolves far future and far past days via epoch-day floorMod`() {
        val p = plan()
        // Anchor 2026-09-01, pattern [t, t, r]. Verified offsets: -2d -> idx 1,
        // +182d (2027-03-02) -> idx 2, -2435d (2020-01-01) -> idx 1.
        assertEquals("t", MacroPlanResolver.resolve(p, base, LocalDate.parse("2026-08-30")).profileId)
        assertEquals("r", MacroPlanResolver.resolve(p, base, LocalDate.parse("2027-03-02")).profileId)
        assertEquals("t", MacroPlanResolver.resolve(p, base, LocalDate.parse("2020-01-01")).profileId)
    }

    @Test
    fun `cycle without anchor or pattern falls back to default`() {
        assertEquals("r", MacroPlanResolver.resolve(plan(anchor = null), base, LocalDate.parse("2026-09-01")).profileId)
        assertEquals("r", MacroPlanResolver.resolve(plan(pattern = emptyList()), base, LocalDate.parse("2026-09-01")).profileId)
    }

    @Test
    fun `empty cycle pattern entry falls back to default then base`() {
        val dangling = plan(pattern = listOf("t", "gone"))
        assertEquals("t", MacroPlanResolver.resolve(dangling, base, LocalDate.parse("2026-09-01")).profileId)
        val noDefault = plan(pattern = listOf("t", "gone"), default = null)
        val resolved = MacroPlanResolver.resolve(noDefault, base, LocalDate.parse("2026-09-02"))
        assertNull(resolved.profileId)
        assertEquals(base, resolved.targets)
    }

    // -- MACRO-CYCLE-B -----------------------------------------------------------

    @Test
    fun `forward average window length follows cycle pattern or seven days`() {
        val cycle = plan() // 3-day window
        val avg = MacroPlanResolver.averageForward(cycle, base, LocalDate.parse("2026-09-01"))
        // t, t, r -> (2800+2800+2100)/3, (170+170+150)/3, (350+350+160)/3, 78
        assertEquals(DayTargets(2567, 163, 287, 78), avg)

        val manual = plan(mode = MacroPlanMode.MANUAL, default = "t")
        val avg7 = MacroPlanResolver.averageForward(manual, base, LocalDate.parse("2026-09-01"))
        assertEquals(DayTargets(2800, 170, 350, 78), avg7)
    }

    @Test
    fun `forward average ignores overrides outside the window`() {
        val p = plan(assignments = mapOf("2026-09-20" to "r"))
        val avg = MacroPlanResolver.averageForward(p, base, LocalDate.parse("2026-09-01"))
        assertEquals(DayTargets(2567, 163, 287, 78), avg)
    }

    // -- MACRO-CYCLE-D -----------------------------------------------------------

    @Test
    fun `journal average skips gaps instead of filling them`() {
        val entries = listOf(
            entry("2026-08-28", 2800),
            entry("2026-08-31", 2100),
        )
        val avg = MacroPlanResolver.journalAverage(entries, LocalDate.parse("2026-08-25"), LocalDate.parse("2026-09-02"))
        assertEquals(DayTargets(2450, 160, 255, 78), avg)
        assertNull(
            MacroPlanResolver.journalAverage(entries, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-07")),
        )
    }

    // -- Goal journal helpers ---------------------------------------------------

    private fun entry(date: String, calories: Int, updatedAt: Long = 0) = GoalJournalEntry(
        date = date, calories = calories, proteinG = 160, carbsG = 255, fatG = 78, updatedAtMillis = updatedAt,
    )

    @Test
    fun `freeze rule - past entries immutable, today live`() {
        val today = LocalDate.parse("2026-09-01")
        val journal = listOf(entry("2026-08-31", 2100))
        // Past rewrite attempt is dropped.
        assertEquals(
            journal,
            GoalJournal.upsertRespectingFreeze(journal, entry("2026-08-31", 9999), today),
        )
        // Today is replaced.
        val withToday = GoalJournal.upsertRespectingFreeze(journal, entry("2026-09-01", 2800), today)
        assertEquals(2, withToday.size)
        assertEquals(2800, withToday.last().calories)
        // A NEW past date (no existing entry) is insertable — gap fill relies on it.
        val filled = GoalJournal.upsertRespectingFreeze(journal, entry("2026-08-30", 2400), today)
        assertEquals(2, filled.size)
    }

    @Test
    fun `merge is per-day last-write-wins and argument-order independent`() {
        val local = listOf(entry("2026-08-30", 2400, updatedAt = 500), entry("2026-08-31", 2100, updatedAt = 900))
        val remote = listOf(entry("2026-08-30", 2500, updatedAt = 700), entry("2026-08-31", 2800, updatedAt = 300))
        val a = GoalJournal.merge(local, remote)
        val b = GoalJournal.merge(remote, local)
        assertEquals(a, b)
        assertEquals(2500, a.first { it.date == "2026-08-30" }.calories)
        assertEquals(2100, a.first { it.date == "2026-08-31" }.calories)
    }

    @Test
    fun `prune keeps 400 days back and drops malformed dates`() {
        val today = LocalDate.parse("2026-09-01")
        val old = today.minusDays(GoalJournal.KEEP_DAYS).toString()
        val kept = today.minusDays(GoalJournal.KEEP_DAYS - 1).toString()
        val pruned = GoalJournal.prune(
            listOf(entry(old, 1), entry(kept, 2), entry(today.toString(), 3), entry("not-a-date", 4)),
            today,
        )
        assertEquals(listOf(kept, today.toString()), pruned.map { it.date })
    }

    @Test
    fun `gap fill bridges holes between first entry and yesterday, not before first`() {
        val today = LocalDate.parse("2026-09-05")
        val journal = listOf(entry("2026-09-01", 2800))
        val resolver = fun(d: LocalDate): ResolvedDayTargets {
            // Alternate to prove which days got filled: pattern-free — return base with calories = epoch day parity marker.
            val cal = if (d.toEpochDay() % 2 == 0L) 2000 else 2200
            return ResolvedDayTargets(DayTargets(cal, 150, 250, 70), null, null)
        }
        val filled = GoalJournal.gapFill(journal, today, nowMillis = 42, resolver)
        // 09-01 exists; 09-02..09-04 are gap-filled; nothing before 09-01.
        assertEquals(listOf("2026-09-01", "2026-09-02", "2026-09-03", "2026-09-04"), filled.map { it.date })
        assertEquals(GoalJournalSource.GAP_FILL, filled.last().source)
        assertEquals(42, filled.last().updatedAtMillis)
        // Empty journal: nothing to fill from.
        assertEquals(emptyList<GoalJournalEntry>(), GoalJournal.gapFill(emptyList(), today, 0, resolver))
    }

    // -- Plan fingerprints -------------------------------------------------------

    @Test
    fun `plan signature feeds goal staleness nudge`() {
        val plain = UserProfile()
        val withPlan = plain.copy(macroPlan = plan())
        assertNotEquals(plain.goalInputSignature, withPlan.goalInputSignature)
        // Map ordering must not matter.
        val a = plain.copy(
            macroPlan = plan(weekdays = mapOf("MONDAY" to "t", "TUESDAY" to "r")),
        )
        val b = plain.copy(
            macroPlan = plan(weekdays = mapOf("TUESDAY" to "r", "MONDAY" to "t")),
        )
        assertEquals(a.goalInputSignature, b.goalInputSignature)
    }

    @Test
    fun `assignment pruning keeps only the bounded window`() {
        val today = LocalDate.parse("2026-09-01")
        val p = MacroPlan(
            dayAssignments = mapOf(
                "2024-01-01" to "t",
                today.minusDays(366).toString() to "r",
                today.toString() to "t",
                today.plusDays(366).toString() to "r",
                "2099-01-01" to "t",
            ),
        )
        val pruned = p.prunedAssignments(today)
        assertEquals(setOf(today.toString(), today.minusDays(366).toString(), today.plusDays(366).toString()), pruned.keys)
        assertTrue(pruned.values.all { it in setOf("t", "r") })
    }

    // -- Journal-first reads (phase 3: Progress + DiaryExporter) -------------

    @Test
    fun `targetsForJournaled prefers frozen entries for past and today, live for gaps and future`() {
        val today = LocalDate.parse("2026-09-10")
        val profile = UserProfile(
            customCalories = 2400,
            customProtein = 150,
            customCarbs = 250,
            customFat = 70,
            macroPlan = plan(),
        )
        val journal = listOf(
            GoalJournalEntry(
                date = "2026-09-08", calories = 2100, proteinG = 150, carbsG = 160, fatG = 78,
                profileId = "r", profileName = "Rest day", updatedAtMillis = 1L,
            ),
        )
        // Past journaled day: frozen entry wins over the cycle (which resolves t).
        val past = MacroPlanResolver.targetsForJournaled(journal, profile, LocalDate.parse("2026-09-08"), today)
        assertEquals(2100, past.targets.calories)
        assertEquals("r", past.profileId)
        // Past gap: live resolve (offset 6 -> pattern[0] = t).
        val gap = MacroPlanResolver.targetsForJournaled(journal, profile, LocalDate.parse("2026-09-07"), today)
        assertEquals(2800, gap.targets.calories)
        // Future: never journaled, live resolve.
        val future = MacroPlanResolver.targetsForJournaled(journal, profile, LocalDate.parse("2026-09-15"), today)
        assertEquals("r", future.profileId)
    }

    @Test
    fun `targetsForJournaled keeps history when the plan is disabled and zeros without a profile`() {
        val today = LocalDate.parse("2026-09-10")
        val entry = GoalJournalEntry(
            date = "2026-09-09", calories = 2800, proteinG = 170, carbsG = 350, fatG = 78,
            profileId = "t", profileName = "Training day", updatedAtMillis = 1L,
        )
        val disabled = UserProfile(
            customCalories = 2400, customProtein = 150, customCarbs = 250, customFat = 70,
            macroPlan = plan(enabled = false),
        )
        // A journaled day from a since-disabled plan stays frozen (design Q2).
        assertEquals(2800, MacroPlanResolver.targetsForJournaled(listOf(entry), disabled, LocalDate.parse("2026-09-09"), today).targets.calories)
        // No profile, no entry -> the DiaryExporter zero-targets shape.
        val zero = MacroPlanResolver.targetsForJournaled(emptyList(), null, LocalDate.parse("2026-09-09"), today)
        assertEquals(DayTargets(0, 0, 0, 0), zero.targets)
        assertNull(zero.profileId)
    }

    @Test
    fun `journal sync record id mirrors the daily_notes per-day scheme`() {
        // 2026-09-01 = epoch day 20697 -> low 48 bits as 12 hex digits.
        assertEquals("00000000-0000-0000-0000-0000000050d9", GoalJournal.idFor(LocalDate.parse("2026-09-01")).toString())
        assertEquals("00000000-0000-0000-0000-000000000000", GoalJournal.idFor(LocalDate.parse("1970-01-01")).toString())
        // Same date -> same id (merge-by-id == merge-by-date).
        assertEquals(GoalJournal.idFor(LocalDate.parse("2026-09-01")), GoalJournal.idFor(LocalDate.parse("2026-09-01")))
    }
}
