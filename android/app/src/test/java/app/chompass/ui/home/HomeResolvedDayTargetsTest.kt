package app.chompass.ui.home

import app.chompass.models.GoalJournalEntry
import app.chompass.models.GoalJournalSource
import app.chompass.models.HomeCalorieDisplayMode
import app.chompass.models.HomeDisplayPreferences
import app.chompass.models.MacroDayProfile
import app.chompass.models.MacroPlan
import app.chompass.models.MacroPlanMode
import app.chompass.models.UserProfile
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Home resolution seams for macro day types (Codeberg #60 phase 1):
 * [HomeUiState.resolvedDayTargets] (journal-first for past days, live resolve
 * for today), [HomeUiState.baseCalorieGoal], the [HomeUiState.macroGoalScale]
 * denominator, and the hero chip label gate.
 */
class HomeResolvedDayTargetsTest {
    private val today: LocalDate = LocalDate.now()
    private val training = MacroDayProfile("t", "Training day", 2800, 170, 350, 78)
    private val rest = MacroDayProfile("r", "Rest day", 2100, 150, 160, 78)

    private fun profile(plan: MacroPlan?) = UserProfile(
        customCalories = 2400,
        customProtein = 150,
        customCarbs = 250,
        customFat = 70,
        macroPlan = plan,
    )

    private fun plan(mode: MacroPlanMode = MacroPlanMode.MANUAL, default: String? = "t") = MacroPlan(
        enabled = true,
        profiles = listOf(training, rest),
        mode = mode,
        defaultProfileId = default,
    )

    private fun journalEntry(date: LocalDate, calories: Int, profileId: String?) = GoalJournalEntry(
        date = date.toString(),
        calories = calories,
        proteinG = 150,
        carbsG = 250,
        fatG = 70,
        profileId = profileId,
        profileName = profileId?.let { if (it == "t") "Training day" else "Rest day" },
        updatedAtMillis = 1_000L,
        source = GoalJournalSource.PLAN,
    )

    @Test
    fun `plan off keeps the base targets`() {
        val ui = HomeUiState(profile = profile(plan = null), date = today)
        assertEquals(2400, ui.baseCalorieGoal)
        assertEquals(150, ui.resolvedDayTargets.targets.proteinG)
        assertNull(ui.resolvedDayTargets.profileId)
        assertNull(ui.dayTypeLabel)
    }

    @Test
    fun `today resolves through the plan and shows the chip label`() {
        val ui = HomeUiState(profile = profile(plan()), date = today)
        assertEquals(2800, ui.baseCalorieGoal)
        assertEquals("t", ui.resolvedDayTargets.profileId)
        assertEquals("Training day", ui.dayTypeLabel)
    }

    @Test
    fun `past day reads the frozen journal entry first`() {
        val yesterday = today.minusDays(1)
        val ui = HomeUiState(
            profile = profile(plan()),
            date = yesterday,
            goalJournal = listOf(journalEntry(yesterday, 2100, "r")),
        )
        assertEquals(2100, ui.baseCalorieGoal)
        assertEquals("r", ui.resolvedDayTargets.profileId)
        // Browsing a past day hides the quick-switch chip (it only edits today).
        assertNull(ui.dayTypeLabel)
    }

    @Test
    fun `past day without journal entry falls back to live resolution`() {
        val yesterday = today.minusDays(1)
        val ui = HomeUiState(profile = profile(plan()), date = yesterday)
        assertEquals(2800, ui.baseCalorieGoal)
    }

    @Test
    fun `macro goal scale denominator is the resolved day target`() {
        // ADD_ACTIVE with a manual active entry: hero = day target + 300.
        // Old behavior divided by the base 2400; the day plan makes today 2800,
        // so the scale must read (2800+300)/2800 — never disagreeing with the ring.
        val ui = HomeUiState(
            profile = profile(plan()),
            date = today,
            homeDisplay = HomeDisplayPreferences(calorieDisplayMode = HomeCalorieDisplayMode.ADD_ACTIVE),
            manualActiveKcal = 300,
        )
        assertEquals(2800 + 300, ui.heroCalorieGoal)
        assertEquals((2800f + 300f) / 2800f, ui.macroGoalScale, 0.001f)
    }
}
