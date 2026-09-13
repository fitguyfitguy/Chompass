package app.chompass.data

import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Shared DataStore accessors — keeps Keys explicit, avoids copy-paste map/edit pairs. */
internal fun PreferencesStore.boolPref(key: Preferences.Key<Boolean>, default: Boolean): Flow<Boolean> =
    dataStore.data.map { it[key] ?: default }

internal suspend fun PreferencesStore.setBoolPref(key: Preferences.Key<Boolean>, value: Boolean) {
    dataStore.edit { it[key] = value }
}

internal fun PreferencesStore.intPref(key: Preferences.Key<Int>, default: Int): Flow<Int> =
    dataStore.data.map { it[key] ?: default }

internal suspend fun PreferencesStore.setIntPref(key: Preferences.Key<Int>, value: Int) {
    dataStore.edit { it[key] = value }
}

internal fun PreferencesStore.longPref(key: Preferences.Key<Long>, default: Long): Flow<Long> =
    dataStore.data.map { it[key] ?: default }

internal suspend fun PreferencesStore.setLongPref(key: Preferences.Key<Long>, value: Long) {
    dataStore.edit { it[key] = value }
}

internal fun PreferencesStore.stringPref(key: Preferences.Key<String>): Flow<String?> =
    dataStore.data.map { it[key] }

internal suspend fun PreferencesStore.setStringPref(key: Preferences.Key<String>, value: String) {
    dataStore.edit { it[key] = value }
}

/** Writes [value], or clears the key when it is null. */
internal suspend fun PreferencesStore.setStringPrefOrRemove(key: Preferences.Key<String>, value: String?) {
    dataStore.edit {
        if (value == null) it.remove(key) else it[key] = value
    }
}

// -- JSON-backed values -----------------------------------------------
//
// Complex prefs are stored as JSON strings. Decoding is deliberately lenient:
// a blob written by an older schema, or a corrupt one, yields the empty/null
// default rather than throwing and taking the whole Flow down with it. Reads
// drop only the unreadable rows (LenientJsonList), and every setter copies an
// unreadable (or partially unreadable) blob aside before replacing it, so
// leniency never turns "append one row" into "erase the store"
// (PersistedJsonGuard, upstream fud-ai c278c64d).

private const val TAG = "PreferencesStore"

internal fun <T> PreferencesStore.listPref(
    key: Preferences.Key<String>,
    serializer: KSerializer<T>,
): Flow<List<T>> = dataStore.data.map { prefs -> prefs.decodeList(key, serializer, json) }

internal suspend fun <T> PreferencesStore.setListPref(
    key: Preferences.Key<String>,
    serializer: KSerializer<T>,
    entries: List<T>,
) {
    dataStore.edit {
        preserveUnreadableList(it, key, serializer)
        it[key] = json.encodeToString(ListSerializer(serializer), entries)
    }
}

internal fun <T> PreferencesStore.objectPref(
    key: Preferences.Key<String>,
    serializer: KSerializer<T>,
): Flow<T?> = dataStore.data.map { prefs ->
    prefs[key]?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }
}

internal suspend fun <T> PreferencesStore.setObjectPref(
    key: Preferences.Key<String>,
    serializer: KSerializer<T>,
    value: T,
) {
    dataStore.edit {
        preserveUndecodableValue(it, key, serializer)
        it[key] = json.encodeToString(serializer, value)
    }
}

/** Writes [value], or clears the key when it is null — the shape every draft pref needs. */
internal suspend fun <T> PreferencesStore.setObjectPrefOrRemove(
    key: Preferences.Key<String>,
    serializer: KSerializer<T>,
    value: T?,
) {
    dataStore.edit {
        preserveUndecodableValue(it, key, serializer)
        if (value == null) it.remove(key) else it[key] = json.encodeToString(serializer, value)
    }
}

internal suspend fun PreferencesStore.removePref(key: Preferences.Key<*>) {
    dataStore.edit { it.remove(key) }
}

/**
 * Decodes a JSON list straight out of a [Preferences] snapshot. Exposed
 * separately from [listPref] so read-modify-write blocks inside a single
 * `dataStore.edit` transaction can reuse the same lenient decode.
 */
internal fun <T> Preferences.decodeList(
    key: Preferences.Key<String>,
    serializer: KSerializer<T>,
    json: Json,
): List<T> {
    val decoded = LenientJsonList.decode(json, serializer, this[key])
    when (decoded) {
        is PersistedListDecode.Corrupt ->
            Log.w(TAG, "Stored '${key.name}' could not be decoded; showing empty until it is preserved on next write")
        is PersistedListDecode.Decoded ->
            if (decoded.dropped > 0) Log.w(TAG, "Dropped ${decoded.dropped} unreadable row(s) from '${key.name}'")
        PersistedListDecode.Missing -> Unit
    }
    return decoded.itemsOrEmpty
}

// -- Preserve-before-overwrite (PersistedJsonGuard) --------------------

/** Inside one `dataStore.edit`: copies an unreadable (or partially unreadable)
 *  list aside before the caller's write replaces it. */
internal fun <T> PreferencesStore.preserveUnreadableList(
    prefs: MutablePreferences,
    key: Preferences.Key<String>,
    serializer: KSerializer<T>,
) {
    val raw = prefs[key] ?: return
    preserveUnreadableRaw(prefs, key.name, raw, serializer)
}

/** Name-based variant for keys discovered dynamically (legacy migration buckets). */
internal fun <T> PreferencesStore.preserveUnreadableRaw(
    prefs: MutablePreferences,
    keyName: String,
    raw: String,
    serializer: KSerializer<T>,
) {
    val decoded = LenientJsonList.decode(json, serializer, raw)
    if (!decoded.needsPreservation) return
    if (decoded is PersistedListDecode.Decoded && decoded.dropped > 0) {
        Log.w(TAG, "Dropped ${decoded.dropped} unreadable row(s) from '$keyName' before rewrite")
    }
    preserveRaw(prefs, keyName, decoded.rawOrNull ?: return)
}

/** Single-value variant: back up an unreadable blob before replacing it. */
internal fun <T> PreferencesStore.preserveUndecodableValue(
    prefs: MutablePreferences,
    key: Preferences.Key<String>,
    serializer: KSerializer<T>,
) {
    val raw = prefs[key] ?: return
    if (runCatching { json.decodeFromString(serializer, raw) }.isFailure) {
        preserveRaw(prefs, key.name, raw)
    }
}

internal fun PreferencesStore.preserveRaw(prefs: MutablePreferences, keyName: String, raw: String) {
    val file = corruptArchive.preserveText("$keyName.json", raw)
    if (file != null) {
        Log.w(TAG, "Preserved unreadable '$keyName' at ${file.absolutePath}")
    } else {
        // Same transaction as the overwrite, so the bytes survive even when
        // the file copy fails.
        val backupKey = stringPreferencesKey(CorruptBlobArchive.backupName(keyName))
        prefs[backupKey] = raw
        Log.w(TAG, "Preserved unreadable '$keyName' under '${backupKey.name}'")
    }
}
