package app.chompass.data

import app.chompass.models.FoodEntry

/**
 * Recents-tab sort for Saved Meals. Pref value is the lowercase name
 * (`recent` / `name` / `size`); unknown values fall back to [RECENT].
 */
enum class SavedMealsSort {
    RECENT,
    NAME,
    SIZE,
    ;

    val prefValue: String get() = name.lowercase()

    companion object {
        fun fromPref(value: String): SavedMealsSort =
            entries.find { it.prefValue.equals(value, ignoreCase = true) } ?: RECENT
    }
}

/** Substring, case-insensitive name match. Blank query returns [list] as-is. */
fun filterHistoryTemplates(list: List<FoodEntry>, query: String): List<FoodEntry> {
    val q = query.trim()
    if (q.isEmpty()) return list
    return list.filter { it.name.contains(q, ignoreCase = true) }
}

/**
 * Recents display order. [list] is already unique-by-[FoodEntry.favoriteKey];
 * this does not collapse again. Filter first, then sort.
 */
fun sortHistoryTemplates(list: List<FoodEntry>, sort: SavedMealsSort): List<FoodEntry> =
    when (sort) {
        SavedMealsSort.RECENT -> list.sortedByDescending { it.timestamp }
        SavedMealsSort.NAME -> list.sortedBy { it.name.lowercase() }
        SavedMealsSort.SIZE -> list.sortedWith(
            compareByDescending<FoodEntry> { it.calories }.thenBy { it.name.lowercase() },
        )
    }
