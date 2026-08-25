package app.chompass.models

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The #60 phase-1 [HomeTopNutrient.goal] overload: macros come from the day's
 * resolved targets with the profile set as fallback; optional micronutrients
 * stay fixed globals; the #38 macro scale still applies to macros only.
 */
class ResolvedDayGoalOverloadTest {
    private val profile = UserProfile(
        customCalories = 2400,
        customProtein = 150,
        customCarbs = 250,
        customFat = 70,
    )
    private val resolved = ResolvedDayTargets(
        targets = DayTargets(2800, 170, 350, 78),
        profileId = "t",
        profileName = "Training day",
    )

    @Test
    fun `resolved targets win for macros`() {
        assertEquals(170, HomeTopNutrient.PROTEIN.goal(resolved, profile, OptionalNutrientGoals.Default))
        assertEquals(350, HomeTopNutrient.CARBS.goal(resolved, profile, OptionalNutrientGoals.Default))
        assertEquals(78, HomeTopNutrient.FAT.goal(resolved, profile, OptionalNutrientGoals.Default))
    }

    @Test
    fun `null resolved falls back to the profile set`() {
        assertEquals(150, HomeTopNutrient.PROTEIN.goal(null, profile, OptionalNutrientGoals.Default))
        assertEquals(250, HomeTopNutrient.CARBS.goal(null, profile, OptionalNutrientGoals.Default))
        assertEquals(70, HomeTopNutrient.FAT.goal(null, profile, OptionalNutrientGoals.Default))
    }

    @Test
    fun `micros stay fixed globals regardless of resolution`() {
        assertEquals(
            OptionalNutrientGoals.Default.fiber,
            HomeTopNutrient.FIBER.goal(resolved, profile, OptionalNutrientGoals.Default),
        )
        assertEquals(
            OptionalNutrientGoals.Default.sodium,
            HomeTopNutrient.SODIUM.goal(resolved, profile, OptionalNutrientGoals.Default),
        )
    }

    @Test
    fun `macro scale applies to resolved macros only`() {
        val scale = 3000f / 2800f
        assertEquals((170 * scale).toInt(), HomeTopNutrient.PROTEIN.goal(resolved, profile, OptionalNutrientGoals.Default, scale))
        assertEquals(
            OptionalNutrientGoals.Default.fiber,
            HomeTopNutrient.FIBER.goal(resolved, profile, OptionalNutrientGoals.Default, scale),
        )
    }
}
