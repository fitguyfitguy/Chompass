package app.chompass.ui.progress

import app.chompass.data.yearMonthsOverlapping
import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.GoalJournalEntry
import app.chompass.models.HomeTopNutrient
import app.chompass.models.MacroDayProfile
import app.chompass.models.MacroPlan
import app.chompass.models.MacroPlanMode
import app.chompass.models.MealType
import app.chompass.models.OptionalNutrientGoals
import app.chompass.models.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.YearMonth

class ProgressRangeFoodsTest {
    @Test
    fun yearTotalsMatchFullScanWhenOnlyOverlappingMonthsAreKept() {
        val today = LocalDate.of(2026, 8, 19)
        val foods = (0..729).map { i ->
            val day = today.minusDays(i.toLong())
            entry("Meal $i", day, calories = 100 + (i % 7))
        }
        val full = buildProgressPreviewUiState(
            profile = null,
            weights = emptyList(),
            bodyFatEntries = emptyList(),
            foods = foods,
            timeRange = TimeRange.YEAR,
            anchorDate = today,
        )
        val (start, end) = TimeRange.YEAR.dateRange(today)
        val months = yearMonthsOverlapping(start, end).toSet()
        val zone = ZoneId.systemDefault()
        val scoped = foods.filter { entry ->
            val day = entry.timestamp.atZone(zone).toLocalDate()
            val month = YearMonth.from(day)
            month in months && !day.isBefore(start) && !day.isAfter(end)
        }
        val filtered = buildProgressPreviewUiState(
            profile = null,
            weights = emptyList(),
            bodyFatEntries = emptyList(),
            foods = scoped,
            timeRange = TimeRange.YEAR,
            anchorDate = today,
        )
        assertEquals(full.dailyCalories, filtered.dailyCalories)
        assertEquals(full.macroAverages, filtered.macroAverages)
    }

    @Test
    fun calorieAverageExcludesToday() {
        val today = LocalDate.of(2026, 8, 21)
        val foods = listOf(
            entry("A", today.minusDays(2), calories = 2000),
            entry("B", today.minusDays(1), calories = 2200),
            entry("C", today, calories = 500),
        )
        val ui = buildProgressPreviewUiState(
            profile = null,
            weights = emptyList(),
            bodyFatEntries = emptyList(),
            foods = foods,
            timeRange = TimeRange.ALL_TIME,
            anchorDate = today,
        )
        assertEquals(3, ui.dailyCalories.size)
        assertEquals(2100, ui.calorieAverage)
    }

    @Test
    fun nutrientAveragesExcludeToday() {
        val today = LocalDate.of(2026, 8, 21)
        val foods = listOf(
            entry("A", today.minusDays(2), calories = 2000, fiber = 10.0, sugar = 20.0, sodium = 1000.0),
            entry("B", today.minusDays(1), calories = 2200, fiber = 20.0, sugar = 40.0, sodium = 2000.0),
            entry("C", today, calories = 500, fiber = 99.0, sugar = 99.0, sodium = 99.0),
        )
        val ui = buildProgressPreviewUiState(
            profile = null,
            weights = emptyList(),
            bodyFatEntries = emptyList(),
            foods = foods,
            timeRange = TimeRange.ALL_TIME,
            anchorDate = today,
        )
        // Default selection = the pre-#75 trio; same window, today excluded.
        assertEquals(
            listOf(HomeTopNutrient.FIBER, HomeTopNutrient.SUGAR, HomeTopNutrient.SODIUM),
            ui.nutrientAverages.map { it.nutrient },
        )
        assertEquals(15.0, ui.nutrientAverages.first { it.nutrient == HomeTopNutrient.FIBER }.avg, 0.01)
        assertEquals(30.0, ui.nutrientAverages.first { it.nutrient == HomeTopNutrient.SUGAR }.avg, 0.01)
        assertEquals(1500.0, ui.nutrientAverages.first { it.nutrient == HomeTopNutrient.SODIUM }.avg, 0.01)
        // Goals ride along from OptionalNutrientGoals defaults.
        assertEquals(
            OptionalNutrientGoals.Default.fiber,
            ui.nutrientAverages.first { it.nutrient == HomeTopNutrient.FIBER }.goal,
        )

        // Selecting iron + saturated fat swaps the rows and excludes today's
        // 99s the same way (iron logged only on complete days).
        val selected = buildProgressPreviewUiState(
            profile = null,
            weights = emptyList(),
            bodyFatEntries = emptyList(),
            foods = foods + entry("D", today.minusDays(1), calories = 100, iron = 9.0, saturatedFat = 8.0),
            timeRange = TimeRange.ALL_TIME,
            anchorDate = today,
            averagesSelection = setOf("iron", "saturatedFat"),
        )
        assertEquals(
            listOf(HomeTopNutrient.SATURATED_FAT, HomeTopNutrient.IRON),
            selected.nutrientAverages.map { it.nutrient },
        )
        // Two complete days: (0 + 8) / 2 and (0 + 9) / 2 — missing values
        // average as 0; rows follow canonical declaration order.
        assertEquals(4.0, selected.nutrientAverages[0].avg, 0.01)
        assertEquals(4.5, selected.nutrientAverages[1].avg, 0.01)
        assertEquals(
            OptionalNutrientGoals.Default.iron,
            selected.nutrientAverages[1].goal,
        )
    }

