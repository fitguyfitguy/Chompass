package app.chompass.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import app.chompass.models.FoodEntry
import app.chompass.models.PendingFoodAnalysisDraft
import app.chompass.models.PendingFoodInputDraft
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer

// -- Food entries (bucketed by calendar month) -------------------------
    //
    // Entries are stored one JSON blob per calendar month (key
    // "foodEntries_2026-07") instead of one blob for all history, so adding/
    // editing/deleting a single entry only decodes+encodes the entries in
    // its own month, not every entry ever logged (which used to cost ~1s+
    // per save and grow linearly forever — see PerfLog "save"/"dataStore").
    // Legacy single-blob data (key "foodEntries") is migrated into buckets
    // once via [migrateFoodEntriesToBucketsIfNeededImpl].

private fun PreferencesStore.decodeEntryListImpl(raw: String?): List<FoodEntry> =
        raw?.let { runCatching { json.decodeFromString(ListSerializer(FoodEntry.serializer()), it) }.getOrNull() }
            ?: emptyList()

private fun PreferencesStore.encodeEntryListImpl(entries: List<FoodEntry>): String =
        json.encodeToString(ListSerializer(FoodEntry.serializer()), entries)

private fun PreferencesStore.foodEntriesForBucketRawImpl(month: YearMonth): Flow<List<FoodEntry>> = dataStore.data.map { prefs ->
        decodeEntryListImpl(prefs[Keys.foodEntriesBucket(month)])
    }

    /** Full history, reconstructed by merging every foodEntries_* bucket. O(n) —
     *  only meant for whole-history consumers (recent/frequent/favorites-migration/etc). */
private val PreferencesStore.allFoodEntriesRawImpl: Flow<List<FoodEntry>> get() = dataStore.data.map { prefs ->
        prefs.asMap().entries
            .filter { it.key.name.startsWith(FOOD_ENTRIES_BUCKET_PREFIX) }
            .flatMap { decodeEntryListImpl(it.value as? String) }
    }

    /** Whole food log across all months. Gated on the one-time bucket migration. */
internal val PreferencesStore.foodEntriesImpl: Flow<List<FoodEntry>> get() = flow {
        migrateFoodEntriesToBucketsIfNeededImpl()
        emitAll(allFoodEntriesRawImpl)
    }

    /** One calendar month's entries only, gated on migration — the fast path for date-scoped reads. */
internal fun PreferencesStore.foodEntriesForMonthImpl(month: YearMonth): Flow<List<FoodEntry>> = flow {
        migrateFoodEntriesToBucketsIfNeededImpl()
        emitAll(foodEntriesForBucketRawImpl(month))
    }

    /** Decode only the named month buckets (missing keys → empty). One DataStore map. */
internal fun PreferencesStore.foodEntriesForMonthsImpl(months: Collection<YearMonth>): Flow<List<FoodEntry>> = flow {
        migrateFoodEntriesToBucketsIfNeededImpl()
        val ordered = months.toList()
        emitAll(dataStore.data.map { prefs ->
            ordered.flatMap { month -> decodeEntryListImpl(prefs[Keys.foodEntriesBucket(month)]) }
        })
    }

    /**
     * Applies upserts (by id) and/or removals (by id) to exactly the named
     * month buckets, in one atomic DataStore edit — so a cross-month move
     * (remove from old bucket + insert into new) can never be observed
     * half-applied. A bucket that ends up empty is removed entirely, so key
     * count stays bounded (~12/year) rather than growing without limit.
     *
     * When [draft] is non-null it is written in the same transaction, so the
     * crash-safety snapshot of a confirming AI entry commits atomically with
     * the diary row instead of costing a second full-file DataStore write.
     * When [clearDraft] is true the pending draft key is removed in the same
     * transaction — used at Log-commit time so clearing the consumed draft
     * never costs a second full-file DataStore write.
     */
