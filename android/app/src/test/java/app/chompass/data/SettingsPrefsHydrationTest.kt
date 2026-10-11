package app.chompass.data

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import app.chompass.models.AiProfile
import app.chompass.models.AIProvider
import app.chompass.models.HomeTopNutrient
import app.chompass.models.OptionalNutrientGoals
import app.chompass.models.SpeechProvider
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

class SettingsPrefsHydrationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun emptySnapshot_matchesFactoryDefaults() {
        val snap = emptyPreferences().toSettingsHydration(json)
        assertEquals(AIProvider.GEMINI, snap.selectedAI)
        assertEquals(SpeechProvider.NATIVE, snap.selectedSpeech)
        assertEquals("cm", snap.heightUnit)
        assertEquals("kg", snap.weightUnit)
        assertFalse(snap.notificationsEnabled)
        assertFalse(snap.dailySummaryEnabled)
        assertEquals(21, snap.dailySummaryHour)
        assertEquals(0, snap.dailySummaryMinute)
        assertFalse(snap.waterTrackingEnabled)
        assertEquals(2_000, snap.waterDailyGoalMl)
        assertTrue(snap.coachTabEnabled)
        assertTrue(snap.aiFeaturesEnabled)
        assertEquals("system", snap.appearanceMode)
        assertEquals("1W", snap.progressDefaultRangeId)
        assertEquals(emptySet<String>(), snap.progressMeasurementSites)
        assertFalse(snap.progressNutrientAverages)

        assertEquals(OptionalNutrientGoals.Default, snap.optionalNutrientGoals)
        assertEquals(HomeTopNutrient.DefaultSelection, snap.homeDisplay.homeTopNutrients)
        assertFalse(snap.homeDisplay.showSteps)
        assertTrue(snap.mealConstituentsEnabled)
        assertTrue(snap.mealTimesEnabled)
        // All three search sources ship on; OFF is the one that also has a
        // switch on the Add Food heading.
        assertTrue(snap.foodSearchOpenFoodFactsEnabled)
        assertTrue(snap.foodSearchUsdaEnabled)
        assertTrue(snap.foodSearchSwissEnabled)
        // Fasting ships ready: popular 16:8 windows + both reminders + auto cycle.
        assertEquals(16, snap.fastingGoalHours)
        assertEquals(8, snap.fastingEatHours)
        assertTrue(snap.fastingAutoWindows)
        assertTrue(snap.fastingGoalNotificationEnabled)
        assertTrue(snap.fastingStartReminderEnabled)
        assertEquals("", snap.customBaseUrl)
    }

    @Test
    fun coldStartSnapshot_matchesFactoryDefaults() {
        val snap = emptyPreferences().toColdStartPrefs()
        assertFalse(snap.onboarded)
        assertEquals("system", snap.appearanceMode)
        assertEquals("", snap.appLanguage)
        assertFalse(snap.fixedLauncherIcon)
    }

    // WS5: the legacy tracker limit (caffeineDailyLimitMg) is an alias of the
    // optional caffeine goal. The read-side merge mirrors
    // PreferencesStore.migrateCaffeineDailyLimitIfNeeded().

    @Test
    fun legacyCaffeineLimit_customized_migratesIntoDefaultGoal() {
        val snap = preferencesOf(Keys.CAFFEINE_DAILY_LIMIT_MG to 300).toSettingsHydration(json)
        assertEquals(300, snap.optionalNutrientGoals.caffeine)
    }

    @Test
    fun legacyCaffeineLimit_ignoredWhenGoalAlreadyCustomized() {
        val prefs = preferencesOf(
            Keys.CAFFEINE_DAILY_LIMIT_MG to 300,
            Keys.OPTIONAL_NUTRIENT_GOALS to json.encodeToString(
                OptionalNutrientGoals.serializer(),
                OptionalNutrientGoals.Default.copy(caffeine = 500),
            ),
        )
        val snap = prefs.toSettingsHydration(json)
        assertEquals(500, snap.optionalNutrientGoals.caffeine)
    }

    @Test
    fun legacyCaffeineLimit_default_leavesDefaultGoal() {
        val snap = preferencesOf(Keys.CAFFEINE_DAILY_LIMIT_MG to 400).toSettingsHydration(json)
        assertEquals(OptionalNutrientGoals.Default, snap.optionalNutrientGoals)
    }

    @Test
    fun customBaseUrl_hydratesForSelectedAndFallbackProviders() {
        val ollama = AiProfile(
            id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            name = "Ollama",
            provider = AIProvider.OLLAMA,
            baseUrl = "http://192.168.1.10:11434",
            createdAt = Instant.EPOCH,
        )
        val custom = AiProfile(
            id = UUID.fromString("22222222-2222-2222-2222-222222222222"),
            name = "Custom fallback",
            provider = AIProvider.CUSTOM_OPENAI,
            baseUrl = "https://example.com/v1",
            createdAt = Instant.EPOCH,
        )
        val prefs = preferencesOf(
            Keys.AI_PROFILES_JSON to json.encodeToString(
                ListSerializer(AiProfile.serializer()),
                listOf(ollama, custom),
            ),
            Keys.ACTIVE_AI_PROFILE_ID to ollama.id.toString(),
            Keys.FALLBACK_AI_PROFILE_ID to custom.id.toString(),
        )
        val snap = prefs.toSettingsHydration(json)
        assertEquals(AIProvider.OLLAMA, snap.selectedAI)
        assertEquals("http://192.168.1.10:11434", snap.customBaseUrl)
    }
}
