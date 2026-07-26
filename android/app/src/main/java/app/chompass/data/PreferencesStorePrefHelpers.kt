package app.chompass.data

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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

internal fun PreferencesStore.stringPref(key: Preferences.Key<String>): Flow<String?> =
    dataStore.data.map { it[key] }

internal suspend fun PreferencesStore.setStringPref(key: Preferences.Key<String>, value: String) {
    dataStore.edit { it[key] = value }
}
