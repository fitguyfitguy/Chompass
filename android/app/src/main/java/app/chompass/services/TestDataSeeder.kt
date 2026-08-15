package app.chompass.services

import app.chompass.AppContainer
import app.chompass.models.BodyFatEntry
import app.chompass.models.DietMode
import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.KetoCarbMode
import app.chompass.models.HomeCalorieDisplayMode
import app.chompass.models.MealType
import app.chompass.models.UserProfile
import app.chompass.models.WeightEntry
import app.chompass.services.health.DebugActivityDay
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Dev-only helper that swaps the user's real data for a year of synthetic food + weight
 * entries so the Progress tab can be eyeballed end-to-end. Triggered by launch flags from
 * MainActivity:
 *   adb shell am start -n app.chompass/.MainActivity --ez seed_test_data true
 *   adb shell am start -n app.chompass/.MainActivity --ez restore_real_data true
 *
 * `seed` snapshots the live state into a single backup blob, disables Health Connect so the
 * synthetic entries can't sync upstream, then writes 365 days of food + weights + body fat +
 * debug steps/active-calorie burn for the home activity cards.
 * `restore` puts everything back exactly as it was.
 */
class TestDataSeeder(private val container: AppContainer) {
    private val json = Json { ignoreUnknownKeys = true }

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
                goalBodyFatPercentage = baseProfile.goalBodyFatPercentage ?: 0.15
            )
        )
        container.prefs.setOnboardingCompleted(true)
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
                mealType = MealType.SNACK,
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
    }

    /** Snapshot the user's real data into the backup blob — first seed run only.
     *  Re-seeds (e.g. switching from the 30-day to the 2-year dataset) must not
     *  overwrite the original backup, or restore would put seed data back. */
    private suspend fun snapshotRealDataIfNeeded() {
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
            profileJson = container.profileRepository.profile.first()?.let {
                json.encodeToString(UserProfile.serializer(), it)
            },
            healthConnectEnabled = container.prefs.healthConnectEnabled.first(),
            onboarded = container.prefs.hasCompletedOnboarding.first(),
            debugActivityJson = container.prefs.debugActivityDaysJson(),
        )
        container.prefs.setTestSeedBackupJson(json.encodeToString(SeedBackup.serializer(), backup))
    }

    suspend fun restore() {
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
        backup.debugActivityJson?.let {
            container.prefs.setDebugActivityDays(
                json.decodeFromString(ListSerializer(DebugActivityDay.serializer()), it)
            )
        } ?: container.prefs.clearDebugActivityDays()
        backup.profileJson?.let {
            container.profileRepository.save(json.decodeFromString(UserProfile.serializer(), it))
        }
        container.prefs.setHealthConnectEnabled(backup.healthConnectEnabled)
        container.prefs.setOnboardingCompleted(backup.onboarded)
        container.prefs.clearTestSeedBackup()
    }

}

@Serializable
private data class SeedBackup(
    val entriesJson: String,
    val weightsJson: String,
    val profileJson: String?,
    val healthConnectEnabled: Boolean,
    val onboarded: Boolean,
    // Added after BodyFatRepository shipped — null in older backups.
    val bodyFatsJson: String? = null,
    val debugActivityJson: String? = null,
)
