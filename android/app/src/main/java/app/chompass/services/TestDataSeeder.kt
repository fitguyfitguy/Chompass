package app.chompass.services

import app.chompass.AppContainer
import app.chompass.BuildConfig
import app.chompass.data.WeatherRepository
import app.chompass.models.ActivityLevel
import app.chompass.models.AutoBalanceMacro
import app.chompass.models.BodyFatEntry
import app.chompass.models.BodyCompositionEntry
import app.chompass.models.BodyMeasurement
import app.chompass.models.CaffeineEntry
import app.chompass.models.CaffeineKind
import app.chompass.models.CalorieSafety
import app.chompass.models.ChatMessage
import app.chompass.models.DailyNote
import app.chompass.models.DietMode
import app.chompass.models.FoodEntry
import app.chompass.models.FoodLogMacroChip
import app.chompass.models.FoodSource
import app.chompass.models.Gender
import app.chompass.models.GoalJournalEntry
import app.chompass.models.GoalJournalSource
import app.chompass.models.GoalPhase
import app.chompass.models.GoalPhaseEdit
import app.chompass.models.PhaseIntent
import app.chompass.models.HabitPresetCatalog
import app.chompass.models.HomeCalorieDisplayMode
import app.chompass.models.HomeTopNutrient
import app.chompass.models.KetoCarbMode
import app.chompass.models.MacroDayProfile
import app.chompass.models.MacroPlan
import app.chompass.models.MacroPlanMode
import app.chompass.models.MacroPlanResolver
import app.chompass.models.ManualActiveEntry
import app.chompass.models.MealCatalog
import app.chompass.models.MealType
import app.chompass.models.MilkKind
import app.chompass.models.NicotineEntry
import app.chompass.models.NicotineKind
import app.chompass.models.OptionalNutrientGoals
import app.chompass.models.ProteinTargetMode
import app.chompass.models.QueuedAnalysis
import app.chompass.models.QueueStatus
import app.chompass.models.Recipe
import app.chompass.models.UserProfile
import app.chompass.models.WaterEntry
import app.chompass.models.WeightEntry
import app.chompass.models.WeightGoal
import app.chompass.models.toFoodEntry
import app.chompass.services.health.DebugActivityDay
import app.chompass.services.weather.OmCity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Dev-only helper that swaps the user's real data for a year of synthetic food + weight
 * entries so the Progress tab can be eyeballed end-to-end. Triggered by launch flags from
 * MainActivity:
 *   adb shell am start -n app.chompass/.MainActivity --ez seed_test_data true
 *   adb shell am start -n app.chompass/.MainActivity --ez restore_real_data true
 *
 * All entry points are dead in release builds (the intent extras are gated on
 * BuildConfig.DEBUG in MainActivityDebugExtras, and [snapshotRealDataIfNeeded] /
 * [restore] no-op here at the data layer too) — an unprivileged app must never be
 * able to overwrite the diary via exported-activity extras.
 *
 * `seed` snapshots the live state into a single backup blob, disables Health Connect so the
 * synthetic entries can't sync upstream, then writes 365 days of food + weights + body fat +
 * debug steps/active-calorie burn for the home activity cards.
 * `restore` puts everything back exactly as it was.
 */
