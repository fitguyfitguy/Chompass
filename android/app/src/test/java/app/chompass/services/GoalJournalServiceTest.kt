package app.chompass.services

import app.chompass.models.GoalJournalEntry
import app.chompass.models.GoalJournalSource
import app.chompass.models.MacroDayProfile
import app.chompass.models.MacroPlan
import app.chompass.models.MacroPlanMode
import app.chompass.models.UserProfile
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit coverage for the goal-journal write-trigger core (Codeberg #60,
 * docs/local/MACRO_PROFILES_DESIGN.md § Per-day goal journal): the pure
 * decision behind [GoalJournalService.refresh] — gap-fill + record today,
 * no-op when nothing changes, nothing while the plan is off.
 */
class GoalJournalServiceTest {
    private val today = LocalDate.parse("2026-09-10")
    private val training = MacroDayProfile("t", "Training day", 2800, 170, 350, 78)
    private val rest = MacroDayProfile("r", "Rest day", 2100, 150, 160, 78)

    private fun profile(plan: MacroPlan? = plan()) = UserProfile(
        // Pinned base targets: effective* = 2400 / 150P / 250C / 70F.
        customCalories = 2400,
        customProtein = 150,
        customCarbs = 250,
        customFat = 70,
        macroPlan = plan,
    )

    private fun plan(
        mode: MacroPlanMode = MacroPlanMode.MANUAL,
        default: String? = "t",
        enabled: Boolean = true,
        assignments: Map<String, String> = emptyMap(),
    ) = MacroPlan(
        enabled = enabled,
        profiles = listOf(training, rest),
        mode = mode,
        defaultProfileId = default,
        dayAssignments = assignments,
    )

    private fun update(
        profile: UserProfile?,
        entries: List<GoalJournalEntry>,
        source: GoalJournalSource = GoalJournalSource.PLAN,
        day: LocalDate = today,
        nowMillis: Long = 1_000L,
    ) = GoalJournalService.updatedJournal(
        profile = profile,
        current = entries,
        today = day,
        nowMillis = nowMillis,
        source = source,
    )

    @Test
    fun `no plan or disabled plan never journals`() {
        assertNull(update(profile = null, entries = emptyList()))
        assertNull(update(profile = profile(plan(enabled = false)), entries = emptyList()))
    }

    @Test
    fun `first enable writes only todays entry`() {
        val result = update(profile(), emptyList())!!
        assertEquals(listOf(today.toString()), result.map { it.date })
        val entry = result.single()
        assertEquals(2800, entry.calories)
        assertEquals("t", entry.profileId)
        assertEquals("Training day", entry.profileName)
        assertEquals(GoalJournalSource.PLAN, entry.source)
        assertEquals(1_000L, entry.updatedAtMillis)
    }

    @Test
    fun `gap-fill backfills missing days between first entry and yesterday`() {
        val seed = listOf(
            GoalJournalEntry(
                date = today.minusDays(3).toString(),
                calories = 2800, proteinG = 170, carbsG = 350, fatG = 78,
                profileId = "t", profileName = "Training day",
                updatedAtMillis = 500L, source = GoalJournalSource.PLAN,
            ),
        )
        val result = update(profile(), seed, nowMillis = 2_000L)!!
        // −3 (kept), −2 / −1 gap-filled, today recorded.
        assertEquals(
            (3 downTo 0).map { today.minusDays(it.toLong()).toString() },
            result.map { it.date },
        )
        assertEquals(GoalJournalSource.GAP_FILL, result[1].source)
        assertEquals(GoalJournalSource.GAP_FILL, result[2].source)
        assertEquals(GoalJournalSource.PLAN, result[3].source)
        // MANUAL default resolves every missing day to the default profile.
        assertEquals("t", result[1].profileId)
    }

    @Test
    fun `unrelated profile write is a no-op when todays values match`() {
        val existing = GoalJournalEntry(
            date = today.toString(),
            calories = 2800, proteinG = 170, carbsG = 350, fatG = 78,
            profileId = "t", profileName = "Training day",
            updatedAtMillis = 500L, source = GoalJournalSource.MANUAL_SWITCH,
        )
        // Gap-filled plan result already matches today's entry values.
        assertNull(update(profile(), listOf(existing), nowMillis = 2_000L))
    }

    @Test
    fun `manual switch provenance survives the observer follow-up`() {
        // Quick switch today to Rest: values change → rewritten as MANUAL_SWITCH.
        val before = listOf(
            GoalJournalEntry(
                date = today.toString(),
                calories = 2800, proteinG = 170, carbsG = 350, fatG = 78,
                profileId = "t", profileName = "Training day",
                updatedAtMillis = 500L, source = GoalJournalSource.PLAN,
            ),
        )
        val switched = update(
            profile(plan(assignments = mapOf(today.toString() to "r"))),
            before,
            source = GoalJournalSource.MANUAL_SWITCH,
            nowMillis = 2_000L,
        )!!
        val entry = switched.single { it.date == today.toString() }
        assertEquals("r", entry.profileId)
        assertEquals(GoalJournalSource.MANUAL_SWITCH, entry.source)

        // The PLAN observer refresh then sees identical values → no rewrite.
        assertNull(update(profile(plan(assignments = mapOf(today.toString() to "r"))), switched, nowMillis = 3_000L))
    }

    @Test
    fun `schedule edit rewrites today but never past days`() {
        val past = today.minusDays(2).toString()
        val existing = listOf(
            GoalJournalEntry(
                date = past,
                calories = 2100, proteinG = 150, carbsG = 160, fatG = 78,
                profileId = "r", profileName = "Rest day",
                updatedAtMillis = 500L, source = GoalJournalSource.PLAN,
            ),
            GoalJournalEntry(
                date = today.toString(),
                calories = 2800, proteinG = 170, carbsG = 350, fatG = 78,
                profileId = "t", profileName = "Training day",
                updatedAtMillis = 500L, source = GoalJournalSource.PLAN,
            ),
        )
        // Default flipped to Rest: today must follow, the past entry stays frozen.
        val result = update(profile(plan(default = "r")), existing, nowMillis = 2_000L)!!
        val pastEntry = result.single { it.date == past }
        assertEquals(2100, pastEntry.calories)
        assertEquals(500L, pastEntry.updatedAtMillis)
        val todayEntry = result.single { it.date == today.toString() }
        assertEquals("r", todayEntry.profileId)
        assertEquals(2_000L, todayEntry.updatedAtMillis)
    }

    @Test
    fun `base-fallback resolution journals the base targets with null profile`() {
        // Plan enabled but nothing resolvable (no default, MANUAL, no override).
        val result = update(profile(plan(default = null)), emptyList())!!
        val entry = result.single()
        assertNull(entry.profileId)
        assertNull(entry.profileName)
        assertEquals(2400, entry.calories)
    }
}
