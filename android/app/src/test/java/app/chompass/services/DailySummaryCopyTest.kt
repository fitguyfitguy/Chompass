package app.chompass.services

import android.app.Application
import app.chompass.models.MacroDayProfile
import app.chompass.models.MacroPlan
import app.chompass.models.MacroPlanMode
import app.chompass.models.UserProfile
import app.chompass.models.WidgetSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class DailySummaryCopyTest {
    private val context = RuntimeEnvironment.getApplication()
    private val fallbackTitle = "Today's summary is ready"
    private val fallbackText = "Tap to see how today's macros lined up."

    @Test
    fun skip_returnsNull() {
        assertNull(
            formatDailySummary(
                context,
                DailySummaryResult(DailySummaryVerdict.SKIP),
                fallbackTitle,
                fallbackText,
            ),
        )
    }

    @Test
    fun static_usesFallback() {
        val copy = formatDailySummary(
            context,
            DailySummaryResult(DailySummaryVerdict.STATIC),
            fallbackTitle,
            fallbackText,
        )!!
        assertEquals(fallbackTitle, copy.title)
        assertEquals(fallbackText, copy.text)
    }

    @Test
    fun deficit_titleAndBody() {
        val copy = formatDailySummary(
            context,
            DailySummaryResult(
                verdict = DailySummaryVerdict.DEFICIT,
                eaten = 1800,
                burned = 2300,
                delta = 500,
                proteinG = 140,
                carbsG = 180,
                fatG = 60,
            ),
            fallbackTitle,
            fallbackText,
        )!!
        assertTrue(copy.title.contains("500"))
        assertTrue(copy.title.contains("deficit") || copy.title.contains("kcal"))
        assertTrue(copy.text.contains("1,800") || copy.text.contains("1800"))
        assertTrue(copy.bigText.contains("P 140"))
        assertTrue(!copy.bigText.contains("goal"))
    }

    @Test
    fun expanded_includesGoalLine() {
        val copy = formatDailySummary(
            context,
            DailySummaryResult(
                verdict = DailySummaryVerdict.DEFICIT,
                eaten = 1500,
                burned = 2467,
                delta = 967,
                proteinG = 60,
                carbsG = 160,
                fatG = 55,
            ),
            fallbackTitle,
            fallbackText,
            goalKcal = 1916,
        )!!
        assertTrue(copy.text.contains("1,500") || copy.text.contains("1500"))
        assertTrue(!copy.text.contains("1916") && !copy.text.contains("1,916"))
        assertTrue(copy.bigText.contains("1,916") || copy.bigText.contains("1916"))
    }

    @Test
    fun keto_usesNetCarbLabel() {
        val copy = formatDailySummary(
            context,
            DailySummaryResult(
                verdict = DailySummaryVerdict.ON_TARGET,
                eaten = 1800,
                burned = 1850,
                proteinG = 140,
                carbsG = 22,
                fatG = 120,
            ),
            fallbackTitle,
            fallbackText,
            carbsAreNet = true,
        )!!
        assertTrue(copy.bigText.contains("22"))
        assertTrue(copy.bigText.contains("net"))
    }

    @Test
    fun surplus_usesAbsoluteDelta() {
        val copy = formatDailySummary(
            context,
            DailySummaryResult(
                verdict = DailySummaryVerdict.SURPLUS,
                eaten = 2300,
                burned = 1800,
                delta = -500,
            ),
            fallbackTitle,
            fallbackText,
        )!!
        assertTrue(copy.title.contains("500"))
        assertTrue(!copy.title.contains("-"))
    }

    @Test
    fun onTarget_noDeltaInTitle() {
        val copy = formatDailySummary(
            context,
            DailySummaryResult(
                verdict = DailySummaryVerdict.ON_TARGET,
                eaten = 1950,
                burned = 2000,
                delta = 50,
            ),
            fallbackTitle,
            fallbackText,
        )!!
        assertTrue(copy.title.isNotBlank())
        assertTrue(!copy.title.contains("50"))
    }

    // -- #60: goal kcal resolution (widget snapshot first, day-type plan next) --

    private fun snapshot(displayGoalTarget: Int? = null, calorieGoal: Int = 2400) = WidgetSnapshot(
        date = Instant.EPOCH,
        dayStart = Instant.EPOCH,
        calories = 1000,
        calorieGoal = calorieGoal,
        protein = 10.0,
        proteinGoal = 150,
        carbs = 10.0,
        carbsGoal = 250,
        fat = 10.0,
        fatGoal = 70,
        displayGoalTarget = displayGoalTarget,
    )

    @Test
    fun goalKcal_prefersFreshWidgetTarget() {
        assertEquals(3000, summaryGoalKcal(snapshot(displayGoalTarget = 3000), null, LocalDate.now()))
    }

    @Test
    fun goalKcal_resolvesTheDayThroughTheMacroPlan() {
        val plan = MacroPlan(
            enabled = true,
            profiles = listOf(MacroDayProfile("t", "Training day", 2800, 170, 350, 78)),
            mode = MacroPlanMode.MANUAL,
            defaultProfileId = "t",
        )
        val profile = UserProfile(customCalories = 2400, customProtein = 150, customCarbs = 250, customFat = 70, macroPlan = plan)
        assertEquals(2800, summaryGoalKcal(widgetForDay = null, profile = profile, day = LocalDate.now()))
    }

    @Test
    fun goalKcal_fallsBackToBaseTargetThenZero() {
        val base = UserProfile(customCalories = 2400, customProtein = 150, customCarbs = 250, customFat = 70)
        assertEquals(2400, summaryGoalKcal(null, base, LocalDate.now()))
        assertEquals(0, summaryGoalKcal(null, null, LocalDate.now()))
    }
}