class TestDataSeeder(private val container: AppContainer) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _seeding = MutableStateFlow(false)
    val seeding: StateFlow<Boolean> = _seeding.asStateFlow()

    /**
     * Focused seeder for Keto mode debugging. It mutates only profile fields (diet mode + keto
     * carb controls + a realistic body-fat context) and leaves food/weight/body-fat history intact.
     *
     *   adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez seed_keto_settings true
     */
    suspend fun seedKetoSettings() {
        snapshotRealDataIfNeeded()

        val baseProfile = container.profileRepository.profile.first()
            ?: UserProfile(weightKg = 80.0, goalWeightKg = 72.0)
        container.profileRepository.save(
            baseProfile.copy(
                dietMode = DietMode.KETO,
                ketoCarbMode = KetoCarbMode.MANUAL,
                ketoCarbManualTarget = 25,
                bodyFatPercentage = baseProfile.bodyFatPercentage ?: 0.22,
                bodyFatUpdatedAt = Instant.now(),
                goalBodyFatPercentage = baseProfile.goalBodyFatPercentage ?: 0.15
            )
        )
        container.prefs.setOnboardingCompleted(true)
    }

    /**
     * Debug-only one-row fixture for the AI fill-missing device pass: an
     * all-macro-zeros named entry in TODAY's diary (the manual-entry dialog
     * refuses 0 kcal, so a Maestro flow cannot author this shape through the
     * UI). Idempotent: same-name rows in the current month are removed first.
     *
     *   adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez seed_gap_fixture true
     */
    suspend fun seedGapFixture(name: String = "Lückentest Gericht") {
        if (!BuildConfig.DEBUG) return
        container.prefs.foodEntriesForMonth(YearMonth.now()).first()
            .filter { it.favoriteKey == name.trim().lowercase() }
            .forEach { container.foodRepository.deleteEntry(it) }
        container.foodRepository.addEntry(
            FoodEntry(
                name = name,
                calories = 0,
                protein = 0.0,
                carbs = 0.0,
                fat = 0.0,
                timestamp = Instant.now(),
                source = FoodSource.MANUAL,
                mealType = MealType.LUNCH.id,
            )
        )
    }

    /**
     * Focused seeder for macro day-type debugging (#60 phase 1): Training/Rest
     * profiles on a 2-on/1-off cycle anchored today−14, with one past and one
     * future override. Mutates only the profile (like [seedKetoSettings]) —
     * food/weight history stays untouched. KETO keeps the plan disabled (the
     * feature is STANDARD-only, settled decision Q4).
     *
     *   adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez seed_macro_cycle true
     *   adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez seed_full true --ez macro_cycle true
     */
    suspend fun seedMacroCycle() {
        if (!BuildConfig.DEBUG) return
        snapshotRealDataIfNeeded()

        val baseProfile = container.profileRepository.profile.first()
            ?: UserProfile(weightKg = 80.0, goalWeightKg = 72.0)
        container.profileRepository.save(baseProfile.copy(macroPlan = macroCyclePlan(baseProfile)))
        container.prefs.setOnboardingCompleted(true)
    }

    /**
     * Standalone goal-phase fixture (Progress v2): applies the phase examples
     * to the current profile without touching diary/weight data.
     *
     *   adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez seed_goal_phases true
     */
    suspend fun seedGoalPhases() {
        if (!BuildConfig.DEBUG) return
        snapshotRealDataIfNeeded()

        val baseProfile = container.profileRepository.profile.first()
            ?: UserProfile(weightKg = 80.0, goalWeightKg = 72.0)
        container.profileRepository.save(goalPhaseSeededProfile(baseProfile))
        container.prefs.setOnboardingCompleted(true)
    }

    /**
     * The phase examples: a past CUT inside the seeded diary history
     * (journal-backed adherence line) and a current BULK spanning ±2 weeks.
     * Applied through [GoalPhaseEdit.applyPhase] so seeds exercise the exact
     * production write path (Base profile + range assignments). Composes with
     * the macro-cycle plan; skipped in keto (STANDARD-only).
     */
    internal fun goalPhaseSeededProfile(profile: UserProfile): UserProfile {
        val today = LocalDate.now()
        val base = MacroPlanResolver.baseTargets(profile)
        val cut = GoalPhase(
            id = seedId("phase-cut"),
            name = "Winter cut",
            intent = PhaseIntent.CUT,
            startDate = today.minusDays(28).toString(),
            endDate = today.minusDays(15).toString(),
            calories = (base.calories - 400).coerceAtLeast(CalorieSafety.ABSOLUTE_FLOOR_KCAL),
            proteinG = base.proteinG,
            carbsG = (base.carbsG - 50).coerceAtLeast(20),
            fatG = base.fatG,
        )
        val bulk = GoalPhase(
            id = seedId("phase-bulk"),
            name = "Lean bulk",
            intent = PhaseIntent.BULK,
            startDate = today.minusDays(14).toString(),
            endDate = today.plusDays(14).toString(),
            calories = base.calories + 300,
            proteinG = base.proteinG + 10,
            carbsG = base.carbsG + 60,
            fatG = base.fatG + 10,
        )
        var next = profile
        for (phase in listOf(cut, bulk)) {
            next = GoalPhaseEdit.applyPhase(next, phase, today)
        }
        return next
    }

    /** The seeded plan: ±300 kcal Training/Rest spread around the base targets. */
    internal fun macroCyclePlan(profile: UserProfile): MacroPlan {
        val base = MacroPlanResolver.baseTargets(profile)
        val trainingCalories = base.calories + 300
        val restCalories = (base.calories - 300).coerceAtLeast(CalorieSafety.ABSOLUTE_FLOOR_KCAL)
        val trainingCarbs = base.carbsG + 45
        val restCarbs = (base.carbsG - 60).coerceAtLeast(20)
        val training = MacroDayProfile(
            id = "seed-training",
            name = "Training day",
            calories = trainingCalories,
            proteinG = base.proteinG + 10,
            carbsG = trainingCarbs,
            fatG = ((trainingCalories - (base.proteinG + 10) * 4 - trainingCarbs * 4) / 9.0).roundToInt(),
            colorKey = "green",
        )
        val rest = MacroDayProfile(
            id = "seed-rest",
            name = "Rest day",
            calories = restCalories,
            proteinG = base.proteinG,
            carbsG = restCarbs,
            fatG = ((restCalories - base.proteinG * 4 - restCarbs * 4) / 9.0).roundToInt(),
            colorKey = "blue",
        )
        val today = LocalDate.now()
        return MacroPlan(
            enabled = profile.dietMode != DietMode.KETO,
            profiles = listOf(training, rest),
            mode = MacroPlanMode.CYCLE,
            defaultProfileId = training.id,
            cyclePattern = listOf(training.id, training.id, rest.id),
            cycleAnchorDay = today.minusDays(14).toString(),
            dayAssignments = mapOf(
                // Past override: a rest day the 2-on/1-off cycle would call training.
                today.minusDays(3).toString() to rest.id,
                // Future override: a training day the cycle would call rest.
                today.plusDays(2).toString() to training.id,
            ),
        )
    }

    suspend fun seedYear() {
        snapshotRealDataIfNeeded()

        container.prefs.setHealthConnectEnabled(false)

        val baseProfile = container.profileRepository.profile.first()
            ?: UserProfile(weightKg = 75.0, goalWeightKg = 70.0)
        container.profileRepository.save(
            baseProfile.copy(
                weightKg = 73.5,
                goalWeightKg = 70.0,
                bodyFatPercentage = 0.175,
                bodyFatUpdatedAt = Instant.now(),
                goalBodyFatPercentage = 0.15
            )
        )
        container.prefs.setOnboardingCompleted(true)

        container.foodRepository.replaceAll(SampleDataGenerators.foodEntries())
        container.weightRepository.replaceAll(SampleDataGenerators.yearWeights())
        container.bodyFatRepository.replaceAll(
            SampleDataGenerators.bodyFatSeries(totalDays = 365, startFraction = 0.225, endFraction = 0.175, seed = 0xFA7365)
        )
        seedDebugHomeActivity(totalDays = 365)
    }

    /**
     * One atomic power-user fixture: year of enriched food (full micros,
     * serving units, notes, product cards, composite meals, planned rows,
     * custom meal slot), 2y metrics + all eight measurement sites, water,
     * recipes (hand-built + Mealie imports), favorites, notes, chat with an
     * attachment, tracker presets, caffeine milk sidecars, manual active burn
     * and a fresh weather cache. Idempotent: replaceAll / set* overwrite, they
     * do not stack.
     *
     *   adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez seed_full true
     *   adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez seed_full true --ez seed_keto_settings true
     *   adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez seed_full true --ez seed_busy_home true
     *   adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez seed_full true --ez macro_cycle true
     *
     * `busy_home` additionally turns on the extra Home/Progress cards (steps,
     * active calories, 4 nutrient cards, Progress nutrient averages + its
     * selection, food-log macro chips) — the "everything switched on" state.
     */
    suspend fun seedFullyUtilized(
        keto: Boolean = false,
        busyHome: Boolean = false,
        macroCycle: Boolean = false,
        goalPhases: Boolean = false,
    ) {
        if (!BuildConfig.DEBUG) return
        _seeding.value = true
        try {
            snapshotRealDataIfNeeded()
            container.prefs.setHealthConnectEnabled(false)

            val profile = UserProfile(
                name = "Alex",
                gender = Gender.MALE,
                heightCm = 178.0,
                weightKg = 73.5,
                activityLevel = ActivityLevel.MODERATE,
                goal = WeightGoal.LOSE,
                dietMode = if (keto) DietMode.KETO else DietMode.STANDARD,
                ketoCarbMode = if (keto) KetoCarbMode.MANUAL else KetoCarbMode.ADAPTIVE,
                ketoCarbManualTarget = if (keto) 25 else null,
                bodyFatPercentage = 0.175,
                bodyFatUpdatedAt = Instant.now(),
                goalBodyFatPercentage = 0.15,
                goalWeightKg = 70.0,
                weeklyChangeKg = 0.5,
            ).let { base ->
                // #60 macro_cycle flag: Training/Rest day types on the fixture
                // profile. Ignored with keto (STANDARD-only, Q4) — the plan is
                // stored disabled instead.
                var p = if (macroCycle) base.copy(macroPlan = macroCyclePlan(base)) else base
                // Progress v2 `seed_goal_phases` flag: CUT + BULK phase windows
                // via the production GoalPhaseEdit write path (also keto-skipped).
                if (goalPhases && !keto) p = goalPhaseSeededProfile(p)
                p
            }
            container.profileRepository.save(profile)
            container.prefs.setOnboardingCompleted(true)

            val today = LocalDate.now()
            val zone = ZoneId.systemDefault()

            // Diary: the year of mixed-source rows, enriched per index with full
            // micros / serving units / notes / product cards, plus composite
            // meals, the custom-slot rows, the planned rows and the milk
            // sidecars the caffeine logs link to. Deterministic ids on the rows
            // this seeder authors directly (defect-layer invariant).
            val enrichedFoods = attachSeedPhotos(SampleDataGenerators.fullFoodEntries())
                .mapIndexed { i, entry ->
                    var row = entry
                    if (i % 3 == 0) row = SampleDataGenerators.withFullMicros(row, seed = 0xF00D000L + i)
                    if (i % 5 == 0) row = SampleDataGenerators.withServingUnits(row)
                    if (i % 4 == 0) row = row.copy(customNote = SEED_NOTES[i % SEED_NOTES.size])
                    if (row.source == FoodSource.BARCODE) row = SampleDataGenerators.withProductMetadata(row)
                    when {
                        i % 97 == 0 -> row.copy(source = FoodSource.NUTRITION_LABEL)
                        i % 89 == 0 -> row.copy(source = FoodSource.SEARCH)
                        else -> row
                    }
                }
            val milkRows = (0 until 5).map { week ->
                val day = today.minusDays(week * 7L)
                MilkKind.OAT.toFoodEntry(
                    ml = 30,
                    at = day.atTime(7, 45).atZone(zone).toInstant(),
                    mealId = MealType.BREAKFAST.id,
                    name = container.appContext.getString(MilkKind.OAT.labelRes) + " 30 ml",
                ).copy(id = seedId("milk:$week"))
            }
            val customSlotRows = listOf(3L, 9L).mapIndexed { i, daysAgo ->
                val day = today.minusDays(daysAgo)
                FoodEntry(
                    id = seedId("custom-slot:$daysAgo"),
                    name = if (i == 0) "Seed brunch plate" else "Seed brunch bowl",
                    calories = if (i == 0) 520 else 610,
                    protein = if (i == 0) 32.0 else 27.0,
                    carbs = if (i == 0) 48.0 else 74.0,
                    fat = if (i == 0) 22.0 else 19.0,
                    timestamp = day.atTime(11, 30).atZone(zone).toInstant(),
                    emoji = "\uD83E\uDD5E",
                    source = FoodSource.MANUAL,
                    mealType = SampleDataGenerators.SEED_CUSTOM_MEAL_ID,
                )
            }
            // Planned rows live in the FUTURE only (today's Home totals stay put)
            // and use the custom slot so the planned chip + Plan-week screen render.
            val plannedRows = listOf(1L, 2L).mapIndexed { i, offset ->
                FoodEntry(
                    id = seedId("planned:$offset"),
                    name = if (i == 0) "Planned chicken bowl" else "Planned lentil curry",
                    calories = if (i == 0) 640 else 560,
                    protein = if (i == 0) 44.0 else 24.0,
                    carbs = if (i == 0) 62.0 else 68.0,
                    fat = if (i == 0) 21.0 else 18.0,
                    timestamp = today.plusDays(offset).atTime(12, 30).atZone(zone).toInstant(),
                    emoji = "\uD83E\uDD69",
                    source = FoodSource.MANUAL,
                    mealType = SampleDataGenerators.SEED_CUSTOM_MEAL_ID,
                    planned = true,
                )
            }
            container.foodRepository.replaceAll(
                enrichedFoods +
                    SampleDataGenerators.compositeEntries() +
                    milkRows +
                    customSlotRows +
                    plannedRows
            )
            val weights = SampleDataGenerators.weightSeries(totalDays = 730, startKg = 82.0, endKg = 73.5, seed = 0x2BEEF)
            val bodyFats = SampleDataGenerators.bodyFatSeries(totalDays = 730, startFraction = 0.240, endFraction = 0.175, seed = 0x2FA7)
            container.weightRepository.replaceAll(weights)
            container.bodyFatRepository.replaceAll(bodyFats)
            container.bodyCompositionRepository.replaceAll(
                SampleDataGenerators.compositionSeries(weights, bodyFats, seed = 0xC0B5)
            )
            // All eight tape sites (measurementSeries only logs the four the
            // Progress plots used to ship with).
            container.bodyMeasurementRepository.replaceAll(
                SampleDataGenerators.allSitesMeasurementSeries(totalDays = 180, seed = 0x7A1)
            )
            seedDebugHomeActivity(totalDays = 365)
            if (macroCycle && !keto) seedDayTypeActiveHistory(profile)

            val favorites = SampleDataGenerators.sampleFavorites() + SampleDataGenerators.richFavorites()
            container.prefs.setFavoriteFoodEntries(favorites)
            container.prefs.setFavoriteKeys(favorites.map { it.favoriteKey }.toSet())
            container.prefs.setRecipes(
                SampleDataGenerators.sampleRecipes() + SampleDataGenerators.sampleMealieRecipes()
            )
            container.prefs.setWaterTrackingEnabled(true)
            container.prefs.setWaterDynamicEnabled(true)
            container.prefs.setWaterFoodWaterEnabled(true)
            container.prefs.setWaterDailyGoalMl(2_500)
            container.prefs.setWaterQuickPresetsMl(listOf(200, 330, 500, 750))
            container.prefs.setWaterEntries(SampleDataGenerators.waterEntries())
            container.prefs.setNicotineTrackingEnabled(true)
            container.prefs.setNicotineDailyLimit(10)
            container.prefs.setNicotineEntries(SampleDataGenerators.nicotineEntries())
            container.prefs.setCaffeineTrackingEnabled(true)
            val (caffeinePresets, nicotinePresets) = SampleDataGenerators.trackerPresets()
            container.prefs.setCaffeinePresets(caffeinePresets)
            container.prefs.setNicotinePresets(nicotinePresets)
            container.prefs.setCaffeineQuickKinds(
                listOf(CaffeineKind.COFFEE.storageKey, SampleDataGenerators.SEED_CAFFEINE_PRESET_ID)
            )
            container.prefs.setNicotineQuickKinds(listOf(NicotineKind.CIGARETTE.storageKey))
            container.prefs.setCaffeineEntries(
                SampleDataGenerators.caffeineEntries() +
                    milkRows.map { row ->
                        // Milk sidecar logs so CaffeineMilkSection has something to show.
                        CaffeineEntry(
                            id = seedId("caffeine-milk:" + row.id),
                            date = row.timestamp.plusSeconds(600),
                            kind = CaffeineKind.COFFEE.storageKey,
                            mg = CaffeineKind.COFFEE.defaultMg ?: 95.0,
                            linkedFoodEntryId = row.id,
                        )
                    } +
                    listOf(
                        CaffeineEntry(
                            id = seedId("caffeine-custom:0"),
                            date = today.atTime(15, 10).atZone(zone).toInstant(),
                            kind = SampleDataGenerators.SEED_CAFFEINE_PRESET_ID,
                            mg = 42.0,
                        ),
                        CaffeineEntry(
                            id = seedId("caffeine-custom:1"),
                            date = today.minusDays(2).atTime(15, 20).atZone(zone).toInstant(),
                            kind = SampleDataGenerators.SEED_CAFFEINE_PRESET_ID,
                            mg = 42.0,
                        ),
                    )
            )
            container.prefs.setManualActiveEntries(
                listOf(
                    ManualActiveEntry(
                        id = seedId("active-walk").toString(),
                        date = today.toString(),
                        name = "Seed Walk",
                        calories = 180,
                    ),
                    ManualActiveEntry(
                        id = seedId("active-ride").toString(),
                        date = today.minusDays(4).toString(),
                        name = "Seed Ride",
                        calories = 320,
                    ),
                )
            )
            container.prefs.setDailyNotesEnabled(true)
            SampleDataGenerators.sampleNotes(today).forEach {
                container.notesRepository.setNote(it.date, it.text)
            }
            container.prefs.setMealCatalog(SampleDataGenerators.sampleMealCatalog())
            container.prefs.setMealPlanningEnabled(true)
            container.chatRepository.replaceAll(SampleDataGenerators.sampleChat(withAttachment = true))
            container.prefs.setCoachTabEnabled(true)

            // Water dynamic goal: a fresh Open-Meteo cache for today, so the
            // temperature factor actually applies (31 °C → factor 1.24).
            container.prefs.setWeatherSource(WeatherRepository.SOURCE_OPEN_METEO)
            container.prefs.setWeatherOmCity(json.encodeToString(OmCity.serializer(), SEED_OM_CITY))
            container.prefs.setWeatherOmHighC(31)
            container.prefs.setWeatherOmDate(today.toString())
            container.prefs.setWeatherOmUpdatedAt(System.currentTimeMillis())

            container.prefs.setProgressMeasurementSites(
                BodyMeasurement.Site.entries.map { it.storageId }.toSet()
            )
            container.prefs.setOptionalNutrientGoals(
                OptionalNutrientGoals.Default.copy(fiber = 30, sodium = 2300, potassium = 3500)
            )

            container.prefs.setNotificationsEnabled(true)
            container.prefs.setStreakReminderEnabled(true)
            container.prefs.setStreakReminderHour(20)
            container.prefs.setDailySummaryEnabled(true)
            container.prefs.setDailySummaryHour(21)
            container.prefs.setWaterReminderEnabled(true)

            if (busyHome) {
                container.prefs.setHomeShowSteps(true)
                container.prefs.setHomeShowActiveCalories(true)
                container.prefs.setHomeNutrientCardCount(4)
                container.prefs.setHomeTopNutrients(
                    HomeTopNutrient.toStorage(
                        listOf(
                            HomeTopNutrient.PROTEIN,
                            HomeTopNutrient.FIBER,
                            HomeTopNutrient.SODIUM,
                            HomeTopNutrient.POTASSIUM,
                        ),
                    )
                )
                // The "extra buttons" half of busy_home: the Progress nutrient
                // averages card, its selection, and the food-log macro chips.
                container.prefs.setProgressNutrientAverages(true)
                container.prefs.setProgressNutrientAveragesSelection(
                    HomeTopNutrient
                        .toStorage(
                            listOf(
                                HomeTopNutrient.FIBER,
                                HomeTopNutrient.SUGAR,
                                HomeTopNutrient.SODIUM,
                                HomeTopNutrient.POTASSIUM,
                            ),
                        )
                        .split(",")
                        .filter { it.isNotBlank() }
                        .toSet()
                )
                container.prefs.setFoodLogMacroChips(
                    FoodLogMacroChip.toStorage(
                        listOf(
                            FoodLogMacroChip.PROTEIN,
                            FoodLogMacroChip.CARBS,
                            FoodLogMacroChip.FAT,
                            FoodLogMacroChip.FIBER,
                            FoodLogMacroChip.SUGAR,
                        )
                    )
                )
            }
        } finally {
            _seeding.value = false
        }
    }

    /** Deterministic fixture id: the bucket stores upsert by id, so re-seeding a
     *  row must produce the same id instead of stacking a duplicate. */
    internal fun seedId(slug: String): UUID =
        UUID.nameUUIDFromBytes("chompass-seed:$slug".toByteArray())

    private fun attachSeedPhotos(entries: List<FoodEntry>): List<FoodEntry> {
        val fixtures = SEED_PHOTO_ASSETS.mapNotNull { path ->
            runCatching { container.appContext.assets.open(path).use { it.readBytes() } }.getOrNull()
                ?.takeIf { it.isNotEmpty() }
        }
        if (fixtures.isEmpty()) return entries
        val photoIdxs = entries.indices.filter { entries[it].source == FoodSource.SNAP_FOOD }.takeLast(36)
        if (photoIdxs.isEmpty()) return entries
        val out = entries.toMutableList()
        photoIdxs.forEachIndexed { i, idx ->
            val entry = out[idx]
            val filename = container.imageStore.storeBytes(fixtures[i % fixtures.size], entry.id)
            if (filename != null) out[idx] = entry.copy(imageFilename = filename)
        }
        return out
    }

    /**
     * Debug-only: 28 days of journal + per-type active so Training vs Rest
     * typicals differ. Journal provenance is covered too: one MANUAL_SWITCH,
     * one OVERRIDE, one GAP_FILL, the rest PLAN.
     */
    private suspend fun seedDayTypeActiveHistory(profile: UserProfile) {
        val plan = profile.macroPlan ?: return
        val today = LocalDate.now()
        val journal = mutableListOf<GoalJournalEntry>()
        val active = mutableMapOf<String, Int>()
        for (i in 0 until 28) {
            val day = today.minusDays(i.toLong())
            val resolved = MacroPlanResolver.targetsFor(profile, day)
            val id = resolved.profileId ?: continue
            journal += GoalJournalEntry(
                date = day.toString(),
                calories = resolved.targets.calories,
                proteinG = resolved.targets.proteinG,
                carbsG = resolved.targets.carbsG,
                fatG = resolved.targets.fatG,
                profileId = id,
                profileName = resolved.profileName,
                updatedAtMillis = System.currentTimeMillis(),
                source = when (i) {
                    1 -> GoalJournalSource.MANUAL_SWITCH
                    2 -> GoalJournalSource.OVERRIDE
                    3 -> GoalJournalSource.GAP_FILL
                    else -> GoalJournalSource.PLAN
                },
            )
            val isTraining = id.contains("training", ignoreCase = true)
            active[day.toString()] = if (isTraining) 620 else 320
        }
        container.prefs.setGoalJournal(journal)
        container.prefs.setHealthEnergyActiveByDay(active)
        container.prefs.setHealthEnergyMeasuredActive(480)
    }

    private suspend fun seedDebugHomeActivity(totalDays: Int) {
        container.prefs.setDebugActivityDays(SampleDataGenerators.debugActivityDays(totalDays))
        container.prefs.setHomeCalorieDisplayMode(HomeCalorieDisplayMode.ADD_ACTIVE.storageKey)
        // Keep seed defaults aligned with production home defaults:
        // no separate activity cards, burn integrated into the hero gauge.
        container.prefs.setHomeShowSteps(false)
        container.prefs.setHomeShowActiveCalories(false)
    }

    /**
     * seed_test_data + the hero burn thermometer enabled. [overrideTodayActive]
     * optionally replaces today's debug active burn; 0 exercises the measured-0
     * morning (projected-day arc, "0 of Y active" caption):
     *
     *   adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez seed_active_calories true
     *   adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez seed_active_calories true --ei active_today_override 1200
     *   adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez seed_active_calories true --ei active_today_override 0
     */
    suspend fun seedActiveCalories(overrideTodayActive: Int? = null) {
        seedYear()
        if (overrideTodayActive != null) {
            val today = LocalDate.now().toString()
            val days = container.prefs.debugActivityDaysJson()?.let {
                runCatching {
                    json.decodeFromString(ListSerializer(DebugActivityDay.serializer()), it)
                }.getOrNull()
            } ?: emptyList()
            container.prefs.setDebugActivityDays(
                days.filterNot { it.date == today } +
                    DebugActivityDay(
                        date = today,
                        steps = 11_000,
                        activeCalories = overrideTodayActive,
                        totalCalories = overrideTodayActive + 1_600,
                    )
            )
        }
        container.prefs.setHomeShowActiveCalories(true)
    }

    /** Switch the hero gauge mode directly ("static" or "add_active") without re-seeding. */
    suspend fun setGaugeMode(value: String) {
        val mode = when (value.lowercase()) {
            "add_active" -> HomeCalorieDisplayMode.ADD_ACTIVE
            else -> HomeCalorieDisplayMode.STATIC
        }
        container.prefs.setHomeCalorieDisplayMode(mode.storageKey)
    }

    /**
     * Force the "Show active calories" toggle on/off. In STATIC mode it gates
     * the "N active" caption; in ADD_ACTIVE the burn presentation is intrinsic.
     */
    suspend fun setShowActiveCalories(show: Boolean) {
        container.prefs.setHomeShowActiveCalories(show)
    }

    /** Drop all debug activity days so the snapshot has no live source (estimate-only path). */
    suspend fun clearDebugActivity() {
        container.prefs.setDebugActivityDays(emptyList())
    }

    /** Toggle the home steps card (forces the activity snapshot to load even when active calories are hidden). */
    suspend fun setShowSteps(show: Boolean) {
        container.prefs.setHomeShowSteps(show)
    }

    /**
     * Toggle the resting (basal) burn rim in the hero arc — debug A/B comparison
     * between the active-shades-only design and the active + resting rim design.
     */
    suspend fun setShowRestingShade(show: Boolean) {
        container.prefs.setDebugShowRestingShade(show)
    }

    /** Insert a large food entry for today so eaten exceeds the goal (over-goal capture). */
    suspend fun seedOverGoal() {
        val zone = ZoneId.systemDefault()
        val ts = LocalDate.now().atTime(LocalTime.now()).atZone(zone).toInstant()
        container.foodRepository.addEntry(
            FoodEntry(
                name = "Post-dinner snack (over-goal fixture)",
                calories = 1_500,
                protein = 60.0,
                carbs = 160.0,
                fat = 55.0,
                timestamp = ts,
                emoji = "🍽️",
                source = FoodSource.TEXT_INPUT,
                mealType = MealType.SNACK.id,
            )
        )
    }

    /**
     * Focused seeder for the v3.2 Progress chart verification — fills 30 days
     * of weight + 30 days of body-fat readings (overlapping so both 1W and 1M
     * Progress views render with data + a goal-line overlay) without touching
     * food entries or other state. Use this when verifying the Body Fat chart
     * + segmented toggle on debug:
     *
     *   adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez seed_body_metrics true
     *   adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez restore_real_data true
     *
     * Snapshots into the same SeedBackup blob seedYear uses, so restore_real_data
     * recovers the original state regardless of which seeder was last invoked.
     */
    suspend fun seedBodyMetrics() {
        snapshotRealDataIfNeeded()

        // Disable HC so the synthetic entries can't echo to the production
        // install's HC sync relationship. (Ports the same guard from seedYear.)
        container.prefs.setHealthConnectEnabled(false)

        // Seed the profile with body fat + a goal so the Body Fat segment on
        // Progress is visible (showsBodyFatSection guard checks any of: entries
        // exist, profile.bodyFatPercentage, profile.goalBodyFatPercentage).
        val baseProfile = container.profileRepository.profile.first()
            ?: UserProfile(weightKg = 75.0, goalWeightKg = 70.0)
        container.profileRepository.save(
            baseProfile.copy(
                weightKg = 73.0,
                goalWeightKg = 68.0,
                bodyFatPercentage = 0.18,
                bodyFatUpdatedAt = Instant.now(),
                goalBodyFatPercentage = 0.12
            )
        )
        container.prefs.setOnboardingCompleted(true)

        container.weightRepository.replaceAll(
            SampleDataGenerators.weightSeries(totalDays = 30, startKg = 75.5, endKg = 73.0, seed = 0xBEEF)
        )
        container.bodyFatRepository.replaceAll(
            SampleDataGenerators.bodyFatSeries(totalDays = 30, startFraction = 0.180, endFraction = 0.165, seed = 0xFA7)
        )
        container.bodyCompositionRepository.replaceAll(
            SampleDataGenerators.compositionSeries(
                weights = container.weightRepository.entries.first(),
                bodyFats = container.bodyFatRepository.entries.first(),
                seed = 0xC0B5,
            )
        )
        // Weekly tape readings so the Customize Progress plots have data to show.
        container.bodyMeasurementRepository.replaceAll(
            SampleDataGenerators.measurementSeries(totalDays = 90, seed = 0x7A1)
        )
    }

    /**
     * Long-range variant of seedBodyMetrics — 2 years (730 days) of weight +
     * body-fat readings so the 6M / 1Y / All Progress ranges and the history
     * lists can be eyeballed with realistic volume (~580 weights, ~440 fats).
     *
     *   adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez seed_body_metrics_2y true
     *   adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez restore_real_data true
     */
    suspend fun seedTwoYearsBodyMetrics() {
        snapshotRealDataIfNeeded()

        container.prefs.setHealthConnectEnabled(false)

        val baseProfile = container.profileRepository.profile.first()
            ?: UserProfile(weightKg = 75.0, goalWeightKg = 70.0)
        container.profileRepository.save(
            baseProfile.copy(
                weightKg = 73.0,
                goalWeightKg = 70.0,
                bodyFatPercentage = 0.165,
                bodyFatUpdatedAt = Instant.now(),
                goalBodyFatPercentage = 0.15
            )
        )
        container.prefs.setOnboardingCompleted(true)

        container.weightRepository.replaceAll(
            SampleDataGenerators.weightSeries(totalDays = 730, startKg = 82.0, endKg = 73.0, seed = 0x2BEEF)
        )
        container.bodyFatRepository.replaceAll(
            SampleDataGenerators.bodyFatSeries(totalDays = 730, startFraction = 0.240, endFraction = 0.165, seed = 0x2FA7)
        )
        container.bodyCompositionRepository.replaceAll(
            SampleDataGenerators.compositionSeries(
                weights = container.weightRepository.entries.first(),
                bodyFats = container.bodyFatRepository.entries.first(),
                seed = 0xC0B5,
            )
        )
    }

    /** Snapshot the user's real data into the backup blob — first seed run only.
     *  Re-seeds (e.g. switching from the 30-day to the 2-year dataset) must not
     *  overwrite the original backup, or restore would put seed data back. */
    private suspend fun snapshotRealDataIfNeeded() {
        if (!BuildConfig.DEBUG) return
        if (container.prefs.testSeedBackupJson.first() != null) return
        val backup = SeedBackup(
            entriesJson = json.encodeToString(
                ListSerializer(FoodEntry.serializer()),
                container.foodRepository.entries.first()
            ),
            weightsJson = json.encodeToString(
                ListSerializer(WeightEntry.serializer()),
                container.weightRepository.entries.first()
            ),
            bodyFatsJson = json.encodeToString(
                ListSerializer(BodyFatEntry.serializer()),
                container.bodyFatRepository.entries.first()
            ),
            compositionsJson = json.encodeToString(
                ListSerializer(BodyCompositionEntry.serializer()),
                container.bodyCompositionRepository.entries.first()
            ),
            profileJson = container.profileRepository.profile.first()?.let {
                json.encodeToString(UserProfile.serializer(), it)
            },
            healthConnectEnabled = container.prefs.healthConnectEnabled.first(),
            onboarded = container.prefs.hasCompletedOnboarding.first(),
            debugActivityJson = container.prefs.debugActivityDaysJson(),
            waterJson = json.encodeToString(
                ListSerializer(WaterEntry.serializer()),
                container.prefs.waterEntries.first(),
            ),
            recipesJson = json.encodeToString(
                ListSerializer(Recipe.serializer()),
                container.prefs.recipes.first(),
            ),
            favoritesJson = json.encodeToString(
                ListSerializer(FoodEntry.serializer()),
                container.prefs.favoriteFoodEntries.first(),
            ),
            chatJson = json.encodeToString(
                ListSerializer(ChatMessage.serializer()),
                container.prefs.chatHistory.first(),
            ),
            measurementsJson = json.encodeToString(
                ListSerializer(BodyMeasurement.serializer()),
                container.bodyMeasurementRepository.entries.first(),
            ),
            settings = SeedSettingsSnapshot(
                waterTrackingEnabled = container.prefs.waterTrackingEnabled.first(),
                waterDynamicEnabled = container.prefs.waterDynamicEnabled.first(),
                waterFoodWaterEnabled = container.prefs.waterFoodWaterEnabled.first(),
                waterDailyGoalMl = container.prefs.waterDailyGoalMl.first(),
                waterReminderEnabled = container.prefs.waterReminderEnabled.first(),
                notificationsEnabled = container.prefs.notificationsEnabled.first(),
                streakReminderEnabled = container.prefs.streakReminderEnabled.first(),
                dailySummaryEnabled = container.prefs.dailySummaryEnabled.first(),
                coachTabEnabled = container.prefs.coachTabEnabled.first(),
                progressMeasurementSites = container.prefs.progressMeasurementSites.first(),
                optionalNutrientGoalsJson = json.encodeToString(
                    OptionalNutrientGoals.serializer(),
                    container.prefs.optionalNutrientGoals.first(),
                ),
                homeShowSteps = container.prefs.homeShowSteps.first(),
                homeShowActiveCalories = container.prefs.homeShowActiveCalories.first(),
                homeTopNutrients = container.prefs.homeTopNutrients.first(),
                homeNutrientCardCount = container.prefs.homeNutrientCardCount.first(),
                homeCalorieDisplayMode = container.prefs.homeCalorieDisplayMode.first(),
                dailyNotesEnabled = container.prefs.dailyNotesEnabled.first(),
                mealPlanningEnabled = container.prefs.mealPlanningEnabled.first(),
                foodLogMacroChips = container.prefs.foodLogMacroChips.first(),
                progressNutrientAverages = container.prefs.progressNutrientAverages.first(),
                progressNutrientAveragesSelection = container.prefs.progressNutrientAveragesSelection.first(),
            ),
            notesJson = json.encodeToString(
                ListSerializer(DailyNote.serializer()),
                container.prefs.noteEntries.first(),
            ),
            nicotineJson = json.encodeToString(
                ListSerializer(NicotineEntry.serializer()),
                container.prefs.nicotineEntries.first(),
            ),
            caffeineJson = json.encodeToString(
                ListSerializer(CaffeineEntry.serializer()),
                container.prefs.caffeineEntries.first(),
            ),
            manualActiveJson = json.encodeToString(
                ListSerializer(ManualActiveEntry.serializer()),
                container.prefs.manualActiveEntries.first(),
            ),
            goalJournalJson = json.encodeToString(
                ListSerializer(GoalJournalEntry.serializer()),
                container.prefs.goalJournal.first(),
            ),
            mealCatalogJson = json.encodeToString(
                MealCatalog.serializer(),
                container.prefs.mealCatalog.first(),
            ),
            untrackedJson = json.encodeToString(
                SeedUntrackedSnapshot.serializer(),
                SeedUntrackedSnapshot(
                    days = container.prefs.untrackedDays.first(),
                    kcalByDay = container.prefs.untrackedKcalByDay.first(),
                ),
            ),
            trackerPresetsJson = json.encodeToString(
                SeedTrackerSnapshot.serializer(),
                SeedTrackerSnapshot(
                    caffeinePresets = container.prefs.caffeinePresets.first(),
                    nicotinePresets = container.prefs.nicotinePresets.first(),
                    caffeineQuickKinds = container.prefs.caffeineQuickKinds.first(),
                    nicotineQuickKinds = container.prefs.nicotineQuickKinds.first(),
                    waterQuickPresetsMl = container.prefs.waterQuickPresetsMl.first(),
                ),
            ),
            energyJson = json.encodeToString(
                SeedEnergySnapshot.serializer(),
                SeedEnergySnapshot(
                    activeByDay = container.prefs.healthEnergyActiveByDay.first(),
                    measuredActive = container.prefs.healthEnergyMeasuredActive.first(),
                ),
            ),
            weatherJson = json.encodeToString(
                SeedWeatherSnapshot.serializer(),
                SeedWeatherSnapshot(
                    source = container.prefs.weatherSource.first(),
                    omCity = container.prefs.weatherOmCity.first(),
                    omHighC = container.prefs.weatherOmHighC.first(),
                    omDate = container.prefs.weatherOmDate.first(),
                ),
            ),
            fastingJson = container.prefs.fastingSession.first().let { session ->
                json.encodeToString(
                    SeedFastingSnapshot.serializer(),
                    SeedFastingSnapshot(
                        enabled = container.prefs.fastingEnabled.first(),
                        goalHours = container.prefs.fastingGoalHours.first(),
                        eatHours = container.prefs.fastingEatHours.first(),
                        goalNotificationEnabled = container.prefs.fastingGoalNotificationEnabled.first(),
                        autoWindows = container.prefs.fastingAutoWindows.first(),
                        startHour = container.prefs.fastingStartHour.first(),
                        startMinute = container.prefs.fastingStartMinute.first(),
                        startReminderEnabled = container.prefs.fastingStartReminderEnabled.first(),
                        startReminderLeadMinutes = container.prefs.fastingStartReminderLeadMinutes.first(),
                        endReminderLeadMinutes = container.prefs.fastingEndReminderLeadMinutes.first(),
                        sessionStartedAtMillis = session.startedAtMillis,
                        lastEndedAtMillis = session.lastEndedAtMillis,
                        lastFastStartedAtMillis = session.lastFastStartedAtMillis,
                        goalReachedNotified = session.goalReachedNotified,
                        autoStarted = session.autoStarted,
                    ),
                )
            },
        )
        container.prefs.setTestSeedBackupJson(json.encodeToString(SeedBackup.serializer(), backup))
    }

    suspend fun restore() {
        // Never reachable from release builds (extras gated upstream); belt-and-
        // suspenders so the real-diary restore primitive cannot run in a release APK.
        if (!BuildConfig.DEBUG) return
        val raw = container.prefs.testSeedBackupJson.first() ?: return
        val backup = runCatching {
            json.decodeFromString(SeedBackup.serializer(), raw)
        }.getOrNull() ?: return

        container.foodRepository.replaceAll(
            json.decodeFromString(ListSerializer(FoodEntry.serializer()), backup.entriesJson)
        )
        container.weightRepository.replaceAll(
            json.decodeFromString(ListSerializer(WeightEntry.serializer()), backup.weightsJson)
        )
        // bodyFatsJson is null for backups written before the field existed —
        // those predate any real body-fat history, so clearing is still right.
        container.bodyFatRepository.replaceAll(
            backup.bodyFatsJson?.let {
                json.decodeFromString(ListSerializer(BodyFatEntry.serializer()), it)
            } ?: emptyList()
        )
        // Same contract for scale composition rows.
        container.bodyCompositionRepository.replaceAll(
            backup.compositionsJson?.let {
                json.decodeFromString(ListSerializer(BodyCompositionEntry.serializer()), it)
            } ?: emptyList()
        )
        backup.debugActivityJson?.let {
            container.prefs.setDebugActivityDays(
                json.decodeFromString(ListSerializer(DebugActivityDay.serializer()), it)
            )
        } ?: container.prefs.clearDebugActivityDays()
        backup.waterJson?.let {
            container.prefs.setWaterEntries(
                json.decodeFromString(ListSerializer(WaterEntry.serializer()), it)
            )
        }
        backup.recipesJson?.let {
            container.prefs.setRecipes(
                json.decodeFromString(ListSerializer(Recipe.serializer()), it)
            )
        }
        backup.favoritesJson?.let {
            val favorites = json.decodeFromString(ListSerializer(FoodEntry.serializer()), it)
            container.prefs.setFavoriteFoodEntries(favorites)
            container.prefs.setFavoriteKeys(favorites.map { fav -> fav.favoriteKey }.toSet())
        }
        backup.chatJson?.let {
            container.chatRepository.replaceAll(
                json.decodeFromString(ListSerializer(ChatMessage.serializer()), it)
            )
        }
        backup.measurementsJson?.let {
            container.bodyMeasurementRepository.replaceAll(
                json.decodeFromString(ListSerializer(BodyMeasurement.serializer()), it)
            )
        }
        // Notes have no bulk setter (setNote/deleteNote only): diff the wanted
        // day set against what is stored. Skipped entirely when the backup
        // predates the field, so real notes are never deleted from an old blob.
        backup.notesJson?.let { raw ->
            val wanted = json.decodeFromString(ListSerializer(DailyNote.serializer()), raw)
                .associateBy { it.date }
            container.prefs.noteEntries.first()
                .filter { it.date !in wanted }
                .forEach { container.notesRepository.deleteNote(it.date) }
            wanted.values.forEach { container.notesRepository.setNote(it.date, it.text) }
        }
        backup.nicotineJson?.let {
            container.prefs.setNicotineEntries(
                json.decodeFromString(ListSerializer(NicotineEntry.serializer()), it)
            )
        }
        backup.caffeineJson?.let {
            container.prefs.setCaffeineEntries(
                json.decodeFromString(ListSerializer(CaffeineEntry.serializer()), it)
            )
        }
        backup.manualActiveJson?.let {
            container.prefs.setManualActiveEntries(
                json.decodeFromString(ListSerializer(ManualActiveEntry.serializer()), it)
            )
        }
        backup.goalJournalJson?.let {
            container.prefs.setGoalJournal(
                json.decodeFromString(ListSerializer(GoalJournalEntry.serializer()), it)
            )
        }
        backup.mealCatalogJson?.let {
            container.prefs.setMealCatalog(json.decodeFromString(MealCatalog.serializer(), it))
        }
        backup.untrackedJson?.let {
            val snapshot = json.decodeFromString(SeedUntrackedSnapshot.serializer(), it)
            container.prefs.setUntrackedDays(snapshot.days)
            container.prefs.setUntrackedKcalByDay(snapshot.kcalByDay)
        }
        backup.trackerPresetsJson?.let {
            val snapshot = json.decodeFromString(SeedTrackerSnapshot.serializer(), it)
            snapshot.caffeinePresets?.let { catalog -> container.prefs.setCaffeinePresets(catalog) }
            snapshot.nicotinePresets?.let { catalog -> container.prefs.setNicotinePresets(catalog) }
            snapshot.caffeineQuickKinds?.let { ids -> container.prefs.setCaffeineQuickKinds(ids) }
            snapshot.nicotineQuickKinds?.let { ids -> container.prefs.setNicotineQuickKinds(ids) }
            snapshot.waterQuickPresetsMl?.let { ml -> container.prefs.setWaterQuickPresetsMl(ml) }
        }
        backup.energyJson?.let {
            val snapshot = json.decodeFromString(SeedEnergySnapshot.serializer(), it)
            container.prefs.setHealthEnergyActiveByDay(snapshot.activeByDay)
            container.prefs.setHealthEnergyMeasuredActive(snapshot.measuredActive)
        }
        backup.weatherJson?.let {
            val snapshot = json.decodeFromString(SeedWeatherSnapshot.serializer(), it)
            snapshot.source?.let { source -> container.prefs.setWeatherSource(source) }
            container.prefs.setWeatherOmCity(snapshot.omCity)
            snapshot.omHighC?.let { highC -> container.prefs.setWeatherOmHighC(highC) }
            container.prefs.setWeatherOmDate(snapshot.omDate)
        }
        backup.fastingJson?.let {
            val snapshot = json.decodeFromString(SeedFastingSnapshot.serializer(), it)
            container.prefs.setFastingEnabled(snapshot.enabled)
            container.prefs.setFastingGoalHours(snapshot.goalHours)
            container.prefs.setFastingEatHours(snapshot.eatHours)
            container.prefs.setFastingGoalNotificationEnabled(snapshot.goalNotificationEnabled)
            container.prefs.setFastingAutoWindows(snapshot.autoWindows)
            container.prefs.setFastingStartHour(snapshot.startHour)
            container.prefs.setFastingStartMinute(snapshot.startMinute)
            container.prefs.setFastingStartReminderEnabled(snapshot.startReminderEnabled)
            container.prefs.setFastingStartReminderLeadMinutes(snapshot.startReminderLeadMinutes)
            container.prefs.setFastingEndReminderLeadMinutes(snapshot.endReminderLeadMinutes)
            container.prefs.setFastingSessionFields(
                startedAtMillis = snapshot.sessionStartedAtMillis,
                lastEndedAtMillis = snapshot.lastEndedAtMillis,
                lastFastStartedAtMillis = snapshot.lastFastStartedAtMillis,
                goalReachedNotified = snapshot.goalReachedNotified,
                autoStarted = snapshot.autoStarted,
            )
        }
        backup.settings?.let { settings ->
            container.prefs.setWaterTrackingEnabled(settings.waterTrackingEnabled)
            container.prefs.setWaterDynamicEnabled(settings.waterDynamicEnabled)
            container.prefs.setWaterFoodWaterEnabled(settings.waterFoodWaterEnabled)
            container.prefs.setWaterDailyGoalMl(settings.waterDailyGoalMl)
            container.prefs.setWaterReminderEnabled(settings.waterReminderEnabled)
            container.prefs.setNotificationsEnabled(settings.notificationsEnabled)
            container.prefs.setStreakReminderEnabled(settings.streakReminderEnabled)
            container.prefs.setDailySummaryEnabled(settings.dailySummaryEnabled)
            container.prefs.setCoachTabEnabled(settings.coachTabEnabled)
            container.prefs.setProgressMeasurementSites(settings.progressMeasurementSites)
            settings.optionalNutrientGoalsJson?.let { raw ->
                json.decodeFromString(OptionalNutrientGoals.serializer(), raw).let {
                    container.prefs.setOptionalNutrientGoals(it)
                }
            }
            container.prefs.setHomeShowSteps(settings.homeShowSteps)
            container.prefs.setHomeShowActiveCalories(settings.homeShowActiveCalories)
            settings.homeNutrientCardCount?.let { container.prefs.setHomeNutrientCardCount(it) }
            settings.homeTopNutrients?.let { container.prefs.setHomeTopNutrients(it) }
            settings.homeCalorieDisplayMode?.let { container.prefs.setHomeCalorieDisplayMode(it) }
            settings.dailyNotesEnabled?.let { container.prefs.setDailyNotesEnabled(it) }
            settings.mealPlanningEnabled?.let { container.prefs.setMealPlanningEnabled(it) }
            settings.foodLogMacroChips?.let { container.prefs.setFoodLogMacroChips(it) }
            settings.progressNutrientAverages?.let { container.prefs.setProgressNutrientAverages(it) }
            settings.progressNutrientAveragesSelection?.let {
                container.prefs.setProgressNutrientAveragesSelection(it)
            }
        }
        backup.profileJson?.let {
            container.profileRepository.save(json.decodeFromString(UserProfile.serializer(), it))
        }
        container.prefs.setHealthConnectEnabled(backup.healthConnectEnabled)
        container.prefs.setOnboardingCompleted(backup.onboarded)
        container.prefs.clearTestSeedBackup()
    }

    /**
     * Seeds the analysis queue + prompt history (Codeberg #53) with two pending
     * photo items, one finished analysis, one failed attempt, and (defect
     * shapes) a 10-photo row, a text-only row, a future-target row, a
     * future-timestamp row, a row naming its last attempt, a single-ingredient
     * row, and one row whose photo file is gone (placeholder thumbnail):
     *
     *   adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez seed_analysis_queue true
     */
    suspend fun seedAnalysisQueue() {
        val fixtures = SEED_PHOTO_ASSETS.mapNotNull { path ->
            runCatching { container.appContext.assets.open(path).use { it.readBytes() } }.getOrNull()
                ?.takeIf { it.isNotEmpty() }
        }
        val now = java.time.Instant.now()
        val pending1 = UUID.randomUUID()
        val pending2 = UUID.randomUUID()
        val done = UUID.randomUUID()
        val failed = UUID.randomUUID()
        val bulk = UUID.randomUUID()
        val textOnly = UUID.randomUUID()
        val futureTarget = UUID.randomUUID()
        val futureCreated = UUID.randomUUID()
        val attempted = UUID.randomUUID()
        val singleIngredient = UUID.randomUUID()
        val missingPhoto = UUID.randomUUID()
        container.analysisQueue.upsert(
            QueuedAnalysis(
                id = pending1,
                createdAt = now.minusSeconds(45 * 60),
                targetDate = LocalDate.now(),
                imageFilenames = container.analysisQueue.storeImages(
                    pending1, fixtures.take(1)
                ),
                note = "Oatmeal with banana and peanut butter",
                confirmedPortionGrams = 320.0,
                source = FoodSource.SNAP_FOOD,
                status = QueueStatus.PENDING,
            )
        )
        container.analysisQueue.upsert(
            QueuedAnalysis(
                id = pending2,
                createdAt = now.minusSeconds(3 * 3600),
                targetDate = LocalDate.now(),
                imageFilenames = container.analysisQueue.storeImages(
                    pending2, fixtures.drop(1).take(1)
                ),
                note = "Pizza slices + salad",
                source = FoodSource.SNAP_FOOD,
                status = QueueStatus.PENDING,
            )
        )
        container.analysisQueue.upsert(
            QueuedAnalysis(
                id = done,
                createdAt = now.minusSeconds(26 * 3600),
                targetDate = LocalDate.now().minusDays(1),
                imageFilenames = container.analysisQueue.storeImages(
                    done, fixtures.drop(2).take(1)
                ),
                note = "Burger combo",
                source = FoodSource.SNAP_FOOD,
                status = QueueStatus.DONE,
                result = app.chompass.services.ai.FoodAnalysis(
                    name = "Cheeseburger with fries",
                    calories = 780,
                    protein = 32.0,
                    carbs = 68.0,
                    fat = 41.0,
                    servingSizeGrams = 310.0,
                    emoji = "🍔",
                    customNote = "Burger combo",
                ),
            )
        )
        container.analysisQueue.upsert(
            QueuedAnalysis(
                id = failed,
                createdAt = now.minusSeconds(50 * 3600),
                targetDate = LocalDate.now().minusDays(2),
                note = "Greek yogurt bowl",
                source = FoodSource.TEXT_INPUT,
                status = QueueStatus.FAILED,
                error = "Analysis failed",
            )
        )
        // Defect shapes: the queue tolerates all of these and the sheet has to
        // render each (bulk badge, text-only row, future day, "missing photo"
        // placeholder, run-with bookkeeping, single-ingredient prompt).
        container.analysisQueue.upsert(
            QueuedAnalysis(
                id = bulk,
                createdAt = now.minusSeconds(20 * 60),
                targetDate = LocalDate.now(),
                imageFilenames = container.analysisQueue.storeImages(
                    bulk,
                    List(FoodPhotoSession.MAX_IMAGES) { fixtures[it % fixtures.size] },
                ),
                note = "Big batch cook (${FoodPhotoSession.MAX_IMAGES} photos)",
                confirmedPortionGrams = 480.0,
                source = FoodSource.SNAP_FOOD,
                status = QueueStatus.PENDING,
            )
        )
        container.analysisQueue.upsert(
            QueuedAnalysis(
                id = textOnly,
                createdAt = now.minusSeconds(35 * 60),
                targetDate = LocalDate.now(),
                note = "Text only, no photo attached",
                source = FoodSource.TEXT_INPUT,
                status = QueueStatus.PENDING,
            )
        )
        container.analysisQueue.upsert(
            QueuedAnalysis(
                id = futureTarget,
                createdAt = now.minusSeconds(8 * 3600),
                targetDate = LocalDate.now().plusDays(3),
                imageFilenames = container.analysisQueue.storeImages(
                    futureTarget, fixtures.take(1)
                ),
                note = "Planned dinner prep",
                source = FoodSource.SNAP_FOOD,
                status = QueueStatus.PENDING,
            )
        )
        container.analysisQueue.upsert(
            QueuedAnalysis(
                id = futureCreated,
                createdAt = now.plusSeconds(2 * 24 * 3600),
                targetDate = LocalDate.now(),
                note = "Clock skew from a sync import",
                source = FoodSource.TEXT_INPUT,
                status = QueueStatus.PENDING,
            )
        )
        container.analysisQueue.upsert(
            QueuedAnalysis(
                id = attempted,
                createdAt = now.minusSeconds(30 * 3600),
                targetDate = LocalDate.now().minusDays(1),
                imageFilenames = container.analysisQueue.storeImages(
                    attempted, fixtures.drop(2).take(1)
                ),
                note = "Ran with an explicit profile",
                source = FoodSource.SNAP_FOOD,
                status = QueueStatus.DONE,
                result = app.chompass.services.ai.FoodAnalysis(
                    name = "Seed bowl",
                    calories = 520,
                    protein = 30.0,
                    carbs = 48.0,
                    fat = 22.0,
                    servingSizeGrams = 300.0,
                ),
                lastAttemptProfile = "Seed Profile",
                lastAttemptModel = "seed-model",
            )
        )
        container.analysisQueue.upsert(
            QueuedAnalysis(
                id = singleIngredient,
                createdAt = now.minusSeconds(15 * 60),
                targetDate = LocalDate.now(),
                imageFilenames = container.analysisQueue.storeImages(
                    singleIngredient, fixtures.drop(1).take(1)
                ),
                note = "Single ingredient (progressive meal)",
                singleIngredient = true,
                source = FoodSource.SNAP_FOOD,
                status = QueueStatus.PENDING,
            )
        )
        container.analysisQueue.upsert(
            QueuedAnalysis(
                id = missingPhoto,
                createdAt = now.minusSeconds(9 * 3600),
                targetDate = LocalDate.now(),
                // Reference to a file the queue image dir does not contain:
                // the list row falls back to the placeholder thumbnail.
                imageFilenames = listOf("${missingPhoto}_0.jpg"),
                note = "Photo file pruned",
                source = FoodSource.SNAP_FOOD,
                status = QueueStatus.PENDING,
            )
        )
    }

    /**
     * Installs the stored-defect fixtures ([SampleDataDefects]) so every shape
     * the app tolerates is reachable on a device — no UI path can author most
     * of them. Groups run in [DefectGroup] declaration order, so a later group
     * sees the rows an earlier one wrote (e.g. METRICS' sparse window sits
     * inside DAYS' entry-free stretch).
     *
     * Reversible: snapshots first (like every other seeder), so
     * `restore_real_data` undoes a defect pass on a real diary.
     *
     *   adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez seed_defects true
     *   adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez seed_full true --ez defect_gaps true
     */
    internal suspend fun seedDefects(groups: Set<DefectGroup>) {
        if (!BuildConfig.DEBUG) return
        if (groups.isEmpty()) return
        snapshotRealDataIfNeeded()
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val fixtureIds = SampleDataDefects.fixtureIds(today)

        // Whole-day gaps first, so the rows this pass writes survive the clear
        // (the month-boundary fixture can fall inside the cleared window).
        if (DefectGroup.DAYS in groups) {
            val clearedDays = SampleDataDefects.entryFreeDayOffsets
                .map { today.minusDays(it) }
                .toSet()
            val removals = container.foodRepository.entries.first()
                .filter { row ->
                    row.id !in fixtureIds &&
                        LocalDate.from(row.timestamp.atZone(zone)) in clearedDays
                }
            if (removals.isNotEmpty()) {
                container.prefs.applyFoodEntryBucketChanges(
                    removalIdsByMonth = removals
                        .groupBy { YearMonth.from(it.timestamp.atZone(zone)) }
                        .mapValues { (_, rows) -> rows.map { it.id }.toSet() },
                )
            }
            container.prefs.setUntrackedDays(SampleDataDefects.untrackedDays(today))
            container.prefs.setUntrackedKcalByDay(SampleDataDefects.untrackedKcalByDay(today))
            val note = SampleDataDefects.noteOnlyNote(today)
            container.notesRepository.setNote(note.date, note.text)
        }

        val diary = mutableListOf<FoodEntry>()
        val favorites = mutableListOf<FoodEntry>()
        DefectGroup.entries.filter { it in groups }.forEach { group ->
            when (group) {
                DefectGroup.GAPS -> {
                    diary += SampleDataDefects.gapRows(today)
                    favorites += SampleDataDefects.gapFavorites(today)
                }
                DefectGroup.STALE_MICROS -> {
                    diary += SampleDataDefects.staleMicrosRows(today)
                    favorites += SampleDataDefects.staleMicrosFavorite(today)
                }
                DefectGroup.LEGACY -> {
                    diary += SampleDataDefects.legacyRows(today)
                    favorites += SampleDataDefects.legacyFavorite(today)
                    container.prefs.setRecipes(
                        container.prefs.recipes.first() + SampleDataDefects.legacyRecipe(today)
                    )
                    val (caffeine, nicotine) = SampleDataDefects.legacyTrackerEntries(today)
                    container.prefs.setCaffeineEntries(container.prefs.caffeineEntries.first() + caffeine)
                    container.prefs.setNicotineEntries(container.prefs.nicotineEntries.first() + nicotine)
                    // The all-null measurement row (same id the METRICS group uses).
                    container.prefs.applyMeasurementBucketChanges(
                        upsertsByMonth = byMonth(
                            SampleDataDefects.measurementDefectRows(today).filterNot { it.hasAnyValue },
                            zone,
                        ) { it.date },
                    )
                }
                DefectGroup.DAYS -> {
                    diary += SampleDataDefects.sharedFavoriteKeyRows(today)
                    diary += SampleDataDefects.futureRow(today)
                }
                DefectGroup.METRICS -> {
                    // Explicit 3-week reading gap: delete, never random skips.
                    val (from, to) = SampleDataDefects.readingGapRange(today)
                    container.weightRepository.entriesInRange(from, to)
                        .forEach { container.weightRepository.deleteEntry(it.id) }
                    container.bodyFatRepository.entriesInRange(from, to)
                        .forEach { container.bodyFatRepository.deleteEntry(it.id) }
                    val (duplicateWeights, duplicateFats) = SampleDataDefects.duplicateReadingRows(today)
                    val (absurdWeights, absurdFats) = SampleDataDefects.absurdReadingRows(today)
                    // Bucket façades (not the repositories): the profile's own
                    // weight / body-fat stay at the seeded values.
                    container.prefs.applyWeightBucketChanges(
                        upsertsByMonth = byMonth(duplicateWeights + absurdWeights, zone) { it.date },
                    )
                    container.prefs.applyBodyFatBucketChanges(
                        upsertsByMonth = byMonth(duplicateFats + absurdFats, zone) { it.date },
                    )
                    container.prefs.applyMeasurementBucketChanges(
                        upsertsByMonth = byMonth(SampleDataDefects.measurementDefectRows(today), zone) { it.date },
                    )
                    val defectDays = SampleDataDefects.activityDefectDays(today)
                    val existing = container.prefs.debugActivityDaysJson()?.let { raw ->
                        runCatching {
                            json.decodeFromString(ListSerializer(DebugActivityDay.serializer()), raw)
                        }.getOrNull()
                    }.orEmpty()
                    container.prefs.setDebugActivityDays(
                        existing.filterNot { day -> defectDays.any { it.date == day.date } } + defectDays
                    )
                }
                DefectGroup.TRACKERS -> {
                    val now = System.currentTimeMillis()
                    container.prefs.setFastingEnabled(true)
                    container.prefs.setFastingGoalHours(16)
                    container.prefs.setFastingEatHours(8)
                    container.prefs.setFastingGoalNotificationEnabled(true)
                    container.prefs.setFastingAutoWindows(true)
                    container.prefs.setFastingStartHour(20)
                    container.prefs.setFastingStartMinute(0)
                    // One running fast, already past its goal, auto-started, with
                    // the previous completed fast as the eating-window input.
                    container.prefs.setFastingSessionFields(
                        startedAtMillis = now - 20 * MILLIS_PER_HOUR,
                        lastEndedAtMillis = now - 20 * MILLIS_PER_HOUR,
                        lastFastStartedAtMillis = now - 38 * MILLIS_PER_HOUR,
                        goalReachedNotified = false,
                        autoStarted = true,
                    )
                    container.prefs.setCaffeineQuickKinds(emptyList())
                    container.prefs.setNicotineQuickKinds(emptyList())
                    container.prefs.setNicotineEntries(
                        container.prefs.nicotineEntries.first() + SampleDataDefects.nicotineDefectRows(today)
                    )
                    val linked = container.prefs.foodEntriesForMonth(YearMonth.now()).first()
                        .maxByOrNull { it.timestamp }?.id
                    container.prefs.setCaffeineEntries(
                        container.prefs.caffeineEntries.first() + SampleDataDefects.caffeineDefectRow(today, linked)
                    )
                    container.prefs.setWaterEntries(
                        container.prefs.waterEntries.first() + SampleDataDefects.waterDefectRows(today)
                    )
                }
            }
        }

        if (diary.isNotEmpty()) {
            container.prefs.applyFoodEntryBucketChanges(
                upsertsByMonth = byMonth(diary, zone) { it.timestamp },
            )
        }
        if (favorites.isNotEmpty()) {
            val addedIds = favorites.map { it.id }.toSet()
            val merged = container.prefs.favoriteFoodEntries.first().filterNot { it.id in addedIds } + favorites
            container.prefs.setFavoriteFoodEntries(merged)
            container.prefs.setFavoriteKeys(merged.map { it.favoriteKey }.toSet())
        }
    }

    /**
     * Debug-only profile variants that compose with `seed_full`. The birthday is
     * pinned so the Settings age row and the BMR demo age stay stable.
     *
     *   adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez seed_profile_variant gain
     */
    suspend fun seedProfileVariant(name: String) {
        if (!BuildConfig.DEBUG) return
        val variant = name.trim().lowercase()
        if (variant.isEmpty()) return
        snapshotRealDataIfNeeded()
        val base = container.profileRepository.profile.first() ?: return
        val birthday = Instant.parse("1992-06-15T00:00:00Z")
        container.profileRepository.save(
            when (variant) {
                "gain" -> base.copy(goal = WeightGoal.GAIN, weeklyChangeKg = 0.25, birthday = birthday)
                "maintain" -> base.copy(
                    goal = WeightGoal.MAINTAIN,
                    weeklyChangeKg = null,
                    goalWeightKg = null,
                    birthday = birthday,
                )
                "pinned" -> base.copy(
                    customCalories = 1_850,
                    customProtein = 150,
                    customCarbs = 180,
                    customFat = 60,
                    caloriesLocked = true,
                    lockedMacros = setOf(AutoBalanceMacro.PROTEIN),
                    birthday = birthday,
                )
                "female" -> base.copy(
                    gender = Gender.FEMALE,
                    useBodyFatInBMR = false,
                    proteinGramsPerKg = 2.2,
                    proteinTargetMode = ProteinTargetMode.G_PER_KG_TOTAL,
                    birthday = birthday,
                )
                // Unknown value: leave the seeder's profile alone.
                else -> return
            }
        )
    }

    /** Bucket stores key month files by the row's own date. */
    private fun <T> byMonth(items: List<T>, zone: ZoneId, date: (T) -> Instant): Map<YearMonth, List<T>> =
        items.groupBy { YearMonth.from(date(it).atZone(zone)) }

    private companion object {
        val SEED_PHOTO_ASSETS = listOf(
            "ondevice_llm/food_plate.jpg",
            "ondevice_llm/pizza_slices.jpg",
            "ondevice_llm/fast_food_combo.jpg",
        )

        /** Custom notes sprinkled over the enriched diary rows (every 4th). */
        val SEED_NOTES = listOf(
            "Meal-prepped on Sunday",
            "Ate out, portion estimated",
            "Shared the plate with the kid",
            "Skipped the sauce this time",
        )

        /** Fasting/seeding hour arithmetic. */
        const val MILLIS_PER_HOUR = 3_600_000L

        /** The seeded Open-Meteo city (valid [OmCity] JSON; no network needed). */
        val SEED_OM_CITY = OmCity(
            id = 2_957_159,
            name = "Seedville",
            country = "Germany",
            admin1 = "Seedland",
            latitude = 52.52,
            longitude = 13.405,
            timezone = "Europe/Berlin",
        )
    }

}

