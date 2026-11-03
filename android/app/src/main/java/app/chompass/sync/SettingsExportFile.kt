package app.chompass.sync

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Codeberg #117 (export half): writes the raw user preferences to a
 * user-picked SAF document as versioned JSON. Export-only by design — there
 * is no restore path; the file documents a setup and can be diffed/inspected.
 *
 * Secrets never leave the device: any key whose name mentions a credential
 * fragment (key, token, secret, password) is excluded regardless of value,
 * so AI API keys and legacy pref keys cannot surface in an export even
 * though runtime keys live in the KeyStore.
 */
object SettingsExportFile {
    const val SUGGESTED_FILE_NAME = "Chompass-settings.json"
    private const val MIME_JSON = "application/json"

    /** Case-insensitive name fragments that keep a preference out of exports. */
    private val EXCLUDED_NAME_FRAGMENTS = listOf("key", "token", "secret", "password")

    /** Raw preferences split by stored value type for the export buckets. */
    data class Buckets(
        val strings: Map<String, String>,
        val booleans: Map<String, Boolean>,
        val ints: Map<String, Long>,
        val floats: Map<String, Double>,
    )

    fun isExcluded(keyName: String): Boolean =
        EXCLUDED_NAME_FRAGMENTS.any { keyName.contains(it, ignoreCase = true) }

    /**
     * Splits [prefs] into the four typed buckets keyed by preference name,
     * dropping excluded names. Typed sets and serialized blobs (lists, JSON
     * strings) are not representable as simple settings and stay out — only
     * scalars export.
     */
    fun split(prefs: Map<Preferences.Key<*>, Any?>): Buckets {
        val strings = LinkedHashMap<String, String>()
        val booleans = LinkedHashMap<String, Boolean>()
        val ints = LinkedHashMap<String, Long>()
        val floats = LinkedHashMap<String, Double>()
        for ((key, value) in prefs) {
            if (isExcluded(key.name)) continue
            when (value) {
                is String -> strings[key.name] = value
                is Boolean -> booleans[key.name] = value
                is Int -> ints[key.name] = value.toLong()
                is Long -> ints[key.name] = value
                is Float -> floats[key.name] = value.toDouble()
                is Double -> floats[key.name] = value
                else -> Unit
            }
        }
        return Buckets(strings, booleans, ints, floats)
    }

    /**
     * Builds `{"version":1,"exportedAt":…,"strings":{…},"booleans":{…},
     * "ints":{…},"floats":{…}}` with keys sorted inside every bucket for
     * stable, diff-friendly output.
     */
    fun buildSettingsExportJson(
        strings: Map<String, String>,
        booleans: Map<String, Boolean>,
        ints: Map<String, Long>,
        floats: Map<String, Double>,
        exportedAt: String,
    ): String {
        fun bucket(map: Map<String, Any?>): JsonObject = buildJsonObject {
            for (name in map.keys.sorted()) {
                put(
                    name,
                    when (val value = map.getValue(name)) {
                        is String -> JsonPrimitive(value)
                        is Boolean -> JsonPrimitive(value)
                        is Number -> JsonPrimitive(value)
                        else -> error("Unsupported settings export value for $name")
                    },
                )
            }
        }
        return buildJsonObject {
            put("version", JsonPrimitive(1))
            put("exportedAt", JsonPrimitive(exportedAt))
            put("strings", bucket(strings))
            put("booleans", bucket(booleans))
            put("ints", bucket(ints))
            put("floats", bucket(floats))
        }.toString()
    }

    /**
     * Writes [json] to the SAF document at [uri] and reads it back to prove
     * the bytes landed, mirroring [LocalBackupFile]'s verify-after-write.
     */
    suspend fun write(context: Context, uri: Uri, json: String) = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        resolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(json.toByteArray(Charsets.UTF_8))
            out.flush()
        } ?: error("Could not open the export file")
        val written = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        check(written == json) { "Settings export verification failed" }
        Unit
    }
}
