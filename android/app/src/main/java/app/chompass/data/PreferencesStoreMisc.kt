package app.chompass.data

import androidx.datastore.preferences.core.edit
import app.chompass.models.ChatMessage
import app.chompass.models.WidgetSnapshot
import app.chompass.services.health.DebugActivityDay
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// -- Coach chat history ----------------------------------------------
internal val PreferencesStore.chatHistoryImpl: Flow<List<ChatMessage>>
    get() = listPref(Keys.CHAT_HISTORY, ChatMessage.serializer())

internal suspend fun PreferencesStore.setChatHistoryImpl(history: List<ChatMessage>) =
    setListPref(Keys.CHAT_HISTORY, ChatMessage.serializer(), history)

// -- Widget snapshot --------------------------------------------------
internal val PreferencesStore.widgetSnapshotImpl: Flow<WidgetSnapshot?>
    get() = objectPref(Keys.WIDGET_SNAPSHOT, WidgetSnapshot.serializer())

internal suspend fun PreferencesStore.setWidgetSnapshotImpl(snapshot: WidgetSnapshot) =
    setObjectPref(Keys.WIDGET_SNAPSHOT, WidgetSnapshot.serializer(), snapshot)

internal suspend fun PreferencesStore.clearWidgetSnapshotImpl() = removePref(Keys.WIDGET_SNAPSHOT)

// -- Test data backup (used by TestDataSeeder during dev seeding) -------
internal val PreferencesStore.testSeedBackupJsonImpl: Flow<String?>
    get() = stringPref(Keys.TEST_SEED_BACKUP)

internal suspend fun PreferencesStore.setTestSeedBackupJsonImpl(json: String) =
    setStringPref(Keys.TEST_SEED_BACKUP, json)

internal suspend fun PreferencesStore.clearTestSeedBackupImpl() = removePref(Keys.TEST_SEED_BACKUP)

// -- Debug activity (TestDataSeeder synthetic steps / energy burn) --------
internal suspend fun PreferencesStore.setDebugActivityDaysImpl(days: List<DebugActivityDay>) =
    setListPref(Keys.DEBUG_ACTIVITY_DAYS, DebugActivityDay.serializer(), days)

internal suspend fun PreferencesStore.clearDebugActivityDaysImpl() = removePref(Keys.DEBUG_ACTIVITY_DAYS)

internal suspend fun PreferencesStore.debugActivityDaysJsonImpl(): String? = dataStore.data.first()[Keys.DEBUG_ACTIVITY_DAYS]

internal suspend fun PreferencesStore.debugActivityDayImpl(date: LocalDate): DebugActivityDay? =
    dataStore.data.first()
        .decodeList(Keys.DEBUG_ACTIVITY_DAYS, DebugActivityDay.serializer(), json)
        .firstOrNull { it.date == date.toString() }

// -- Debug resting-shade flag (home hero A/B) ---------------------------
internal val PreferencesStore.debugShowRestingShadeImpl: Flow<Boolean>
    get() = dataStore.data.map { it[Keys.DEBUG_SHOW_RESTING_SHADE] ?: false }

internal suspend fun PreferencesStore.setDebugShowRestingShadeImpl(show: Boolean) {
    dataStore.edit { it[Keys.DEBUG_SHOW_RESTING_SHADE] = show }
}

// -- Debug demo-analysis flag (demo_ai extra; scripted video capture) --------
internal val PreferencesStore.debugDemoAnalysisImpl: Flow<Boolean>
    get() = dataStore.data.map { it[Keys.DEBUG_DEMO_ANALYSIS] ?: false }

internal suspend fun PreferencesStore.setDebugDemoAnalysisImpl(enabled: Boolean) {
    dataStore.edit { it[Keys.DEBUG_DEMO_ANALYSIS] = enabled }
}

// -- Wipe everything --------------------------------------------------
internal suspend fun PreferencesStore.clearAllImpl() {
    dataStore.edit { it.clear() }
}