internal suspend fun PreferencesStore.applyFoodEntryBucketChangesImpl(
        upsertsByMonth: Map<YearMonth, List<FoodEntry>> = emptyMap(),
        removalIdsByMonth: Map<YearMonth, Set<UUID>> = emptyMap(),
        draft: PendingFoodAnalysisDraft? = null,
        clearDraft: Boolean = false,
    ) {
        if (upsertsByMonth.isEmpty() && removalIdsByMonth.isEmpty() && draft == null && !clearDraft) return
        dataStore.edit { prefs ->
            for (month in upsertsByMonth.keys + removalIdsByMonth.keys) {
                mergeFoodEntryMonth(prefs, month, upsertsByMonth[month].orEmpty(), removalIdsByMonth[month].orEmpty())
            }
            if (draft != null) {
                prefs[Keys.PENDING_FOOD_ANALYSIS_DRAFT] =
                    json.encodeToString(PendingFoodAnalysisDraft.serializer(), draft)
            } else if (clearDraft) {
                prefs.remove(Keys.PENDING_FOOD_ANALYSIS_DRAFT)
            }
        }
    }

    /** Merges one month's upserts/removals into an in-flight DataStore edit. */
private fun PreferencesStore.mergeFoodEntryMonth(
        prefs: MutablePreferences,
        month: YearMonth,
        upserts: List<FoodEntry>,
        removals: Set<UUID>,
    ) {
        val key = Keys.foodEntriesBucket(month)
        val existing = decodeEntryListImpl(prefs[key])
        val kept = if (removals.isEmpty()) existing else existing.filterNot { it.id in removals }
        val byId = kept.associateByTo(LinkedHashMap()) { it.id }
        for (entry in upserts) byId[entry.id] = entry
        val merged = byId.values.sortedBy { it.timestamp }
        if (merged.isEmpty()) prefs.remove(key) else prefs[key] = encodeEntryListImpl(merged)
    }

    /** Full replace (reseed / clear-all) — wipes every existing bucket and regroups [entries] by month. */
internal suspend fun PreferencesStore.replaceAllFoodEntriesImpl(entries: List<FoodEntry>) {
        dataStore.edit { prefs ->
            prefs.asMap().keys.filter { it.name.startsWith(FOOD_ENTRIES_BUCKET_PREFIX) }.forEach { prefs.remove(it) }
            prefs.remove(Keys.FOOD_ENTRIES)
            prefs[Keys.FOOD_ENTRIES_MIGRATED] = true
            entries.groupBy { YearMonth.from(it.timestamp.atZone(ZoneId.systemDefault())) }
                .forEach { (month, monthEntries) ->
                    prefs[Keys.foodEntriesBucket(month)] = encodeEntryListImpl(monthEntries.sortedBy { it.timestamp })
                }
        }
    }

    /**
     * One-time migration of the legacy single-blob `foodEntries` key into
     * monthly buckets. Idempotent: a fast no-op read once already migrated,
     * otherwise a single atomic edit. DataStore's edit() commits via atomic
     * file rename, so this can never leave a partially-migrated state on
     * disk, and it's safe to re-run from scratch if interrupted.
     */
private suspend fun PreferencesStore.migrateFoodEntriesToBucketsIfNeededImpl() {
        if (dataStore.data.first()[Keys.FOOD_ENTRIES_MIGRATED] == true) return
        dataStore.edit { prefs ->
            if (prefs[Keys.FOOD_ENTRIES_MIGRATED] == true) return@edit
            val legacy = decodeEntryListImpl(prefs[Keys.FOOD_ENTRIES])
            legacy.groupBy { YearMonth.from(it.timestamp.atZone(ZoneId.systemDefault())) }
                .forEach { (month, monthEntries) ->
                    val key = Keys.foodEntriesBucket(month)
                    val existing = decodeEntryListImpl(prefs[key])
                    val merged = (existing + monthEntries).distinctBy { it.id }.sortedBy { it.timestamp }
                    prefs[key] = encodeEntryListImpl(merged)
                }
            prefs.remove(Keys.FOOD_ENTRIES)
            prefs[Keys.FOOD_ENTRIES_MIGRATED] = true
        }
    }

