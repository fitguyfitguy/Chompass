package org.codeberg.fitguy.nofud.data

import androidx.datastore.preferences.core.edit
import org.codeberg.fitguy.nofud.models.ChatMessage
import org.codeberg.fitguy.nofud.models.WidgetSnapshot
import org.codeberg.fitguy.nofud.services.health.DebugActivityDay
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer

// -- Coach chat history ----------------------------------------------
internal val PreferencesStore.chatHistoryImpl: Flow<List<ChatMessage>> get() = dataStore.data.map { prefs ->
        prefs[Keys.CHAT_HISTORY]?.let {
            runCatching { json.decodeFromString(ListSerializer(ChatMessage.serializer()), it) }.getOrNull()
        } ?: emptyList()
    }

internal suspend fun PreferencesStore.setChatHistoryImpl(history: List<ChatMessage>) {
        dataStore.edit { it[Keys.CHAT_HISTORY] = json.encodeToString(ListSerializer(ChatMessage.serializer()), history) }
    }

    // -- Widget snapshot --------------------------------------------------
internal val PreferencesStore.widgetSnapshotImpl: Flow<WidgetSnapshot?> get() = dataStore.data.map { prefs ->
        prefs[Keys.WIDGET_SNAPSHOT]?.let {
            runCatching { json.decodeFromString<WidgetSnapshot>(it) }.getOrNull()
        }
    }

internal suspend fun PreferencesStore.setWidgetSnapshotImpl(snapshot: WidgetSnapshot) {
        dataStore.edit { it[Keys.WIDGET_SNAPSHOT] = json.encodeToString(WidgetSnapshot.serializer(), snapshot) }
    }

internal suspend fun PreferencesStore.clearWidgetSnapshotImpl() {
        dataStore.edit { it.remove(Keys.WIDGET_SNAPSHOT) }
    }

    // -- Test data backup (used by TestDataSeeder during dev seeding) -------
internal val PreferencesStore.testSeedBackupJsonImpl: Flow<String?> get() = dataStore.data.map { it[Keys.TEST_SEED_BACKUP] }
internal suspend fun PreferencesStore.setTestSeedBackupJsonImpl(json: String) {
        dataStore.edit { it[Keys.TEST_SEED_BACKUP] = json }
    }
internal suspend fun PreferencesStore.clearTestSeedBackupImpl() {
        dataStore.edit { it.remove(Keys.TEST_SEED_BACKUP) }
    }

  // -- Debug activity (TestDataSeeder synthetic steps / energy burn) --------
internal suspend fun PreferencesStore.setDebugActivityDaysImpl(days: List<DebugActivityDay>) {
        dataStore.edit {
            it[Keys.DEBUG_ACTIVITY_DAYS] = json.encodeToString(
                ListSerializer(DebugActivityDay.serializer()),
                days
            )
        }
    }

internal suspend fun PreferencesStore.clearDebugActivityDaysImpl() {
        dataStore.edit { it.remove(Keys.DEBUG_ACTIVITY_DAYS) }
    }

internal suspend fun PreferencesStore.debugActivityDaysJsonImpl(): String? = dataStore.data.first()[Keys.DEBUG_ACTIVITY_DAYS]

internal suspend fun PreferencesStore.debugActivityDayImpl(date: LocalDate): DebugActivityDay? {
        val raw = dataStore.data.first()[Keys.DEBUG_ACTIVITY_DAYS] ?: return null
        val days = runCatching {
            json.decodeFromString(ListSerializer(DebugActivityDay.serializer()), raw)
        }.getOrNull() ?: return null
        return days.firstOrNull { it.date == date.toString() }
    }

// -- Wipe everything --------------------------------------------------
internal suspend fun PreferencesStore.clearAllImpl() {
        dataStore.edit { it.clear() }
    }

