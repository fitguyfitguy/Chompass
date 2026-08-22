package app.chompass.data

import android.app.Application
import app.chompass.models.AIProvider
import app.chompass.models.UserProfile
import app.chompass.services.ai.GoalCalculation
import app.chompass.services.ai.GoalCalculationReport
import app.chompass.services.ai.GoalRecalcTier
import app.chompass.services.ai.RecalcSheetData
import app.chompass.services.ai.RecalcSheetSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** DataStore JSON round-trip for the on-demand goal-change transparency sheet. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class RecalcSheetPersistenceTest {
    private fun prefs() = PreferencesStore(RuntimeEnvironment.getApplication())

    @Before
    fun setUp() = runBlocking {
        // DataStore is a process-wide singleton: start every test clean.
        prefs().setLastGoalChangeSheetJsonImpl(null)
    }

    @Test
    fun aiSheet_roundTripsThroughDataStore() = runBlocking {
        val store = prefs()
        val sheet = RecalcSheetData(
            result = GoalCalculation(
                calories = 1980, protein = 136, carbs = 257, fat = 45,
                reason = "test reason", tier = GoalRecalcTier.SAFE,
                provider = AIProvider.ON_DEVICE, model = "gemma-4-E2B-it",
                fallbackFired = true, primaryProvider = AIProvider.GEMINI,
                primaryError = "boom",
                report = GoalCalculationReport(
                    bmr = 1727, tdee = 2530, activityMultiplier = 1.465,
                    calorieAdjustment = -550, formulaCalories = 1980,
                    formulaProtein = 136, formulaCarbs = 257, formulaFat = 45,
                    measuredTdee = 2500, weighIns = 12, weightSpanDays = 40,
                    foodDays = 40, loggedDayAvgCalories = 2290,
                    impliedMaintenance = 2540, impliedWithheld = null,
                    trendsDisagree = false,
                ),
            ),
            before = UserProfile(heightCm = 178.0, weightKg = 76.0),
            after = UserProfile(heightCm = 178.0, weightKg = 76.0, customCalories = 1980),
            savedAtMillis = 1_234_567_890L,
        )
        store.saveLastGoalChangeSheet(sheet)
        assertEquals(sheet, store.loadLastGoalChangeSheet())
    }

    @Test
    fun adaptiveSheet_roundTrips_withTierAndProviderNull() = runBlocking {
        val store = prefs()
        val sheet = RecalcSheetData(
            result = GoalCalculation(
                calories = 1728, protein = 136, carbs = 257, fat = 45,
                reason = "Adaptive Goals raised calories to the safety floor.",
                report = GoalCalculationReport(
                    bmr = 1727, tdee = 2530, activityMultiplier = 1.2,
                    calorieAdjustment = 0, formulaCalories = 1980,
                    formulaProtein = 136, formulaCarbs = 257, formulaFat = 45,
                ),
            ),
            before = UserProfile(heightCm = 178.0, weightKg = 76.0),
            after = UserProfile(heightCm = 178.0, weightKg = 76.0, customCalories = 1728),
            savedAtMillis = 42L,
            source = RecalcSheetSource.ADAPTIVE,
        )
        store.saveLastGoalChangeSheet(sheet)
        assertEquals(sheet, store.loadLastGoalChangeSheet())
    }

    @Test
    fun load_returnsNull_whenNothingStored() = runBlocking {
        assertNull(prefs().loadLastGoalChangeSheet())
    }
}
