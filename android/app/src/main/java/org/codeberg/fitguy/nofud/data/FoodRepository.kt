package org.codeberg.fitguy.nofud.data

import org.codeberg.fitguy.nofud.models.FoodEntry
import org.codeberg.fitguy.nofud.models.FoodSource
import org.codeberg.fitguy.nofud.services.FoodImageStore
import org.codeberg.fitguy.nofud.services.ReviewPrompter
import org.codeberg.fitguy.nofud.models.MealType
import org.codeberg.fitguy.nofud.services.health.HealthConnectManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.math.roundToInt

/**
 * CRUD + reactive reads for food entries. Port of iOS FoodStore.
 * Backed by [PreferencesStore] (entries + favorites serialized as JSON).
 */
class FoodRepository(
    private val prefs: PreferencesStore,
    private val health: HealthConnectManager? = null,
    private val imageStore: FoodImageStore? = null,
) {
    val entries: Flow<List<FoodEntry>> = prefs.foodEntries

    /**
     * Favorites are now stored as an ordered list of [FoodEntry] copies (not
     * a Set of keys), mirroring iOS `FoodStore.favorites`. The list owns its
     * own copies so a favorite survives deletion of the original log entry
     * and the user-defined order persists across restarts.
     *
     * Reads also trigger a one-time migration from the legacy `favoriteKeys`
     * Set if the new list is empty but the old set has entries — done via a
     * suspend [migratedFavorites] helper that the Saved Meals UI calls
     * directly when the sheet opens.
     */
    val favorites: Flow<List<FoodEntry>> = prefs.favoriteFoodEntries

    /** Run the migration once and return the (possibly newly-seeded) list. */
    suspend fun migratedFavorites(): List<FoodEntry> {
        ensureFavoritesMigrated()
        return prefs.favoriteFoodEntries.first()
    }

    /**
     * Derived from [favorites] so existing call sites that read favoriteKeys
     * (Home list heart icon, Saved Meals heart icon, etc.) keep working
     * without change.
     */
    val favoriteKeys: Flow<Set<String>> = prefs.favoriteFoodEntries.map { list ->
        list.map { it.favoriteKey }.toSet()
    }

    fun entriesForDate(date: LocalDate): Flow<List<FoodEntry>> = entries.map { list ->
        list.filter { it.timestamp.atZone(ZoneId.systemDefault()).toLocalDate() == date }
            .sortedByDescending { it.timestamp }
    }

    fun entriesByMealForDate(date: LocalDate): Flow<List<Pair<MealType, List<FoodEntry>>>> =
        entriesForDate(date).map { dayEntries ->
            MealType.values().mapNotNull { meal ->
                val mealEntries = dayEntries.filter { it.mealType == meal }
                if (mealEntries.isEmpty()) null else meal to mealEntries
            }
        }

    suspend fun addEntry(entry: FoodEntry) {
        val current = prefs.foodEntries.first()
        prefs.setFoodEntries(current + entry)
        if (shouldSyncHealth()) {
            health?.writeNutrition(entry)
        }
        // One-time organic review moment: the first successful food log (iOS parity).
        if (!prefs.reviewPromptedAfterFirstLog.first()) {
            prefs.setReviewPromptedAfterFirstLog(true)
            ReviewPrompter.requestReview.value = true
        }
    }

    suspend fun updateEntry(entry: FoodEntry) {
        val current = prefs.foodEntries.first()
        val index = current.indexOfFirst { it.id == entry.id }
        if (index < 0) return
        val updated = current.toMutableList().also { it[index] = entry }
        prefs.setFoodEntries(updated)
        if (shouldSyncHealth()) {
            health?.updateNutrition(entry)
        } else {
            // Sync off: still clean up the stale HC record for this entry (iOS
            // parity, best-effort) so the restore path can't resurrect the
            // pre-edit version later.
            health?.deleteNutrition(entry.id)
        }
    }

    suspend fun deleteEntry(entryId: UUID) {
        val current = prefs.foodEntries.first()
        val removed = current.find { it.id == entryId } ?: return
        prefs.setFoodEntries(current.filter { it.id != entryId })
        deleteImageIfUnreferenced(removed.imageFilename)
        // Delete even when sync is off (iOS parity, best-effort) — a surviving
        // fudai-tagged record would resurrect through restoreFromHealthConnect.
        health?.deleteNutrition(entryId)
    }

    suspend fun replaceAll(entries: List<FoodEntry>) {
        prefs.setFoodEntries(entries)
    }

    /**
     * Bulk-import diary entries into the existing log in a single write.
     * We preserve current rows and dedupe only by id to avoid clobbering
     * user edits that share similar nutrition values.
     */
    suspend fun importEntries(entries: List<FoodEntry>): Int {
        if (entries.isEmpty()) return 0
        val current = prefs.foodEntries.first()
        val existingIds = current.asSequence().map { it.id }.toHashSet()
        val incoming = entries.filter { existingIds.add(it.id) }
        if (incoming.isEmpty()) return 0
        prefs.setFoodEntries((current + incoming).sortedBy { it.timestamp })
        return incoming.size
    }

    suspend fun clear() {
        prefs.setFoodEntries(emptyList())
    }

    // -- Favorites --------------------------------------------------------

    suspend fun isFavorite(entry: FoodEntry): Boolean {
        return prefs.favoriteFoodEntries.first().any { it.favoriteKey == entry.favoriteKey }
    }

    /**
     * Toggle favorite status by favoriteKey. Mirrors iOS
     * FoodStore.toggleFavorite — if a favorite with the same favoriteKey
     * exists, remove it; otherwise append a *copy* of [entry] to the list.
     * The legacy `favoriteKeys` Set is also kept in sync for any older code
     * paths still reading it directly.
     */
    suspend fun toggleFavorite(entry: FoodEntry) {
        ensureFavoritesMigrated()
        val current = prefs.favoriteFoodEntries.first().toMutableList()
        val idx = current.indexOfFirst { it.favoriteKey == entry.favoriteKey }
        if (idx >= 0) {
            val removed = current.removeAt(idx)
            prefs.setFavoriteFoodEntries(current)
            prefs.setFavoriteKeys(current.map { it.favoriteKey }.toSet())
            deleteImageIfUnreferenced(removed.imageFilename)
            return
        } else {
            // Drop any other entry with the same id (defensive — should not
            // normally happen since we matched by favoriteKey above).
            current.removeAll { it.id == entry.id }
            current.add(entry)
        }
        prefs.setFavoriteFoodEntries(current)
        prefs.setFavoriteKeys(current.map { it.favoriteKey }.toSet())
    }

    /**
     * Reorder a favorite from index [from] to index [to]. Mirrors iOS
     * FoodStore.moveFavorite using SwiftUI's `Array.move(fromOffsets:toOffset:)`
     * semantics — [to] is the *destination* index in the post-removal list.
     */
    suspend fun moveFavorite(from: Int, to: Int) {
        ensureFavoritesMigrated()
        val list = prefs.favoriteFoodEntries.first().toMutableList()
        if (from !in list.indices) return
        val item = list.removeAt(from)
        val safeTo = to.coerceIn(0, list.size)
        list.add(safeTo, item)
        prefs.setFavoriteFoodEntries(list)
    }

    /**
     * One-time migration: if the new ordered favoriteFoodEntries list is
     * empty but the legacy favoriteKeys Set has entries, reconstruct the
     * ordered list from current food log entries (best-effort — no preserved
     * order since the old format never tracked one).
     */
    private suspend fun ensureFavoritesMigrated() {
        val ordered = prefs.favoriteFoodEntries.first()
        if (ordered.isNotEmpty()) return
        val legacy = prefs.favoriteKeys.first()
        if (legacy.isEmpty()) return
        val all = prefs.foodEntries.first()
        val seeded = legacy.mapNotNull { key -> all.firstOrNull { it.favoriteKey == key } }
        if (seeded.isNotEmpty()) prefs.setFavoriteFoodEntries(seeded)
    }

    private suspend fun shouldSyncHealth(): Boolean {
        val manager = health ?: return false
        return prefs.healthConnectEnabled.first() && manager.hasNutritionWrite()
    }

    /** Drop on-disk JPEGs once no log row or favorite still references them. */
    private suspend fun deleteImageIfUnreferenced(imageFilename: String?) {
        val filename = imageFilename ?: return
        val store = imageStore ?: return
        if (prefs.foodEntries.first().any { it.imageFilename == filename }) return
        if (prefs.favoriteFoodEntries.first().any { it.imageFilename == filename }) return
        store.delete(filename)
    }

    // -- Restore from Health Connect --------------------------------------

    /**
     * Rebuilds the food log from the NutritionRecords Fud AI itself wrote to
     * Health Connect — the restore path after a reinstall or new phone, where
     * Health Connect data survives but app storage doesn't. Only records
     * carrying our fudai_(uuid) clientRecordId are considered; the original
     * entry UUID is recovered from the tag so future edits and deletes still
     * target the matching HC record. Ids already in the log and nameless
     * records are skipped, and nothing is written back to Health Connect.
     * Photos, emojis, notes and serving units aren't in HC and don't return.
     */
    suspend fun restoreFromHealthConnect(external: List<org.codeberg.fitguy.nofud.services.health.ExternalNutrition>) {
        val manager = health ?: return
        val current = prefs.foodEntries.first()
        val existingIds = current.map { it.id }.toSet()
        val restored = external.mapNotNull { record ->
            val id = manager.ownRecordId(record.clientRecordId) ?: return@mapNotNull null
            if (id in existingIds) return@mapNotNull null
            val name = record.name?.trim().orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            FoodEntry(
                id = id,
                name = name,
                calories = (record.calories ?: 0.0).roundToInt(),
                protein = record.protein ?: 0.0,
                carbs = record.carbs ?: 0.0,
                fat = record.fat ?: 0.0,
                timestamp = record.time,
                source = FoodSource.MANUAL,
                mealType = record.mealType,
                sugar = record.sugar,
                fiber = record.fiber,
                saturatedFat = record.saturatedFat,
                monounsaturatedFat = record.monounsaturatedFat,
                polyunsaturatedFat = record.polyunsaturatedFat,
                cholesterol = record.cholesterol,
                sodium = record.sodium,
                potassium = record.potassium,
                transFat = record.transFat,
                calcium = record.calcium,
                iron = record.iron,
                magnesium = record.magnesium,
                zinc = record.zinc,
                vitaminA = record.vitaminA,
                vitaminC = record.vitaminC,
                vitaminD = record.vitaminD,
                vitaminB12 = record.vitaminB12,
                vitaminE = record.vitaminE,
                vitaminK = record.vitaminK,
                folate = record.folate
            )
        }
        if (restored.isEmpty()) return
        prefs.setFoodEntries((current + restored).sortedBy { it.timestamp })
    }

    /**
     * Live-merge meals other apps wrote to Health Connect (the change-token path;
     * own fudai_ records are already filtered at the manager level). Same idempotent
     * upsert scheme as [WeightRepository.importExternalWeights]: a deterministic id
     * per external record, so re-imports and in-place edits update instead of
     * duplicating. Nameless records are skipped — they can't be shown in the log.
     */
    suspend fun importExternalNutrition(external: List<org.codeberg.fitguy.nofud.services.health.ExternalNutrition>) {
        if (external.isEmpty()) return
        val incoming = external.mapNotNull { record ->
            val name = record.name?.trim().orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            FoodEntry(
                id = externalId(record.clientRecordId, record.recordId, record.time),
                name = name,
                calories = (record.calories ?: 0.0).roundToInt(),
                protein = record.protein ?: 0.0,
                carbs = record.carbs ?: 0.0,
                fat = record.fat ?: 0.0,
                timestamp = record.time,
                source = FoodSource.MANUAL,
                mealType = record.mealType,
                sugar = record.sugar,
                fiber = record.fiber,
                saturatedFat = record.saturatedFat,
                monounsaturatedFat = record.monounsaturatedFat,
                polyunsaturatedFat = record.polyunsaturatedFat,
                cholesterol = record.cholesterol,
                sodium = record.sodium,
                potassium = record.potassium,
                transFat = record.transFat,
                calcium = record.calcium,
                iron = record.iron,
                magnesium = record.magnesium,
                zinc = record.zinc,
                vitaminA = record.vitaminA,
                vitaminC = record.vitaminC,
                vitaminD = record.vitaminD,
                vitaminB12 = record.vitaminB12,
                vitaminE = record.vitaminE,
                vitaminK = record.vitaminK,
                folate = record.folate
            )
        }
        if (incoming.isEmpty()) return
        val byId = prefs.foodEntries.first().associateBy { it.id }.toMutableMap()
        var changed = false
        for (entry in incoming) {
            if (byId[entry.id] != entry) {
                byId[entry.id] = entry
                changed = true
            }
        }
        if (!changed) return
        prefs.setFoodEntries(byId.values.sortedBy { it.timestamp })
    }

    /** Stable id for an external nutrition record — see [WeightRepository.externalId]:
     *  clientRecordId, then the HC record id, then the timestamp; never the values. */
    private fun externalId(clientRecordId: String?, recordId: String, time: Instant): UUID {
        val seed = clientRecordId?.takeIf { it.isNotBlank() }
            ?: recordId.takeIf { it.isNotBlank() }
            ?: "hc-nutrition:${time.toEpochMilli()}"
        return UUID.nameUUIDFromBytes(seed.toByteArray())
    }

    // -- Recents / Frequent ---------------------------------------------

    suspend fun recent(limit: Int = 50): List<FoodEntry> =
        prefs.foodEntries.first().sortedByDescending { it.timestamp }.take(limit)

    suspend fun frequent(): List<FrequentFoodGroup> {
        val all = prefs.foodEntries.first()
        val aggregates = mutableMapOf<String, Pair<Int, FoodEntry>>()
        for (entry in all) {
            val key = entry.favoriteKey
            val existing = aggregates[key]
            if (existing != null) {
                val (count, template) = existing
                val newTemplate = if (entry.timestamp > template.timestamp) entry else template
                aggregates[key] = (count + 1) to newTemplate
            } else {
                aggregates[key] = 1 to entry
            }
        }
        return aggregates.map { (_, pair) ->
            FrequentFoodGroup(template = pair.second, count = pair.first)
        }.sortedWith(
            compareByDescending<FrequentFoodGroup> { it.count }.thenBy { it.name.lowercase() }
        )
    }
}

data class FrequentFoodGroup(
    val template: FoodEntry,
    val count: Int
) {
    val id: String = template.favoriteKey
    val name: String = template.name
    val calories: Int = template.calories
}

// Helper — converts Instant -> start-of-day in system zone.
@Suppress("unused")
internal fun Instant.toLocalDate(): LocalDate =
    this.atZone(ZoneId.systemDefault()).toLocalDate()
