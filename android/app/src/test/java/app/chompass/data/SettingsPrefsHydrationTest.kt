package app.chompass.data

import androidx.datastore.preferences.core.emptyPreferences
import app.chompass.models.AIProvider
import app.chompass.models.HomeTopNutrient
import app.chompass.models.OptionalNutrientGoals
import app.chompass.models.SpeechProvider
import kotlinx.serialization.json.Json
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
        assertFalse(snap.waterTrackingEnabled)
        assertEquals(2_000, snap.waterDailyGoalMl)
        assertTrue(snap.coachTabEnabled)
        assertTrue(snap.aiFeaturesEnabled)
        assertEquals("system", snap.appearanceMode)
        assertEquals("1W", snap.progressDefaultRangeId)
        assertEquals(emptySet<String>(), snap.progressMeasurementSites)
        assertEquals(OptionalNutrientGoals.Default, snap.optionalNutrientGoals)
        assertEquals(HomeTopNutrient.DefaultSelection, snap.homeDisplay.homeTopNutrients)
        assertFalse(snap.homeDisplay.showSteps)
        assertTrue(snap.portionClarifyEnabled)
        assertTrue(snap.mealConstituentsEnabled)
    }

    @Test
    fun coldStartSnapshot_matchesFactoryDefaults() {
        val snap = emptyPreferences().toColdStartPrefs()
        assertFalse(snap.onboarded)
        assertEquals("system", snap.appearanceMode)
        assertEquals("", snap.appLanguage)
        assertFalse(snap.fixedLauncherIcon)
    }
}
