package app.chompass

import app.chompass.models.ActivityLevel
import app.chompass.models.BodyMeasurement
import app.chompass.models.ChatMessage
import app.chompass.models.FoodConstituent
import app.chompass.models.FoodEntry
import app.chompass.models.FoodLogMacroChip
import app.chompass.models.FoodSource
import app.chompass.models.Gender
import app.chompass.models.HomeCalorieDisplayMode
import app.chompass.models.HomeDisplayPreferences
import app.chompass.models.MealType
import app.chompass.models.Recipe
import app.chompass.models.RecipeIngredient
import app.chompass.models.UserProfile
import app.chompass.models.WeightGoal
import app.chompass.services.ai.PartialFoodAnalysis
import app.chompass.services.health.ActivityDataSource
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

    val mealConstituents: List<FoodConstituent> = listOf(
        FoodConstituent(
            name = "Grilled chicken",
            calories = 220,
            protein = 36.0,
            carbs = 0.0,
            fat = 8.0,
            servingSizeGrams = 140.0,
            emoji = "🍗",
        ),
        FoodConstituent(
            name = "Jasmine rice",
            calories = 210,
            protein = 4.0,
            carbs = 46.0,
            fat = 1.0,
            servingSizeGrams = 160.0,
            emoji = "🍚",
        ),
        FoodConstituent(
            name = "Cucumber salad",
            calories = 45,
            protein = 1.0,
            carbs = 8.0,
            fat = 1.0,
            servingSizeGrams = 90.0,
            emoji = "🥒",
        ),
        FoodConstituent(
            name = "Sesame dressing",
            calories = 65,
            protein = 1.0,
            carbs = 4.0,
            fat = 4.0,
            servingSizeGrams = 20.0,
            emoji = "🥣",
        ),
    )

    val demoRecipes: List<Recipe> = listOf(
        Recipe(
            id = UUID.fromString("00000000-0000-4000-8000-000000000021"),
            name = "Chicken rice bowl",
            emoji = "🍗",
            mealType = MealType.LUNCH,
            ingredients = listOf(
                RecipeIngredient(
                    id = UUID.fromString("00000000-0000-4000-8000-000000000031"),
                    name = "Grilled chicken",
                    emoji = "🍗",
                    baseCalories = 220,
                    baseProtein = 36.0,
                    baseCarbs = 0.0,
                    baseFat = 8.0,
                ),
                RecipeIngredient(
                    id = UUID.fromString("00000000-0000-4000-8000-000000000032"),
                    name = "Jasmine rice",
                    emoji = "🍚",
                    baseCalories = 210,
                    baseProtein = 4.0,
                    baseCarbs = 46.0,
                    baseFat = 1.0,
                ),
                RecipeIngredient(
                    id = UUID.fromString("00000000-0000-4000-8000-000000000033"),
                    name = "Cucumber salad",
                    emoji = "🥒",
                    baseCalories = 45,
                    baseProtein = 1.0,
                    baseCarbs = 8.0,
                    baseFat = 1.0,
                ),
            ),
            createdAt = atNoon(snapshotDate),
        ),
        Recipe(
            id = UUID.fromString("00000000-0000-4000-8000-000000000022"),
            name = "Overnight oats",
            emoji = "🥣",
            mealType = MealType.BREAKFAST,
            ingredients = listOf(
                RecipeIngredient(
                    id = UUID.fromString("00000000-0000-4000-8000-000000000034"),
                    name = "Rolled oats",
                    emoji = "🌾",
                    baseCalories = 150,
                    baseProtein = 5.0,
                    baseCarbs = 27.0,
                    baseFat = 3.0,
                ),
                RecipeIngredient(
                    id = UUID.fromString("00000000-0000-4000-8000-000000000035"),
                    name = "Greek yogurt",
                    emoji = "🥛",
                    baseCalories = 100,
                    baseProtein = 17.0,
                    baseCarbs = 6.0,
                    baseFat = 0.0,
                ),
                RecipeIngredient(
                    id = UUID.fromString("00000000-0000-4000-8000-000000000036"),
                    name = "Blueberries",
                    emoji = "🫐",
                    baseCalories = 40,
                    baseProtein = 0.5,
                    baseCarbs = 10.0,
                    baseFat = 0.0,
                ),
            ),
            createdAt = atNoon(snapshotDate).minusSeconds(86_400),
        ),
        Recipe(
            id = UUID.fromString("00000000-0000-4000-8000-000000000023"),
            name = "Salmon plate",
            emoji = "🐟",
            mealType = MealType.DINNER,
            ingredients = listOf(
                RecipeIngredient(
                    id = UUID.fromString("00000000-0000-4000-8000-000000000037"),
                    name = "Baked salmon",
                    emoji = "🐟",
                    baseCalories = 340,
                    baseProtein = 34.0,
                    baseCarbs = 0.0,
                    baseFat = 22.0,
                ),
                RecipeIngredient(
                    id = UUID.fromString("00000000-0000-4000-8000-000000000038"),
                    name = "Roasted broccoli",
                    emoji = "🥦",
                    baseCalories = 70,
                    baseProtein = 4.0,
                    baseCarbs = 10.0,
                    baseFat = 2.0,
                ),
            ),
            createdAt = atNoon(snapshotDate).minusSeconds(172_800),
        ),
    )

    /** Mid-stream AI fields for the progressive analysis marketing shot. */
    val streamingPartial: PartialFoodAnalysis = PartialFoodAnalysis(
        name = "Chicken rice bowl",
        emoji = "🍗",
        calories = 540,
        protein = 42.0,
        carbs = null,
        fat = 14.0,
        servingSizeGrams = 410.0,
        streaming = true,
    )

    fun homeUiState(): HomeUiState = HomeUiState(
        date = snapshotDate,
        profile = profile,
        todayEntries = foodEntries,
        homeDisplay = HomeDisplayPreferences(
            calorieDisplayMode = HomeCalorieDisplayMode.ADD_ACTIVE,
            showSteps = false,
            showActiveCalories = true,
        ),
        measuredActiveAverageCalories = 560,
        activitySnapshot = HomeActivitySnapshot(
            date = snapshotDate,
            steps = 6_842,
            activeCalories = 380,
            totalCalories = 1_250,
            source = ActivityDataSource.HEALTH_CONNECT,
        ),
        foodLogMacroChips = FoodLogMacroChip.DefaultSelection,
    )

    /**
     * Home variant where the logged macros exceed their goals, so the macro
     * cards render the "over" status line (macro_status_over) — the surface
     * that overflowed in German ("überschritten") and used the prefix form in
     * Russian ("Превышение …"). Used by the de/ru locale screenshot tests.
     */
    fun homeOverGoalUiState(): HomeUiState = homeUiState().copy(
        profile = profile.copy(
            customProtein = 100,
            customCarbs = 100,
            customFat = 50,
        ),
    )

    /**
     * Home variant with a long macro status line (large left/over difference) —
     * the ru "91,5g осталось" case that clipped at max font scale. Used by the
     * max-font-scale locale screenshot test (auto-size regression guard).
     */
    fun homeLongStatusUiState(): HomeUiState = homeUiState().copy(
        profile = profile.copy(
            customProtein = 100,
            customCarbs = 400, // carbs 132 < 400 → "268g осталось" (long left line)
            customFat = 50,
        ),
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
        bodyMeasurements = SampleDataGenerators.measurementSeries(
            totalDays = 180,
            seed = 0x7A11,
            today = snapshotDate,
        ),
        measurementSites = setOf(BodyMeasurement.Site.WAIST, BodyMeasurement.Site.HIPS, BodyMeasurement.Site.CHEST),
    )

    /** Progress variant with no weight/body-fat history so the enabled
     *  measurement plot cards sit high enough to be fully visible in one shot. */
    fun progressPlotsUiState() = buildProgressPreviewUiState(
        profile = profile,
        weights = emptyList(),
        bodyFatEntries = emptyList(),
        foods = SampleDataGenerators.foodEntries(totalDays = 365, today = snapshotDate),
        timeRange = TimeRange.SIX_MONTHS,
        anchorDate = snapshotDate,
        bodyMeasurements = SampleDataGenerators.measurementSeries(
            totalDays = 180,
            seed = 0x7A11,
            today = snapshotDate,
        ),
        measurementSites = setOf(BodyMeasurement.Site.WAIST, BodyMeasurement.Site.HIPS, BodyMeasurement.Site.CHEST),
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