@Serializable
internal data class SeedBackup(
    val entriesJson: String,
    val weightsJson: String,
    val profileJson: String?,
    val healthConnectEnabled: Boolean,
    val onboarded: Boolean,
    // Added after BodyFatRepository shipped — null in older backups.
    val bodyFatsJson: String? = null,
    // Added with the scale-composition store — null in older backups.
    val compositionsJson: String? = null,
    val debugActivityJson: String? = null,
    val waterJson: String? = null,
    val recipesJson: String? = null,
    val favoritesJson: String? = null,
    val chatJson: String? = null,
    val measurementsJson: String? = null,
    val settings: SeedSettingsSnapshot? = null,
    // Added with the full coverage pass (seed_full writes these too, and the
    // defect seeders touch them) — null in older backups, so restore leaves the
    // real value untouched instead of clearing it.
    val notesJson: String? = null,
    val nicotineJson: String? = null,
    val caffeineJson: String? = null,
    val manualActiveJson: String? = null,
    val goalJournalJson: String? = null,
    val mealCatalogJson: String? = null,
    val untrackedJson: String? = null,
    val trackerPresetsJson: String? = null,
    val energyJson: String? = null,
    val weatherJson: String? = null,
    val fastingJson: String? = null,
)

/** Untracked-day marks: the day set plus the per-day "assumed kcal" map. */
@Serializable
internal data class SeedUntrackedSnapshot(
    val days: Set<String> = emptySet(),
    val kcalByDay: Map<String, Int> = emptyMap(),
)

