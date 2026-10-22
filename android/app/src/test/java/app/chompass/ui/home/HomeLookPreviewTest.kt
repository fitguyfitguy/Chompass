package app.chompass.ui.home

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.chompass.models.FoodLogMacroChip
import app.chompass.models.HomeCalorieDisplayMode
import app.chompass.models.HomeCalorieHeroLayout
import app.chompass.models.HomeDisplayPreferences
import app.chompass.models.HomeMacroCardLayout
import app.chompass.models.HomeTopNutrient
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w411dp-h914dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeLookPreviewTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setPreview(
        display: HomeDisplayPreferences = HomeDisplayPreferences(),
        sampleFoodCount: Int = 2,
    ) {
        composeRule.setContent {
            MaterialTheme {
                HomeLookPreview(display = display, sampleFoodCount = sampleFoodCount)
            }
        }
    }

    private fun assertDisplayed(text: String) {
        composeRule.onNodeWithText(text).assertIsDisplayed()
    }

    private fun assertAbsent(text: String) {
        composeRule.onNodeWithText(text).assertDoesNotExist()
    }

    @Test
    fun defaultSampleShowsFixedGoalAndDefaultCards() {
        setPreview()
        assertDisplayed("Sample day")
        assertDisplayed("P")
        assertDisplayed("Fi")
        assertDisplayed("of 2,000 kcal")
        assertAbsent("Steps")
        assertAbsent("380 active")
        assertAbsent("of 2,380 kcal")
    }

    @Test
    fun nutrientSelectionReplacesTheDefaultCards() {
        setPreview(
            HomeDisplayPreferences(
                homeTopNutrients = listOf(HomeTopNutrient.SODIUM),
                nutrientCardCount = 1,
            ),
        )
        assertDisplayed("Sodium")
        assertAbsent("Protein")
    }

    @Test
    fun slimLineFullNamesShowCompleteLabels() {
        setPreview(
            HomeDisplayPreferences(
                homeTopNutrients = listOf(HomeTopNutrient.PROTEIN),
                nutrientCardCount = 1,
                macroCardLayout = HomeMacroCardLayout.SLIM_LINE,
                slimCardFullNames = true,
            ),
        )
        assertDisplayed("Protein")
        assertAbsent("P")
    }

    @Test
    fun foodLogChipsFollowTheSelection() {
        setPreview(
            display = HomeDisplayPreferences(foodLogMacroChips = listOf(FoodLogMacroChip.FAT)),
            sampleFoodCount = 2,
        )
        assertDisplayed("F 6g")
        assertAbsent("P 22g")
    }

    @Test
    fun stepsCardAppearsWhenEnabled() {
        setPreview(HomeDisplayPreferences(showSteps = true))
        assertDisplayed("Steps")
    }

    @Test
    fun addActiveRaisesTheSampleGoal() {
        setPreview(HomeDisplayPreferences(calorieDisplayMode = HomeCalorieDisplayMode.ADD_ACTIVE))
        assertDisplayed("of 2,380 kcal")
        assertAbsent("of 2,000 kcal")
    }

    @Test
    fun staticModeCanShowTheActiveBurnCaption() {
        setPreview(HomeDisplayPreferences(showActiveCalories = true))
        assertDisplayed("380 active")
    }

    @Test
    fun hiddenHeroLayoutOmitsTheGauge() {
        setPreview(HomeDisplayPreferences(calorieHeroLayout = HomeCalorieHeroLayout.HIDDEN))
        assertDisplayed("Sample day")
        assertAbsent("of 2,000 kcal")
    }
}
