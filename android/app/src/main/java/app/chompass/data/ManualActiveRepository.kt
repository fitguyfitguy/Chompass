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
        val next = prefs.manualActiveEntries.first() + entry.copy(
            name = entry.name.trim().ifEmpty { "Activity" },
            calories = entry.calories.coerceAtLeast(0),
        )
        prefs.setManualActiveEntries(next)
    }

    suspend fun delete(id: String) {
        prefs.setManualActiveEntries(prefs.manualActiveEntries.first().filterNot { it.id == id })
    }
}
