package app.chompass.data

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import app.chompass.models.AIProvider
import app.chompass.models.HomeTopNutrient
import app.chompass.models.OptionalNutrientGoals
import app.chompass.models.SpeechProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
        // Fasting ships ready: popular 16:8 windows + both reminders + auto cycle.
        assertEquals(16, snap.fastingGoalHours)
        assertEquals(8, snap.fastingEatHours)
        assertTrue(snap.fastingAutoWindows)
        assertTrue(snap.fastingGoalNotificationEnabled)
        assertTrue(snap.fastingStartReminderEnabled)
        assertEquals("", snap.customBaseUrl)
        assertEquals("", snap.fallbackCustomBaseUrl)
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
        val prefs = preferencesOf(
            Keys.SELECTED_AI_PROVIDER to AIProvider.OLLAMA.name,
            Keys.FALLBACK_PROVIDER to AIProvider.CUSTOM_OPENAI.name,
            Keys.customBaseUrl(AIProvider.OLLAMA) to "http://192.168.1.10:11434",
            Keys.fallbackCustomBaseUrl(AIProvider.CUSTOM_OPENAI) to "https://example.com/v1",
        )
        val snap = prefs.toSettingsHydration(json)
        assertEquals("http://192.168.1.10:11434", snap.customBaseUrl)
        assertEquals("https://example.com/v1", snap.fallbackCustomBaseUrl)
    }
}
