package app.chompass.data

import app.chompass.models.ManualActiveEntry
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class ManualActiveRepository(private val prefs: PreferencesStore) {
    val entries: Flow<List<ManualActiveEntry>> = prefs.manualActiveEntries.map { list ->
        list.sortedByDescending { it.date }
    }

    suspend fun caloriesForDate(date: LocalDate): Int =
        prefs.manualActiveEntries.first()
            .filter { it.date == date.toString() }
            .sumOf { it.calories }

    suspend fun add(entry: ManualActiveEntry) {
        val normalized = entry.copy(
            name = entry.name.trim().ifEmpty { "Activity" },
            calories = entry.calories.coerceAtLeast(0),
        )
        prefs.editListPref(Keys.MANUAL_ACTIVE_ENTRIES, ManualActiveEntry.serializer()) { it + normalized }
    }

    suspend fun update(id: String, name: String, calories: Int) {
        prefs.editListPref(Keys.MANUAL_ACTIVE_ENTRIES, ManualActiveEntry.serializer()) { current ->
            current.map { entry ->
                if (entry.id != id) entry
                else entry.copy(
                    name = name.trim().ifEmpty { "Activity" },
                    calories = calories.coerceAtLeast(0),
                )
            }
        }
    }

    suspend fun delete(id: String) {
        prefs.editListPref(Keys.MANUAL_ACTIVE_ENTRIES, ManualActiveEntry.serializer()) { current ->
            current.filterNot { it.id == id }
        }
    }
}