/** Custom/renamed tracker presets + the hub quick-chip selections + water presets. */
@Serializable
internal data class SeedTrackerSnapshot(
    val caffeinePresets: HabitPresetCatalog? = null,
    val nicotinePresets: HabitPresetCatalog? = null,
    val caffeineQuickKinds: List<String>? = null,
    val nicotineQuickKinds: List<String>? = null,
    val waterQuickPresetsMl: List<Int>? = null,
)

/** Health-Connect energy cache (per-day active map + the single measured value). */
@Serializable
internal data class SeedEnergySnapshot(
    val activeByDay: Map<String, Int> = emptyMap(),
    val measuredActive: Int = 0,
)

/** Water dynamic-goal temperature cache (Open-Meteo) + its source selector. */
@Serializable
internal data class SeedWeatherSnapshot(
    val source: String? = null,
    val omCity: String? = null,
    val omHighC: Int? = null,
    val omDate: String? = null,
)

/** Optional intermittent-fasting timer: settings + the session scalars. */
@Serializable
internal data class SeedFastingSnapshot(
    val enabled: Boolean = false,
    val goalHours: Int = 0,
    val eatHours: Int = 0,
    val goalNotificationEnabled: Boolean = false,
    val autoWindows: Boolean = false,
    val startHour: Int = 0,
    val startMinute: Int = 0,
    val startReminderEnabled: Boolean = false,
    val startReminderLeadMinutes: Int = 0,
    val endReminderLeadMinutes: Int = 0,
    val sessionStartedAtMillis: Long? = null,
    val lastEndedAtMillis: Long? = null,
    val lastFastStartedAtMillis: Long? = null,
    val goalReachedNotified: Boolean = false,
    val autoStarted: Boolean = false,
)

