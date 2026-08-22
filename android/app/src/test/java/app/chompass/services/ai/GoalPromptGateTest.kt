package app.chompass.services.ai

import android.app.Application
import app.chompass.data.PreferencesStore
import app.chompass.models.AIProvider
import app.chompass.models.FoodEntry
import app.chompass.models.UserProfile
import app.chompass.models.WeightEntry
import app.chompass.services.WeightAnalysisService
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Hit-and-trial confidence gating in `calculateGoals`: the OBSERVED DATA
 * prompt section must withhold the "Implied actual maintenance" number (and
 * the "prefer empirical" instruction) while the log density is below the
 * empirical minimums — sparse weigh-ins are water-weight noise and anchored
 * the on-device model at ~BMR (e.g. 1742 kcal vs formula TDEE ~2680).
 * See docs/CALCULATION_METHODS.md and docs/ON_DEVICE_LLM.md.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class GoalPromptGateTest {
    private lateinit var prefs: PreferencesStore

    @Before
    fun setUp() {
        prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        runBlocking {
            prefs.setAiFeaturesEnabled(true)
            prefs.setSelectedAIProvider(AIProvider.GEMINI)
            prefs.setFallbackEnabled(false)
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            prefs.setAiFeaturesEnabled(true)
            prefs.setFallbackEnabled(true)
        }
    }

    private fun profile() = UserProfile(
        heightCm = 178.0,
        weightKg = 76.0,
    )

    /** Delegate that captures the built prompt and returns a formula-like reply. */
    private fun capturingService(onPrompt: (String) -> Unit): FoodAnalysisService =
        FoodAnalysisService(
            prefs = prefs,
            callAiDelegate = { prompt, _, _ ->
                onPrompt(prompt)
                """{"calories":1980,"protein":136,"carbs":257,"fat":45,"reason":"test"}"""
            },
        )

    private fun forecast(
        weighIns: Int,
        spanDays: Int,
        foodDays: Int,
        loggedDayAvg: Int = 2290,
        observedWeekly: Double = +0.5,
    ): app.chompass.services.WeightForecast {
        val zone = java.time.ZoneId.systemDefault()
        val now = Instant.now()
        val today = now.atZone(zone).toLocalDate()
        val n = weighIns.coerceAtLeast(2)
        val totalChange = observedWeekly * spanDays / 7.0
        val weights = (0 until n).map { i ->
            // i = 0 oldest, i = n-1 newest; linear trend of [observedWeekly] kg/week over [spanDays].
            val frac = i.toDouble() / (n - 1)
            WeightEntry(
                date = now.minus((spanDays.toLong() * (n - 1 - i) / (n - 1)).coerceAtLeast(0L), ChronoUnit.DAYS),
                weightKg = 76.0 + totalChange * frac,
            )
        }
        val foods = (0 until foodDays).map { i ->
            FoodEntry(
                name = "test",
                calories = loggedDayAvg,
                protein = 100.0,
                carbs = 200.0,
                fat = 50.0,
                source = app.chompass.models.FoodSource.MANUAL,
                timestamp = today.minusDays(i.toLong() + 1).atStartOfDay(zone).toInstant(),
            )
        }
        return WeightAnalysisService.compute(weights, foods, profile(), now = now, zone = zone)
    }

    @Test
    fun thinData_withholdsImpliedMaintenance() = runBlocking {
        var prompt = ""
        val service = capturingService { prompt = it }
        val f = forecast(weighIns = 2, spanDays = 4, foodDays = 2)
        service.calculateGoals(profile(), f, heightMetric = true, weightMetric = true)

        assertTrue("observed section missing", prompt.contains("OBSERVED DATA"))
        assertFalse("thin data must not surface implied maintenance", prompt.contains("Implied actual maintenance"))
        assertTrue("thin data must say so", prompt.contains("too thin"))
        assertTrue("thin data must keep formula anchor", prompt.contains("Use the formula TDEE as the maintenance anchor"))
    }

    @Test
    fun richData_surfacesImpliedMaintenance() = runBlocking {
        var prompt = ""
        val service = capturingService { prompt = it }
        val f = forecast(weighIns = 12, spanDays = 40, foodDays = 40)
        service.calculateGoals(profile(), f, heightMetric = true, weightMetric = true)

        assertTrue("rich data must surface implied maintenance", prompt.contains("Implied actual maintenance"))
        assertFalse("rich data is not thin", prompt.contains("too thin"))
        assertFalse("rich data must not claim medium confidence", prompt.contains("MEDIUM CONFIDENCE"))
    }

    @Test
    fun mediumData_surfacesImpliedWithConfidenceQualifier() = runBlocking {
        var prompt = ""
        val service = capturingService { prompt = it }
        val f = forecast(weighIns = 6, spanDays = 20, foodDays = 8, loggedDayAvg = 2900, observedWeekly = +0.3)
        service.calculateGoals(profile(), f, heightMetric = true, weightMetric = true)

        assertTrue("medium data must surface implied maintenance", prompt.contains("Implied actual maintenance"))
        assertTrue("medium data must carry the 15% qualifier", prompt.contains("MEDIUM CONFIDENCE"))
    }
}
