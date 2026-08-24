package app.chompass.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import app.chompass.R

class SettingsIndexTest {
    private fun entry(labelRes: Int, keywords: List<String>) =
        SettingsIndexEntry(
            groupRes = R.string.settings_section_goals,
            labelRes = labelRes,
            keywords = keywords,
            route = "settings/goals",
            icon = Icons.Filled.Check,
        )

    @Test
    fun `matches on the folded label`() {
        val e = entry(R.string.settings_water_goal, emptyList())
        // The fold is applied to both sides; "Wasser" folds to "wasser".
        assertTrue(settingsSearchMatches(e, "Wasser", "wasser"))
        assertTrue(settingsSearchMatches(e, "Daily water goal", "water"))
    }

    @Test
    fun `matches on keywords even when the label does not contain the term`() {
        val e = entry(R.string.settings_water_goal, listOf("hydration", "ml"))
        assertTrue(settingsSearchMatches(e, "Daily water goal", "hydration"))
        assertTrue(settingsSearchMatches(e, "Daily water goal", "ml"))
    }

    @Test
    fun `no match returns false and blank queries never match`() {
        val e = entry(R.string.settings_water_goal, listOf("hydration"))
        assertFalse(settingsSearchMatches(e, "Daily water goal", "keto"))
        assertFalse(settingsSearchMatches(e, "Daily water goal", " "))
        assertFalse(settingsSearchMatches(e, "Daily water goal", ""))
    }

    @Test
    fun `folded matcher agrees with the per-entry matcher`() {
        val label = "Daily water goal"
        val e = entry(R.string.settings_water_goal, listOf("hydration", "ml"))
        val folded = foldSettingsEntry(e, label)
        val queries = listOf("water", "Wasser", "hydration", "ML", "goal", "keto", " ", "")
        for (q in queries) {
            assertEquals(
                "folded vs per-entry mismatch for query '$q'",
                settingsSearchMatches(e, label, q),
                settingsSearchMatchesFolded(folded, q),
            )
        }
    }

    @Test
    fun `diacritics fold consistently on both sides`() {
        // é decomposes; ß maps to ss so "grosse" matches "Größe".
        assertTrue(settingsSearchFold("Größe") == settingsSearchFold("grosse"))
        assertTrue(settingsSearchFold("Café") == settingsSearchFold("cafe"))
    }

    @Test
    fun `index covers the expected settings vocabulary`() {
        // F1 success bar: the words a user would plausibly type resolve to at
        // least one entry each. Labels resolve via stringResource at runtime, so
        // here we check keyword coverage for the vocabulary instead.
        val queries = listOf(
            "units", "imperial", "height", "weight", "body fat", "birthday",
            "diet", "keto", "calories", "protein", "carbs", "fat", "fiber",
            "recalculate", "formula", "serving", "grams", "meal", "photo",
            "dark", "theme", "language", "week", "progress", "water", "nicotine",
            "caffeine", "journal", "reminders", "speech", "voice", "api key", "model", "fallback",
            "health connect", "export", "import", "backup", "webdav", "delete",
        )
        for (q in queries) {
            val folded = settingsSearchFold(q)
            val hit = SETTINGS_INDEX.any { e ->
                e.keywords.any { settingsSearchFold(it) == folded } ||
                    settingsSearchFold(e.route).contains(folded)
            }
            assertTrue("no index entry covers query '$q'", hit)
        }
    }

    @Test
    fun `every entry has a label, keywords and a route`() {
        for (e in SETTINGS_INDEX) {
            assertTrue("empty keywords for labelRes ${e.labelRes}", e.keywords.isNotEmpty())
            assertTrue("blank route for labelRes ${e.labelRes}", e.route.isNotBlank())
        }
    }
}
