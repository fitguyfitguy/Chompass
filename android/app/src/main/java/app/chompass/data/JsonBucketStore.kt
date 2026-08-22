package app.chompass.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.time.YearMonth
import java.util.UUID

/**
 * File-backed JSON store for unbounded datasets that would otherwise bloat the
 * single DataStore preferences proto (which every `edit` rewrites in full).
 *
 * Data is split one file per calendar month (`<root>/<yyyy-MM>.json`), so a
 * single-row write only re-encodes and atomically replaces that month's file —
 * the cost stops growing with lifetime data, unlike a whole-proto rewrite.
 * Same month-bucket mental model (and migration discipline) as the food
 * buckets in PreferencesStoreFood.
 *
 * - All I/O runs on [Dispatchers.IO]; callers never block the main thread.
 * - Writes are tmp-file + atomic rename, serialized per store by a [Mutex].
 * - Reads go through an in-memory cache; flows re-emit only when the month
 *   they observe actually changes (single-process app, every writer goes
 *   through this store, so the cache cannot go stale).
 * - Decode failures are lenient (empty month), matching listPref semantics —
 *   a corrupt file must not take the app down.
 */
internal class JsonBucketStore<T>(
    private val root: File,
    private val json: Json,
    private val serializer: KSerializer<T>,
    private val idOf: (T) -> UUID,
    private val order: Comparator<T>? = null,
) {
    private val io = Dispatchers.IO
    private val mutex = Mutex()
    private val listSerializer = ListSerializer(serializer)
    private val cache = MutableStateFlow<Map<YearMonth, List<T>>>(emptyMap())

    private fun monthFile(month: YearMonth): File = File(root, "$month.json")

    // -- Reads ------------------------------------------------------------

    /**
     * Ensures [months] are in the in-memory cache, loading any missing month
     * file from disk exactly once.
     */
    suspend fun warm(months: Collection<YearMonth>) = mutex.withLock {
        val missing = months.filter { it !in cache.value }
        if (missing.isEmpty()) return
        val loaded = missing.associateWith { decodeFile(it) }
        cache.value = cache.value + loaded
    }

    /** One month's entries, from the cache (loaded on first access). */
    suspend fun readMonth(month: YearMonth): List<T> {
        warm(listOf(month))
        return cache.value[month].orEmpty()
    }

    /** Reactive single-month read; re-emits only when that month changes. */
    fun monthFlow(month: YearMonth): Flow<List<T>> = flow {
        warm(listOf(month))
        emitAll(cache.map { it[month].orEmpty() }.distinctUntilChanged())
    }

    /** Reactive merged read of exactly [months], in that order; re-emits only
     *  when one of those months changes. Used for date-window reads (e.g. the
     *  Add Food hub's 30/90-day windows) that must not decode the whole history. */
    fun monthsFlow(months: Collection<YearMonth>): Flow<List<T>> = flow {
        val ordered = months.toList()
        warm(ordered)
        emitAll(cache.map { prefs -> ordered.flatMap { prefs[it].orEmpty() } }.distinctUntilChanged())
    }

    /** Full history across every month file on disk, oldest month first. */
    suspend fun readAll(): List<T> = mutex.withLock {
        val onDisk = monthsOnDiskLocked()
        warmLocked(onDisk)
        cache.value.toSortedMap().values.flatten()
    }

    /** Reactive full-history read; re-emits when this dataset changes. */
    fun allFlow(): Flow<List<T>> = flow {
        mutex.withLock {
            warmLocked(monthsOnDiskLocked())
        }
        emitAll(cache.map { it.toSortedMap().values.flatten() })
    }

    /** Month files present on disk (sorted). */
    suspend fun monthsOnDisk(): List<YearMonth> = mutex.withLock { monthsOnDiskLocked() }

    // -- Writes -----------------------------------------------------------

    /**
     * Merges upserts (by id) and/or removals (by id) into exactly the named
     * months, writing only the files that actually changed. A month whose
     * merged content is empty is removed from disk. No-op months (e.g. an
     * import that changes nothing) skip file I/O entirely.
     */
    suspend fun applyChanges(
        upsertsByMonth: Map<YearMonth, List<T>> = emptyMap(),
        removalIdsByMonth: Map<YearMonth, Set<UUID>> = emptyMap(),
    ) {
        if (upsertsByMonth.isEmpty() && removalIdsByMonth.isEmpty()) return
        mutex.withLock {
            val months = upsertsByMonth.keys + removalIdsByMonth.keys
            // Merge against what is actually on disk, not an empty default:
            // a month that was never warmed must load its file first.
            warmLocked(months.toList())
            var next = cache.value
            for (month in months) {
                val existing = cache.value[month].orEmpty()
                val removals = removalIdsByMonth[month].orEmpty()
                val kept = if (removals.isEmpty()) existing else existing.filterNot { idOf(it) in removals }
                val upserts = upsertsByMonth[month].orEmpty()
                val byId = kept.associateByTo(LinkedHashMap()) { idOf(it) }
                for (entry in upserts) byId[idOf(entry)] = entry
                val merged = if (order != null) byId.values.sortedWith(order) else byId.values.toList()
                if (merged == existing) continue
                writeFileLocked(month, merged)
                next = if (merged.isEmpty()) next - month else next + (month to merged)
            }
            cache.value = next
        }
    }

    /** Full dataset replace: [byMonth] becomes the entire content. Months on
     *  disk that are absent from [byMonth] are deleted. */
    suspend fun replaceAll(byMonth: Map<YearMonth, List<T>>) = mutex.withLock {
        val onDisk = monthsOnDiskLocked()
        for (month in onDisk - byMonth.keys) monthFile(month).delete()
        for ((month, entries) in byMonth) writeFileLocked(month, entries)
        cache.value = byMonth.mapValues { if (order != null) it.value.sortedWith(order) else it.value }
    }

    /** Deletes every month file; the dataset is empty afterwards. */
    suspend fun clear() = mutex.withLock {
        root.listFiles()?.forEach { it.delete() }
        cache.value = emptyMap()
    }

    // -- Internals (callers hold the mutex) -------------------------------

    private suspend fun warmLocked(months: List<YearMonth>) {
        val missing = months.filter { it !in cache.value }
        if (missing.isEmpty()) return
        cache.value = cache.value + missing.associateWith { decodeFile(it) }
    }

    private suspend fun monthsOnDiskLocked(): List<YearMonth> = withContext(io) {
        root.listFiles()
            ?.mapNotNull { file ->
                if (!file.isFile) return@mapNotNull null
                file.name.removeSuffix(".json").let {
                    runCatching { YearMonth.parse(it) }.getOrNull()
                }
            }
            ?.sorted()
            ?: emptyList()
    }

    private suspend fun decodeFile(month: YearMonth): List<T> = withContext(io) {
        val file = monthFile(month)
        if (!file.isFile) return@withContext emptyList()
        runCatching { json.decodeFromString(listSerializer, file.readText()) }
            .getOrElse { emptyList() }
    }

    private suspend fun writeFileLocked(month: YearMonth, entries: List<T>) = withContext(io) {
        if (entries.isEmpty()) {
            monthFile(month).delete()
            return@withContext
        }
        root.mkdirs()
        val target = monthFile(month)
        val tmp = File(root, "$month.json.tmp")
        tmp.writeText(json.encodeToString(listSerializer, entries))
        // renameTo is an atomic rename(2) on Android and replaces the target;
        // a stale tmp from a crashed write is ignored by every reader.
        if (!tmp.renameTo(target)) {
            throw IOException("bucket write failed for $target")
        }
    }
}
