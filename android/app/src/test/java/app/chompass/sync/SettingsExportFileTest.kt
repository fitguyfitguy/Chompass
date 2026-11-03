package app.chompass.sync

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Codeberg #117: settings export builder — bucketing, exclusion, stable JSON. */
class SettingsExportFileTest {
    @Test
    fun splitBucketsMixedSampleByTypeSortsKeysAndDropsCredentialNames() {
        val buckets = SettingsExportFile.split(
            mapOf(
                Pair(stringPreferencesKey("zeta"), "last"),
                Pair(stringPreferencesKey("alpha"), "first"),
                Pair(booleanPreferencesKey("flag"), true),
                Pair(intPreferencesKey("count"), 3),
                Pair(longPreferencesKey("bigCount"), 9_000_000_000L),
                Pair(doublePreferencesKey("ratio"), 0.5),
                // Credential-named keys of every type must never export.
                Pair(stringPreferencesKey("anthropicApiKey"), "sk-secret"),
                Pair(longPreferencesKey("speechToken"), 7L),
            ),
        )

        // split preserves encounter order; only the JSON builder sorts keys.
        assertEquals(setOf("alpha", "zeta"), buckets.strings.keys.toSet())
        assertEquals(setOf("flag"), buckets.booleans.keys.toSet())
        assertEquals(setOf("bigCount", "count"), buckets.ints.keys.toSet())
        assertEquals(setOf("ratio"), buckets.floats.keys.toSet())
        assertFalse(buckets.strings.containsKey("anthropicApiKey"))
        assertFalse(buckets.ints.containsKey("speechToken"))
    }

    @Test
    fun jsonHasVersionExportedAtAndSortedTypedBuckets() {
        val json = SettingsExportFile.buildSettingsExportJson(
            strings = mapOf("b" to "2", "a" to "1"),
            booleans = mapOf("on" to true),
            ints = mapOf("n" to 5L),
            floats = mapOf("f" to 1.25),
            exportedAt = "2026-11-03T10:00:00Z",
        )

        val root = JSONObject(json)
        assertEquals(1, root.getInt("version"))
        assertEquals("2026-11-03T10:00:00Z", root.getString("exportedAt"))
        assertEquals(listOf("a", "b"), root.getJSONObject("strings").keys().asSequence().toList())
        assertTrue(root.getJSONObject("booleans").getBoolean("on"))
        assertEquals(5, root.getJSONObject("ints").getInt("n"))
        assertEquals(1.25, root.getJSONObject("floats").getDouble("f"), 0.0)
    }

    @Test
    fun excludedKeyNamesCoverEveryCredentialFragmentCaseInsensitively() {
        assertTrue(SettingsExportFile.isExcluded("anthropicApiKey"))
        assertTrue(SettingsExportFile.isExcluded("SPEECH_TOKEN"))
        assertTrue(SettingsExportFile.isExcluded("mySecret"))
        assertTrue(SettingsExportFile.isExcluded("docPassword"))
        assertFalse(SettingsExportFile.isExcluded("useMetric"))
        assertFalse(SettingsExportFile.isExcluded("appThemeColor"))
    }
}
