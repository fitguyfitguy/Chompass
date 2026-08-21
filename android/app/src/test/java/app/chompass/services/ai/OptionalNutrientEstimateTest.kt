package app.chompass.services.ai

import android.app.Application
import app.chompass.data.PreferencesStore
import app.chompass.models.OptionalNutrientGoals
import app.chompass.models.UserProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Settings → Other Nutrient Goals → "Estimate with AI": the one user-initiated
 * round-trip for optional nutrient goals. Verifies the prompt carries profile
 * context, the op tag is `optionalNutrients`, and the response parses into
 * [OptionalNutrientGoals] with untouched fields falling back to defaults.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class OptionalNutrientEstimateTest {
    private lateinit var prefs: PreferencesStore

    @Before
    fun setUp() {
        prefs = PreferencesStore(RuntimeEnvironment.getApplication())
    }

    @Test
    fun estimate_includesProfileContext_andParsesGoals() = runBlocking {
        var capturedPrompt: String? = null
        var capturedOp: String? = null
        val service = FoodAnalysisService(
            prefs = prefs,
            callAiDelegate = { prompt, _, op ->
                capturedPrompt = prompt
                capturedOp = op
                """{"sugar":40,"fiber":35,"sodium":2000,"vitamin_d":25,"omega_3":3}"""
            },
        )
        val profile = UserProfile(
            customCalories = 2100,
            customProtein = 150,
            customCarbs = 220,
            customFat = 70,
        )

        val goals = service.estimateOptionalNutrientGoals(profile)

        assertEquals("optionalNutrients", capturedOp)
        assertTrue("expected profile calories in prompt", capturedPrompt!!.contains("daily_calories: 2100"))
        assertTrue("expected diet mode in prompt", capturedPrompt!!.contains("diet_mode: standard"))
        assertEquals(40, goals.sugar)
        assertEquals(35, goals.fiber)
        assertEquals(2000, goals.sodium)
        assertEquals(25, goals.vitaminD)
        assertEquals(3, goals.omega3)
        // Fields the model did not return fall back to defaults, never zero.
        assertEquals(OptionalNutrientGoals.Default.addedSugar, goals.addedSugar)
        assertEquals(OptionalNutrientGoals.Default.calcium, goals.calcium)
    }

    @Test
    fun estimate_nullProfile_usesConservativeAdultDefaults() = runBlocking {
        var capturedPrompt: String? = null
        val service = FoodAnalysisService(
            prefs = prefs,
            callAiDelegate = { prompt, _, _ ->
                capturedPrompt = prompt
                """{"sugar":50,"fiber":30,"sodium":2300}"""
            },
        )

        val goals = service.estimateOptionalNutrientGoals(null)

        assertTrue("expected conservative-defaults line", capturedPrompt!!.contains("No user profile is available"))
        assertEquals(50, goals.sugar)
        assertEquals(30, goals.fiber)
        assertEquals(2300, goals.sodium)
        assertEquals(OptionalNutrientGoals.Default.iron, goals.iron)
    }
}
