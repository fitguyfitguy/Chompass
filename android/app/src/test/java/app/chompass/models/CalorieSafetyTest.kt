package app.chompass.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

class CalorieSafetyTest {
    private fun atAge(years: Int) =
        LocalDate.now().minusYears(years.toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant()

    @Test
    fun floor_isMaxOfBmrAnd1200() {
        assertEquals(1200, CalorieSafety.floorKcal(1027.75))
        assertEquals(1674, CalorieSafety.floorKcal(1673.75))
        assertEquals(1200, CalorieSafety.floorKcal(976.5))
    }

    @Test
    fun clampAuto_raisesStarvationRangeToFloor() {
        assertEquals(1200, CalorieSafety.clampAuto(683, bmr = 1027.75, tdee = 1233.3))
        assertEquals(1200, CalorieSafety.clampAuto(72, bmr = 976.5, tdee = 1171.8))
    }

    @Test
    fun clampAuto_usesBmrWhenBmrAbove1200() {
        assertEquals(1674, CalorieSafety.clampAuto(1352, bmr = 1673.75, tdee = 2452.0))
    }

    @Test
    fun clampAuto_doesNotLowerASafeTarget() {
        assertEquals(2057, CalorieSafety.clampAuto(2057, bmr = 1780.0, tdee = 2607.7))
    }

    @Test
    fun smallFemaleSedentaryLose_profileDailyCaloriesIsFloor() {
        val p = UserProfile(
            gender = Gender.FEMALE,
            birthday = atAge(60),
            heightCm = 155.0,
            weightKg = 52.0,
            activityLevel = ActivityLevel.SEDENTARY,
            goal = WeightGoal.LOSE,
            weeklyChangeKg = 0.5,
        )
        assertEquals(-550, p.calorieAdjustment)
        assertTrue(p.rawDailyCalories < CalorieSafety.ABSOLUTE_FLOOR_KCAL)
        assertEquals(CalorieSafety.floorKcal(p.bmr), p.dailyCalories)
        assertEquals(p.dailyCalories, p.effectiveCalories)
    }

    @Test
    fun defaultMaleLoseFast_clampsToBmrNot1352() {
        val p = UserProfile(
            gender = Gender.MALE,
            birthday = atAge(25),
            heightCm = 175.0,
            weightKg = 70.0,
            activityLevel = ActivityLevel.MODERATE,
            goal = WeightGoal.LOSE,
            weeklyChangeKg = 1.0,
        )
        assertEquals(-1100, p.calorieAdjustment)
        assertEquals(p.bmr.roundToInt(), p.dailyCalories)
        assertTrue(p.dailyCalories > p.rawDailyCalories)
    }
}
