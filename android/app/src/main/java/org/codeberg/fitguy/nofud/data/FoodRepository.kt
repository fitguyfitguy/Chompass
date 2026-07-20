package org.codeberg.fitguy.nofud.data

import org.codeberg.fitguy.nofud.models.FoodEntry
import org.codeberg.fitguy.nofud.models.FoodSource
import org.codeberg.fitguy.nofud.services.FoodImageStore
import org.codeberg.fitguy.nofud.services.PerfLog
import org.codeberg.fitguy.nofud.services.ReviewPrompter
import org.codeberg.fitguy.nofud.models.MealType
import org.codeberg.fitguy.nofud.services.health.HealthConnectManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import kotlin.math.roundToInt

private fun FoodEntry.month(): YearMonth = YearMonth.from(timestamp.atZone(ZoneId.systemDefault()))

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
     * directly when the sheet opens. That helper also collapses duplicates
     * produced by the old name|calories identity key.
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

    fun entriesForDate(date: LocalDate): Flow<List<FoodEntry>> =
        prefs.foodEntriesForMonth(YearMonth.from(date)).map { monthEntries ->
            monthEntries.filter { it.timestamp.atZone(ZoneId.systemDefault()).toLocalDate() == date }
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
        // Persistence commit — only this entry's month bucket is re-serialized
        // and written, not the whole food log, then the Health Connect IPC insert.
        PerfLog.measure("save", "dataStore", "month=${entry.month()}") {
            prefs.applyFoodEntryBucketChanges(upsertsByMonth = mapOf(entry.month() to listOf(entry)))
        }
        if (shouldSyncHealth()) {
            PerfLog.measure("save", "healthWrite") { health?.writeNutrition(entry) }
        }
        // One-time organic review moment: the first successful food log (iOS parity).
        if (!prefs.reviewPromptedAfterFirstLog.first()) {
            prefs.setReviewPromptedAfterFirstLog(true)
            ReviewPrompter.requestReview.value = true
        }
    }

    suspend fun updateEntry(original: FoodEntry, updated: FoodEntry) {
        val oldMonth = original.month()
        val newMonth = updated.month()
        if (oldMonth == newMonth) {
            prefs.applyFoodEntryBucketChanges(upsertsByMonth = mapOf(newMonth to listOf(updated)))
        } else {
            prefs.applyFoodEntryBucketChanges(
                upsertsByMonth = mapOf(newMonth to listOf(updated)),
                removalIdsByMonth = mapOf(oldMonth to setOf(updated.id)),
            )
        }
        if (shouldSyncHealth()) {
            health?.updateNutrition(updated)
        } else {
            // Sync off: still clean up the stale HC record for this entry (iOS
            // parity, best-effort) so the restore path can't resurrect the
            // pre-edit version later.
            health?.deleteNutrition(updated.id)
        }
    }

    suspend fun deleteEntry(entry: FoodEntry) {
        prefs.applyFoodEntryBucketChanges(removalIdsByMonth = mapOf(entry.month() to setOf(entry.id)))
        deleteImageIfUnreferenced(entry.imageFilename)
        // Delete even when sync is off (iOS parity, best-effort) — a surviving
        // fudai-tagged record would resurrect through restoreFromHealthConnect.
        health?.deleteNutrition(entry.id)
    }

    suspend fun replaceAll(entries: List<FoodEntry>) {
        prefs.replaceAllFoodEntries(entries)
    }

    /**
     * Bulk-import diary entries into the existing log in a single write.
     * We preserve current rows and dedupe only by id to avoid clobbering
     * user edits that share similar nutrition values.
     */
    suspend fun importEntries(entries: List<FoodEntry>): Int {
        if (entries.isEmpty()) return 0
        val existingIds = prefs.foodEntries.first().asSequence().map { it.id }.toHashSet()
        val incoming = entries.filter { existingIds.add(it.id) }
        if (incoming.isEmpty()) return 0
        prefs.applyFoodEntryBucketChanges(upsertsByMonth = incoming.groupBy { it.month() })
        return incoming.size
    }

    suspend fun clear() {
        prefs.replaceAllFoodEntries(emptyList())
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
     *
     * Also collapses duplicate favorites that shared a name under the old
     * `name|calories` identity (same food, different servings), and rewrites
     * the legacy key set to the name-only identity.
     */
    private suspend fun ensureFavoritesMigrated() {
        val ordered = prefs.favoriteFoodEntries.first()
        if (ordered.isEmpty()) {
            val legacy = prefs.favoriteKeys.first()
            if (legacy.isEmpty()) return
            val all = prefs.foodEntries.first()
            val seeded = legacy.mapNotNull { key ->
                all.firstOrNull { matchesFavoriteIdentity(it, key) }
            }.let { dedupeFavoritesByIdentity(it) }
            if (seeded.isEmpty()) return
            prefs.setFavoriteFoodEntries(seeded)
            prefs.setFavoriteKeys(seeded.map { it.favoriteKey }.toSet())
            return
        }
        val deduped = dedupeFavoritesByIdentity(ordered)
        val newKeys = deduped.map { it.favoriteKey }.toSet()
        val oldKeys = prefs.favoriteKeys.first()
        if (deduped.size != ordered.size || newKeys != oldKeys) {
            prefs.setFavoriteFoodEntries(deduped)
            prefs.setFavoriteKeys(newKeys)
        }
    }

    /** Keep first occurrence per [FoodEntry.favoriteKey] (preserves user order). */
    private fun dedupeFavoritesByIdentity(entries: List<FoodEntry>): List<FoodEntry> {
        val seen = mutableSetOf<String>()
        return entries.filter { it.favoriteKey.isNotEmpty() && seen.add(it.favoriteKey) }
    }

    /**
     * Match a stored favorite key against an entry. Accepts the current
     * name-only identity and the legacy `name|calories` form.
     */
    private fun matchesFavoriteIdentity(entry: FoodEntry, storedKey: String): Boolean {
        if (entry.favoriteKey == storedKey) return true
        val namePart = storedKey.substringBefore('|').trim().lowercase()
        return namePart.isNotEmpty() && entry.favoriteKey == namePart
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
        prefs.applyFoodEntryBucketChanges(upsertsByMonth = restored.groupBy { it.month() })
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
        val existingById = prefs.foodEntries.first().associateBy { it.id }
        val changed = incoming.filter { existingById[it.id] != it }
        if (changed.isEmpty()) return
        // A record can carry a new timestamp on re-sync, moving it to a different
        // month bucket than the one it's currently stored in — remove the stale
        // copy from its old bucket alongside upserting the new one.
        val staleRemovals = changed.mapNotNull { entry ->
            val existingMonth = existingById[entry.id]?.month() ?: return@mapNotNull null
            existingMonth.takeIf { it != entry.month() }?.let { it to entry.id }
        }.groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() }
        prefs.applyFoodEntryBucketChanges(
            upsertsByMonth = changed.groupBy { it.month() },
            removalIdsByMonth = staleRemovals,
        )
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

    /** Identity keys already taken by diary rows and favorites. */
    suspend fun existingFoodIdentityKeys(): Set<String> {
        val diary = prefs.foodEntries.first().mapNotNull { it.favoriteKey.takeIf { k -> k.isNotEmpty() } }
        val favs = prefs.favoriteFoodEntries.first().mapNotNull { it.favoriteKey.takeIf { k -> k.isNotEmpty() } }
        return diary.toSet() + favs
    }

    /**
     * Newest diary rows, collapsed by [FoodEntry.favoriteKey] so re-logging
     * the same food at a new serving does not stack duplicate picker rows.
     * Template for each food is the most recent log (latest grams/units).
     */
    suspend fun recent(limit: Int = 50): List<FoodEntry> =
        recentFoodTemplates(prefs.foodEntries.first(), limit)

    /**
     * Groups diary rows by food identity ([FoodEntry.favoriteKey]). Count is
     * how often that food was logged; template is the newest serving snapshot
     * so a re-log with new grams/pieces becomes the amount offered next time.
     */
    suspend fun frequent(): List<FrequentFoodGroup> =
        frequentFoodGroups(prefs.foodEntries.first())
}