@Serializable
internal data class SeedSettingsSnapshot(
    val waterTrackingEnabled: Boolean = false,
    val waterDynamicEnabled: Boolean = false,
    val waterFoodWaterEnabled: Boolean = false,
    val waterDailyGoalMl: Int = 2_000,
    val waterReminderEnabled: Boolean = false,
    val notificationsEnabled: Boolean = false,
    val streakReminderEnabled: Boolean = false,
    val dailySummaryEnabled: Boolean = false,
    val coachTabEnabled: Boolean = true,
    val progressMeasurementSites: Set<String> = emptySet(),
    val optionalNutrientGoalsJson: String? = null,
    val homeShowSteps: Boolean = false,
    val homeShowActiveCalories: Boolean = false,
    val homeTopNutrients: String? = null,
    val homeNutrientCardCount: Int? = null,
    val homeCalorieDisplayMode: String? = null,
    // Added with the full coverage pass. Nullable so an older blob leaves the
    // real value alone (the Boolean fields above predate that convention).
    val dailyNotesEnabled: Boolean? = null,
    val mealPlanningEnabled: Boolean? = null,
    val foodLogMacroChips: String? = null,
    val progressNutrientAverages: Boolean? = null,
    val progressNutrientAveragesSelection: Set<String>? = null,
)
