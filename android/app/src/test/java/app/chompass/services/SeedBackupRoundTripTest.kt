package app.chompass.services

import app.chompass.models.CaffeineEntry
import app.chompass.models.ChatMessage
import app.chompass.models.DailyNote
import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.GoalJournalEntry
import app.chompass.models.GoalJournalSource
import app.chompass.models.ManualActiveEntry
import app.chompass.models.MealCatalog
import app.chompass.models.MealType
import app.chompass.models.NicotineEntry
import app.chompass.models.OptionalNutrientGoals
import app.chompass.models.WaterEntry
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SeedBackupRoundTripTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val today = LocalDate.of(2026, 8, 19)

    @Test
    fun newFieldsSurviveEncodeDecode() {
        val water = listOf(WaterEntry(date = Instant.parse("2026-08-19T08:00:00Z"), milliliters = 300))
        val chat = SampleDataGenerators.sampleChat(now = Instant.parse("2026-08-19T12:00:00Z"))
        val favorites = listOf(
            FoodEntry(
                name = "Oats",
                calories = 150,
                protein = 5.0,
                carbs = 27.0,
                fat = 3.0,
                timestamp = Instant.parse("2026-08-18T08:00:00Z"),
                source = FoodSource.TEXT_INPUT,
                mealType = MealType.BREAKFAST.id,
            )
        )
        val recipes = SampleDataGenerators.sampleRecipes(now = Instant.parse("2026-08-19T12:00:00Z"))
        val backup = SeedBackup(
            entriesJson = "[]",
            weightsJson = "[]",
            profileJson = null,
            healthConnectEnabled = false,
            onboarded = true,
            waterJson = json.encodeToString(ListSerializer(WaterEntry.serializer()), water),
            recipesJson = json.encodeToString(ListSerializer(app.chompass.models.Recipe.serializer()), recipes),
            favoritesJson = json.encodeToString(ListSerializer(FoodEntry.serializer()), favorites),
            chatJson = json.encodeToString(ListSerializer(ChatMessage.serializer()), chat),
            measurementsJson = "[]",
            settings = SeedSettingsSnapshot(
                waterTrackingEnabled = true,
                waterDynamicEnabled = true,
                notificationsEnabled = true,
                progressMeasurementSites = setOf("waist", "hips"),
                optionalNutrientGoalsJson = json.encodeToString(
                    OptionalNutrientGoals.serializer(),
                    OptionalNutrientGoals.Default.copy(fiber = 30),
                ),
                homeShowSteps = true,
                dailyNotesEnabled = true,
                mealPlanningEnabled = true,
                foodLogMacroChips = "protein,carbs",
                progressNutrientAverages = true,
                progressNutrientAveragesSelection = setOf("fiber", "sugar"),
            ),
            notesJson = json.encodeToString(
                ListSerializer(DailyNote.serializer()),
                SampleDataGenerators.sampleNotes(today),
            ),
            nicotineJson = json.encodeToString(
                ListSerializer(NicotineEntry.serializer()),
                SampleDataGenerators.nicotineEntries(totalDays = 3, today = today),
            ),
            caffeineJson = json.encodeToString(
                ListSerializer(CaffeineEntry.serializer()),
                SampleDataGenerators.caffeineEntries(totalDays = 3, today = today),
            ),
            manualActiveJson = json.encodeToString(
                ListSerializer(ManualActiveEntry.serializer()),
                listOf(ManualActiveEntry.forDay(today, "Walk", 180)),
            ),
            goalJournalJson = json.encodeToString(
                ListSerializer(GoalJournalEntry.serializer()),
                listOf(
                    GoalJournalEntry(
                        date = today.toString(),
                        calories = 2_100,
                        proteinG = 150,
                        carbsG = 220,
                        fatG = 70,
                        profileName = "Training day",
                        updatedAtMillis = 1_700_000_000_000L,
                        source = GoalJournalSource.MANUAL_SWITCH,
                    )
                ),
            ),
            mealCatalogJson = json.encodeToString(
                MealCatalog.serializer(),
                SampleDataGenerators.sampleMealCatalog(),
            ),
            untrackedJson = json.encodeToString(
                SeedUntrackedSnapshot.serializer(),
                SeedUntrackedSnapshot(
                    days = setOf(today.minusDays(3).toString()),
                    kcalByDay = mapOf(today.minusDays(3).toString() to 350),
                ),
            ),
            trackerPresetsJson = json.encodeToString(
                SeedTrackerSnapshot.serializer(),
                SeedTrackerSnapshot(
                    caffeinePresets = SampleDataGenerators.trackerPresets().first,
                    nicotinePresets = SampleDataGenerators.trackerPresets().second,
                    caffeineQuickKinds = listOf("coffee", SampleDataGenerators.SEED_CAFFEINE_PRESET_ID),
                    nicotineQuickKinds = emptyList(),
                    waterQuickPresetsMl = listOf(200, 330, 500, 750),
                ),
            ),
            energyJson = json.encodeToString(
                SeedEnergySnapshot.serializer(),
                SeedEnergySnapshot(
                    activeByDay = mapOf(today.toString() to 480),
                    measuredActive = 512,
                ),
            ),
            weatherJson = json.encodeToString(
                SeedWeatherSnapshot.serializer(),
                SeedWeatherSnapshot(
                    source = "openmeteo",
                    omCity = """{"id":2957159,"name":"Seedville","latitude":52.52,"longitude":13.405,"timezone":"Europe/Berlin"}""",
                    omHighC = 31,
                    omDate = today.toString(),
                ),
            ),
            fastingJson = json.encodeToString(
                SeedFastingSnapshot.serializer(),
                SeedFastingSnapshot(
                    enabled = true,
                    goalHours = 16,
                    eatHours = 8,
                    goalNotificationEnabled = true,
                    autoWindows = true,
                    startHour = 20,
                    startMinute = 30,
                    startReminderEnabled = true,
                    startReminderLeadMinutes = 15,
                    endReminderLeadMinutes = 20,
                    sessionStartedAtMillis = 1_700_000_000_000L,
                    lastEndedAtMillis = 1_699_900_000_000L,
                    lastFastStartedAtMillis = 1_699_800_000_000L,
                    goalReachedNotified = true,
                    autoStarted = true,
                ),
            ),
        )
        val decoded = json.decodeFromString(SeedBackup.serializer(), json.encodeToString(SeedBackup.serializer(), backup))
        assertEquals(true, decoded.settings?.waterTrackingEnabled)
        assertEquals(setOf("waist", "hips"), decoded.settings?.progressMeasurementSites)
        assertEquals(true, decoded.settings?.dailyNotesEnabled)
        assertEquals(true, decoded.settings?.mealPlanningEnabled)
        assertEquals("protein,carbs", decoded.settings?.foodLogMacroChips)
        assertEquals(true, decoded.settings?.progressNutrientAverages)
        assertEquals(setOf("fiber", "sugar"), decoded.settings?.progressNutrientAveragesSelection)
        assertEquals(1, json.decodeFromString(ListSerializer(WaterEntry.serializer()), decoded.waterJson!!).size)
        assertEquals(4, json.decodeFromString(ListSerializer(app.chompass.models.Recipe.serializer()), decoded.recipesJson!!).size)
        assertEquals("oats", json.decodeFromString(ListSerializer(FoodEntry.serializer()), decoded.favoritesJson!!).single().favoriteKey)
        assertEquals(16, json.decodeFromString(ListSerializer(ChatMessage.serializer()), decoded.chatJson!!).size)

        val notes = json.decodeFromString(ListSerializer(DailyNote.serializer()), decoded.notesJson!!)
        assertEquals(3, notes.size)
        assertEquals(DailyNote.idFor(today), notes.first().id)
        assertEquals(
            true,
            json.decodeFromString(ListSerializer(NicotineEntry.serializer()), decoded.nicotineJson!!).isNotEmpty(),
        )
        assertTrue(
            json.decodeFromString(ListSerializer(CaffeineEntry.serializer()), decoded.caffeineJson!!).isNotEmpty()
        )
        assertEquals(
            180,
            json.decodeFromString(ListSerializer(ManualActiveEntry.serializer()), decoded.manualActiveJson!!)
                .single().calories,
        )
        assertEquals(
            GoalJournalSource.MANUAL_SWITCH,
            json.decodeFromString(ListSerializer(GoalJournalEntry.serializer()), decoded.goalJournalJson!!)
                .single().source,
        )
        val catalog = json.decodeFromString(MealCatalog.serializer(), decoded.mealCatalogJson!!)
        assertTrue(catalog.def(SampleDataGenerators.SEED_CUSTOM_MEAL_ID) != null)
        val untracked = json.decodeFromString(SeedUntrackedSnapshot.serializer(), decoded.untrackedJson!!)
        assertEquals(setOf(today.minusDays(3).toString()), untracked.days)
        assertEquals(350, untracked.kcalByDay.getValue(today.minusDays(3).toString()))
        val presets = json.decodeFromString(SeedTrackerSnapshot.serializer(), decoded.trackerPresetsJson!!)
        assertEquals(listOf(200, 330, 500, 750), presets.waterQuickPresetsMl)
        assertEquals(SampleDataGenerators.SEED_CAFFEINE_PRESET_ID, presets.caffeinePresets!!.presets.last().id)
        assertEquals(emptyList<String>(), presets.nicotineQuickKinds)
        val energy = json.decodeFromString(SeedEnergySnapshot.serializer(), decoded.energyJson!!)
        assertEquals(512, energy.measuredActive)
        assertEquals(480, energy.activeByDay.getValue(today.toString()))
        val weather = json.decodeFromString(SeedWeatherSnapshot.serializer(), decoded.weatherJson!!)
        assertEquals("openmeteo", weather.source)
        assertEquals(31, weather.omHighC)
        val fasting = json.decodeFromString(SeedFastingSnapshot.serializer(), decoded.fastingJson!!)
        assertEquals(true, fasting.enabled)
        assertEquals(16, fasting.goalHours)
        assertEquals(1_700_000_000_000L, fasting.sessionStartedAtMillis)
        assertEquals(true, fasting.autoStarted)
    }

    @Test
    fun olderBackupWithoutNewFieldsStillDecodes() {
        val raw = """
            {"entriesJson":"[]","weightsJson":"[]","profileJson":null,
             "healthConnectEnabled":true,"onboarded":false}
        """.trimIndent()
        val decoded = json.decodeFromString(SeedBackup.serializer(), raw)
        assertNull(decoded.waterJson)
        assertNull(decoded.recipesJson)
        assertNull(decoded.favoritesJson)
        assertNull(decoded.chatJson)
        assertNull(decoded.settings)
        assertNull(decoded.notesJson)
        assertNull(decoded.nicotineJson)
        assertNull(decoded.caffeineJson)
        assertNull(decoded.manualActiveJson)
        assertNull(decoded.goalJournalJson)
        assertNull(decoded.mealCatalogJson)
        assertNull(decoded.untrackedJson)
        assertNull(decoded.trackerPresetsJson)
        assertNull(decoded.energyJson)
        assertNull(decoded.weatherJson)
        assertNull(decoded.fastingJson)
        assertEquals(true, decoded.healthConnectEnabled)
    }
}
