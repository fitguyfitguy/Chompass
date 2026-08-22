package app.chompass.data

import androidx.datastore.preferences.core.edit
import app.chompass.services.ai.RecalcSheetData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// -- Last goal-change transparency sheet ------------------------------
// The Goals screen keeps the newest goal-change explanation (AI Recalculate
// or the deterministic Adaptive pass) so the user can reopen the result sheet
// on demand, e.g. after Adaptive nudges goals while they were away.

internal val PreferencesStore.lastGoalChangeSheetJsonImpl: Flow<String?>
    get() = dataStore.data.map { it[Keys.LAST_GOAL_CHANGE_SHEET_JSON] }

internal suspend fun PreferencesStore.setLastGoalChangeSheetJsonImpl(json: String?) {
    dataStore.edit {
        if (json == null) it.remove(Keys.LAST_GOAL_CHANGE_SHEET_JSON)
        else it[Keys.LAST_GOAL_CHANGE_SHEET_JSON] = json
    }
}

/** Latest goal-change explanation, or null when none is stored yet. */
suspend fun PreferencesStore.loadLastGoalChangeSheet(): RecalcSheetData? =
    lastGoalChangeSheetJsonImpl.first()?.let { raw ->
        runCatching { json.decodeFromString(RecalcSheetData.serializer(), raw) }.getOrNull()
    }

/** Persist the latest goal-change explanation (newest entry wins). */
suspend fun PreferencesStore.saveLastGoalChangeSheet(data: RecalcSheetData) {
    setLastGoalChangeSheetJsonImpl(json.encodeToString(RecalcSheetData.serializer(), data))
}
