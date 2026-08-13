package app.chompass.data

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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
// default rather than throwing and taking the whole Flow down with it.

internal fun <T> PreferencesStore.listPref(
    key: Preferences.Key<String>,
    serializer: KSerializer<T>,
): Flow<List<T>> = dataStore.data.map { prefs -> prefs.decodeList(key, serializer, json) }

internal suspend fun <T> PreferencesStore.setListPref(
    key: Preferences.Key<String>,
    serializer: KSerializer<T>,
    entries: List<T>,
) {
    dataStore.edit { it[key] = json.encodeToString(ListSerializer(serializer), entries) }
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
    dataStore.edit { it[key] = json.encodeToString(serializer, value) }
}

/** Writes [value], or clears the key when it is null — the shape every draft pref needs. */
internal suspend fun <T> PreferencesStore.setObjectPrefOrRemove(
    key: Preferences.Key<String>,
    serializer: KSerializer<T>,
    value: T?,
) {
    dataStore.edit {
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
): List<T> = this[key]?.let {
    runCatching { json.decodeFromString(ListSerializer(serializer), it) }.getOrNull()
} ?: emptyList()