    @Test
    fun oneWeekDoesNotTouchTwoYearOldBuckets() {
        val today = LocalDate.of(2026, 8, 19)
        val (start, end) = TimeRange.WEEK.dateRange(today)
        val months = yearMonthsOverlapping(start, end)
        assertFalse(months.contains(YearMonth.of(2024, 8)))
        assertTrue(months.size <= 2)
    }

    // -- #60 phase 3: journal-backed range goals + per-day bar targets ----------

    @Test
    fun `range goals fall back to current targets without journal coverage`() {
        val today = LocalDate.of(2026, 8, 21)
        val ui = buildProgressPreviewUiState(
            profile = UserProfile(
                customCalories = 2400, customProtein = 150, customCarbs = 250, customFat = 70,
            ),
            weights = emptyList(),
            bodyFatEntries = emptyList(),
            foods = listOf(entry("A", today.minusDays(1), 2000)),
            timeRange = TimeRange.WEEK,
            anchorDate = today,
        )
        assertEquals(2400, ui.calorieGoal)
        assertEquals(150, ui.proteinGoal)
        assertEquals(250, ui.carbsGoal)
        assertEquals(70, ui.fatGoal)
        // Same for every logged bar (plan off = base targets everywhere).
        assertEquals(2400, ui.dailyCalorieGoals[today.minusDays(1)])
    }

    @Test
    fun `range goals use the journaled average and bars their own day targets`() {
        val today = LocalDate.of(2026, 8, 21)
        val profile = UserProfile(
            customCalories = 2400, customProtein = 150, customCarbs = 250, customFat = 70,
            macroPlan = MacroPlan(
                enabled = true,
                profiles = listOf(
                    MacroDayProfile("t", "Training day", 2800, 170, 350, 78),
                    MacroDayProfile("r", "Rest day", 2100, 150, 160, 78),
                ),
                mode = MacroPlanMode.CYCLE,
                defaultProfileId = "r",
                cyclePattern = listOf("t", "t", "r"),
                cycleAnchorDay = today.minusDays(6).toString(),
            ),
        )
        val yesterday = today.minusDays(1)
        val twoDaysAgo = today.minusDays(2)
        val journal = listOf(
            GoalJournalEntry(
                date = twoDaysAgo.toString(), calories = 2800, proteinG = 170, carbsG = 350, fatG = 78,
                profileId = "t", profileName = "Training day", updatedAtMillis = 1L,
            ),
            GoalJournalEntry(
                date = yesterday.toString(), calories = 2100, proteinG = 150, carbsG = 160, fatG = 78,
                profileId = "r", profileName = "Rest day", updatedAtMillis = 2L,
            ),
        )
        val ui = buildProgressPreviewUiState(
            profile = profile,
            weights = emptyList(),
            bodyFatEntries = emptyList(),
            foods = listOf(
                entry("A", twoDaysAgo, 2600),
                entry("B", yesterday, 2050),
            ),
            timeRange = TimeRange.WEEK,
            anchorDate = today,
            goalJournal = journal,
        )
        // MACRO-CYCLE-D: mean of the two journaled days, not the current target.
        assertEquals(2450, ui.calorieGoal)
        assertEquals(160, ui.proteinGoal)
        assertEquals(255, ui.carbsGoal)
        assertEquals(78, ui.fatGoal)
        // Each bar gets its own frozen day target.
        assertEquals(2800, ui.dailyCalorieGoals[twoDaysAgo])
        assertEquals(2100, ui.dailyCalorieGoals[yesterday])
    }

    @Test
    fun `journal days outside the range never bend the average`() {
        val today = LocalDate.of(2026, 8, 21)
        val profile = UserProfile(
            customCalories = 2400, customProtein = 150, customCarbs = 250, customFat = 70,
        )
        val farPast = today.minusDays(90)
        val journal = listOf(
            GoalJournalEntry(
                date = farPast.toString(), calories = 4000, proteinG = 999, carbsG = 999, fatG = 999,
                profileId = null, profileName = null, updatedAtMillis = 1L,
            ),
        )
        val ui = buildProgressPreviewUiState(
            profile = profile,
            weights = emptyList(),
            bodyFatEntries = emptyList(),
            foods = listOf(entry("A", today.minusDays(1), 2000)),
            timeRange = TimeRange.WEEK,
            anchorDate = today,
            goalJournal = journal,
        )
        // No journaled days inside the week -> current targets, gaps skipped
        // (the 90-day-old entry must not leak into the average).
        assertEquals(2400, ui.calorieGoal)
        assertEquals(150, ui.proteinGoal)
    }

    private fun entry(
        name: String,
        day: LocalDate,
        calories: Int,
        fiber: Double? = null,
        sugar: Double? = null,
        sodium: Double? = null,
        iron: Double? = null,
        saturatedFat: Double? = null,
    ) = FoodEntry(
        name = name,
        calories = calories,
        protein = 10.0,
        carbs = 10.0,
        fat = 5.0,
        fiber = fiber,
        sugar = sugar,
        sodium = sodium,
        iron = iron,
        saturatedFat = saturatedFat,
        timestamp = day.atTime(12, 0).toInstant(ZoneOffset.UTC),
        source = FoodSource.MANUAL,
        mealType = MealType.LUNCH.id,
    )
}
