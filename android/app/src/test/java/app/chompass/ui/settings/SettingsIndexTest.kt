package app.chompass.ui.settings

import android.app.Application
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import app.chompass.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SettingsIndexTest {
    private val context get() = RuntimeEnvironment.getApplication()

    private fun entry(keywordsRes: Int) =
        SettingsIndexEntry(
            groupRes = R.string.settings_section_goals,
            labelRes = R.string.settings_water_goal,
            keywordsRes = keywordsRes,
            route = "settings/goals",
            icon = Icons.Filled.Check,
        )

    private fun folded(
        entry: SettingsIndexEntry,
        label: String,
        group: String = "Goals & Nutrition",
        keywords: List<String> = emptyList(),
    ) = foldSettingsEntry(entry, label = label, group = group, keywords = keywords)

    private fun matches(
        entry: SettingsIndexEntry,
        label: String,
        query: String,
        group: String = "Goals & Nutrition",
        keywords: List<String> = emptyList(),
    ) = settingsSearchMatchesFolded(folded(entry, label, group, keywords), settingsSearchFold(query))

    @Test
    fun `matches on the folded label`() {
        val e = entry(R.array.settings_search_kw_water)
        // The fold is applied to both sides; "Wasser" folds to "wasser".
        assertTrue(matches(e, "Wasser", "wasser"))
        assertTrue(matches(e, "Daily water goal", "water"))
    }

    @Test
    fun `matches on keywords even when the label does not contain the term`() {
        val e = entry(R.array.settings_search_kw_water)
        assertTrue(matches(e, "Daily water goal", "hydration", keywords = listOf("hydration", "ml")))
        assertTrue(matches(e, "Daily water goal", "ml", keywords = listOf("hydration", "ml")))
    }

    @Test
    fun `matches on the localized group label`() {
        // Groups are already translated in every locale, so a query for a
        // section name must resolve without any keyword entries.
        val e = entry(R.array.settings_search_kw_water)
        assertTrue(matches(e, "Daily water goal", "trackers", group = "Trackers & Reminders"))
        assertTrue(matches(e, "Daily water goal", "ziele", group = "Ziele & Ernährung"))
        assertFalse(matches(e, "Daily water goal", "unrelated", group = "Trackers & Reminders"))
    }

    @Test
    fun `no match returns false and blank queries never match`() {
        val e = entry(R.array.settings_search_kw_water)
        assertFalse(matches(e, "Daily water goal", "keto", keywords = listOf("hydration")))
        assertFalse(matches(e, "Daily water goal", " "))
        assertFalse(matches(e, "Daily water goal", ""))
    }

    @Test
    fun `diacritics fold consistently on both sides`() {
        // é decomposes; ß maps to ss so "grosse" matches "Größe".
        assertTrue(settingsSearchFold("Größe") == settingsSearchFold("grosse"))
        assertTrue(settingsSearchFold("Café") == settingsSearchFold("cafe"))
    }

    @Test
    fun `case folding is locale-stable (Turkish I would break matching)`() {
        // Locale.getDefault() in Turkish lowercases I -> ı, which would fold
        // "Imperial"/"API" to "ımperial"/"apı" and miss the keyword folds.
        assertTrue(settingsSearchFold("Imperial") == settingsSearchFold("imperial"))
        assertTrue(settingsSearchFold("API") == "api")
    }

    @Test
    fun `index covers the expected settings vocabulary`() {
        // F1 success bar: the words a user would plausibly type resolve to at
        // least one entry each. Runs through the real resource arrays so a
        // missing/misnamed keyword array fails here (not just at runtime).
        val resolved = resolveSettingsIndex(context)
        val queries = listOf(
            "units", "imperial", "height", "weight", "body fat", "birthday",
            "diet", "keto", "calories", "protein", "carbs", "fat", "fiber",
            "recalculate", "formula", "serving", "grams", "meal", "photo",
            "dark", "theme", "language", "week", "progress", "water", "nicotine",
            "caffeine", "journal", "reminders", "speech", "voice", "api key", "model", "fallback",
            "health connect", "export", "import", "backup", "webdav", "delete",
        )
        for (q in queries) {
            val foldedQuery = settingsSearchFold(q)
            val hit = resolved.any { settingsSearchMatchesFolded(it, foldedQuery) }
            assertTrue("no index entry covers query '$q'", hit)
        }
    }

    @Test
    fun `every entry has a label, keyword array and a route`() {
        val resolved = resolveSettingsIndex(context)
        assertEquals("index size mismatch", SETTINGS_INDEX.size, resolved.size)
        for ((index, folded) in resolved.withIndex()) {
            assertTrue("empty keyword array for labelRes ${folded.entry.labelRes}", folded.keywordFolds.isNotEmpty())
            assertTrue("blank route for labelRes ${folded.entry.labelRes}", folded.entry.route.isNotBlank())
            assertTrue("blank group for index $index", folded.groupFold.isNotBlank())
        }
    }
}