/**
 * Pure Recents collapse used by [FoodRepository.recent] — newest-first,
 * one row per [FoodEntry.favoriteKey].
 */
internal fun recentFoodTemplates(entries: List<FoodEntry>, limit: Int = 50): List<FoodEntry> {
    val seen = mutableSetOf<String>()
    return entries
        .sortedByDescending { it.timestamp }
        .filter { it.favoriteKey.isNotEmpty() && seen.add(it.favoriteKey) }
        .take(limit)
}

/**
 * Pure Frequent aggregation used by [FoodRepository.frequent].
 */
internal fun frequentFoodGroups(entries: List<FoodEntry>): List<FrequentFoodGroup> {
    val aggregates = mutableMapOf<String, Pair<Int, FoodEntry>>()
    for (entry in entries) {
        val key = entry.favoriteKey
        if (key.isEmpty()) continue
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

/**
 * Avoid accidental Saved Meals identity collisions for brand-new foods
 * (scan / AI / manual). If [desired] already exists (case-insensitive),
 * returns "Name (2)", "Name (3)", … based on the stem without a trailing
 * numeric suffix. Relogs should keep the original name so servings merge.
 */
fun disambiguateFoodName(desired: String, existingKeys: Set<String>): String {
    val trimmed = desired.trim()
    if (trimmed.isEmpty()) return desired
    val key = trimmed.lowercase()
    if (key !in existingKeys) return trimmed

    val stem = TRAILING_NUMERIC_SUFFIX.replace(trimmed, "").trim().ifEmpty { trimmed }
    var n = 2
    while (n <= 10_000) {
        val candidate = "$stem ($n)"
        if (candidate.lowercase() !in existingKeys) return candidate
        n++
    }
    return "$stem ($n)"
}

private val TRAILING_NUMERIC_SUFFIX = Regex("""\s+\((\d+)\)$""")

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