internal val PreferencesStore.favoriteKeysImpl: Flow<Set<String>> get() = dataStore.data.map { prefs ->
        prefs[Keys.FAVORITE_KEYS]?.let {
            runCatching { json.decodeFromString(SetSerializer(String.serializer()), it) }.getOrNull()
        } ?: emptySet()
    }

internal suspend fun PreferencesStore.setFavoriteKeysImpl(keys: Set<String>) {
        dataStore.edit { it[Keys.FAVORITE_KEYS] = json.encodeToString(SetSerializer(String.serializer()), keys) }
    }

    /**
     * Ordered list of favorite FoodEntry copies — mirrors iOS UserDefaults
     * key "favoriteFoodEntries". Stored as a separate copy (not a reference
     * into [foodEntries]) so a favorite survives deletion of the original
     * log entry, AND so user-defined order is preserved across restarts.
     */
internal val PreferencesStore.favoriteFoodEntriesImpl: Flow<List<FoodEntry>>
    get() = listPref(Keys.FAVORITE_ENTRIES, FoodEntry.serializer())

internal suspend fun PreferencesStore.setFavoriteFoodEntriesImpl(entries: List<FoodEntry>) =
    setListPref(Keys.FAVORITE_ENTRIES, FoodEntry.serializer(), entries)

// -- Pending food analysis draft --------------------------------------
internal val PreferencesStore.pendingFoodAnalysisDraftImpl: Flow<PendingFoodAnalysisDraft?>
    get() = objectPref(Keys.PENDING_FOOD_ANALYSIS_DRAFT, PendingFoodAnalysisDraft.serializer())

internal suspend fun PreferencesStore.setPendingFoodAnalysisDraftImpl(draft: PendingFoodAnalysisDraft?) =
    setObjectPrefOrRemove(Keys.PENDING_FOOD_ANALYSIS_DRAFT, PendingFoodAnalysisDraft.serializer(), draft)

// -- Pending food input draft (failed camera+note input) --------------
internal val PreferencesStore.pendingFoodInputDraftImpl: Flow<PendingFoodInputDraft?>
    get() = objectPref(Keys.PENDING_FOOD_INPUT_DRAFT, PendingFoodInputDraft.serializer())

internal suspend fun PreferencesStore.setPendingFoodInputDraftImpl(draft: PendingFoodInputDraft?) =
    setObjectPrefOrRemove(Keys.PENDING_FOOD_INPUT_DRAFT, PendingFoodInputDraft.serializer(), draft)

/**
 * Returns an atomic snapshot of every persisted food-image reference. A decode
 * failure returns null so cleanup never treats unreadable user data as empty.
 */
internal suspend fun PreferencesStore.foodImageReferenceFilenamesImpl(): Set<String>? {
    migrateFoodEntriesToBucketsIfNeededImpl()
    val prefs = dataStore.data.first()
    val foods = prefs.asMap().entries
        .filter { it.key.name.startsWith(FOOD_ENTRIES_BUCKET_PREFIX) }
        .flatMap { decodeEntryListImpl(it.value as? String) }
    val favorites = prefs[Keys.FAVORITE_ENTRIES]?.let { raw ->
        runCatching {
            json.decodeFromString(ListSerializer(FoodEntry.serializer()), raw)
        }.getOrNull() ?: return null
    }.orEmpty()
    val analysisDraft = prefs[Keys.PENDING_FOOD_ANALYSIS_DRAFT]?.let { raw ->
        runCatching { json.decodeFromString<PendingFoodAnalysisDraft>(raw) }.getOrNull()
            ?: return null
    }
    val inputDraft = prefs[Keys.PENDING_FOOD_INPUT_DRAFT]?.let { raw ->
        runCatching { json.decodeFromString<PendingFoodInputDraft>(raw) }.getOrNull()
            ?: return null
    }

    return buildSet {
        foods.forEach { entry -> entry.imageFilename?.let { add(it) } }
        favorites.forEach { entry -> entry.imageFilename?.let { add(it) } }
        analysisDraft?.imageFilename?.let { add(it) }
        inputDraft?.imageFilename?.let { add(it) }
    }
}
