package app.chompass

import app.chompass.models.ActivityLevel
import app.chompass.models.ChatMessage
import app.chompass.models.FoodEntry
import app.chompass.models.FoodLogMacroChip
import app.chompass.models.FoodSource
import app.chompass.models.Gender
import app.chompass.models.HomeCalorieDisplayMode
import app.chompass.models.HomeDisplayPreferences
import app.chompass.models.MealType
import app.chompass.models.UserProfile
import app.chompass.models.WeightGoal
import app.chompass.services.health.HomeActivitySnapshot
import app.chompass.ui.coach.CoachUiState
import app.chompass.ui.home.HomeUiState
import app.chompass.services.SampleDataGenerators
import app.chompass.ui.progress.TimeRange
import app.chompass.ui.progress.buildProgressPreviewUiState
import app.chompass.ui.settings.SettingsUiState
import app.chompass.ui.theme.AppThemeColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Deterministic demo data for JVM release screenshot previews. */
internal object ScreenshotFixtures {
    val snapshotDate: LocalDate = LocalDate.of(2026, 6, 15)
    private val zone: ZoneId = ZoneId.of("UTC")

    private fun atNoon(day: LocalDate): Instant =
        day.atTime(12, 0).atZone(zone).toInstant()

    val profile: UserProfile = UserProfile(
        name = "Alex",
        gender = Gender.MALE,
        birthday = LocalDate.of(1990, 3, 12).atStartOfDay(zone).toInstant(),
        heightCm = 178.0,
        weightKg = 73.5,
        activityLevel = ActivityLevel.MODERATE,
        goal = WeightGoal.LOSE,
        bodyFatPercentage = 0.175,
        goalBodyFatPercentage = 0.15,
        weeklyChangeKg = 0.5,
        goalWeightKg = 68.0,
    )

    private val breakfastId = UUID.fromString("00000000-0000-4000-8000-000000000001")
    private val lunchId = UUID.fromString("00000000-0000-4000-8000-000000000002")
    private val snackId = UUID.fromString("00000000-0000-4000-8000-000000000003")
    private val dinnerId = UUID.fromString("00000000-0000-4000-8000-000000000004")

    val foodEntries: List<FoodEntry> = listOf(
        FoodEntry(
            id = breakfastId,
            name = "Greek yogurt with berries",
            calories = 320,
            protein = 24.0,
            carbs = 28.0,
            fat = 9.0,
            timestamp = atNoon(snapshotDate),
            source = FoodSource.MANUAL,
            mealType = MealType.BREAKFAST,
            fiber = 4.0,
            emoji = "🥣",
        ),
        FoodEntry(
            id = lunchId,
            name = "Chicken rice bowl",
            calories = 540,
            protein = 42.0,
            carbs = 58.0,
            fat = 14.0,
            timestamp = atNoon(snapshotDate).plusSeconds(14_400),
            source = FoodSource.SNAP_FOOD,
            mealType = MealType.LUNCH,
            fiber = 6.0,
            emoji = "🍗",
        ),
        FoodEntry(
            id = snackId,
            name = "Protein bar",
            calories = 210,
            protein = 20.0,
            carbs = 22.0,
            fat = 7.0,
            timestamp = atNoon(snapshotDate).plusSeconds(21_600),
            source = FoodSource.BARCODE,
            mealType = MealType.SNACK,
            emoji = "🍫",
        ),
        FoodEntry(
            id = dinnerId,
            name = "Salmon and roasted vegetables",
            calories = 610,
            protein = 38.0,
            carbs = 24.0,
            fat = 38.0,
            timestamp = atNoon(snapshotDate).plusSeconds(32_400),
            source = FoodSource.MANUAL,
            mealType = MealType.DINNER,
            fiber = 8.0,
            emoji = "🐟",
        ),
    )

    fun homeUiState(): HomeUiState = HomeUiState(
        date = snapshotDate,
        profile = profile,
        todayEntries = foodEntries,
        homeDisplay = HomeDisplayPreferences(
            calorieDisplayMode = HomeCalorieDisplayMode.STATIC,
            showSteps = false,
        ),
        activitySnapshot = HomeActivitySnapshot(date = snapshotDate),
        foodLogMacroChips = FoodLogMacroChip.DefaultSelection,
    )

    fun progressUiState() = buildProgressPreviewUiState(
        profile = profile,
        weights = SampleDataGenerators.yearWeights(today = snapshotDate),
        bodyFatEntries = SampleDataGenerators.bodyFatSeries(
            totalDays = 365,
            startFraction = 0.225,
            endFraction = 0.175,
            seed = 0xFA7365,
            today = snapshotDate,
        ),
        foods = SampleDataGenerators.foodEntries(totalDays = 365, today = snapshotDate),
        timeRange = TimeRange.SIX_MONTHS,
        anchorDate = snapshotDate,
    )

    fun coachUiState(): CoachUiState = CoachUiState(
        messages = listOf(
            ChatMessage(
                id = UUID.fromString("00000000-0000-4000-8000-000000000010"),
                role = ChatMessage.Role.USER,
                content = "How am I doing toward my weight goal this week?",
                timestamp = atNoon(snapshotDate),
            ),
            ChatMessage(
                id = UUID.fromString("00000000-0000-4000-8000-000000000011"),
                role = ChatMessage.Role.ASSISTANT,
                content = "You are averaging about 1,680 kcal/day over the last 7 days, " +
                    "which is on track for roughly 0.5 kg/week loss. Protein is solid at 128 g/day. " +
                    "If dinner calories creep up, try logging snacks right after eating.",
                timestamp = atNoon(snapshotDate).plusSeconds(30),
            ),
        ),
        suggestions = listOf(
            app.chompass.R.string.coach_chip_predict_30_days,
            app.chompass.R.string.coach_chip_lose_faster,
            app.chompass.R.string.coach_chip_eating_too_much,
            app.chompass.R.string.coach_chip_what_dinner,
        ),
    )

    fun settingsUiState(appearanceMode: String = "light"): SettingsUiState = SettingsUiState(
        profile = profile,
        weightUnit = "kg",
        heightUnit = "cm",
        appearanceMode = appearanceMode,
        appThemeColor = AppThemeColor.TEAL,
        adaptiveGoalsEnabled = false,
        healthConnectEnabled = false,
    )
}
