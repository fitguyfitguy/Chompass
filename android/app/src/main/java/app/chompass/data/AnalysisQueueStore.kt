package app.chompass.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.chompass.models.QueuedAnalysis
import app.chompass.models.QueueStatus
import app.chompass.services.ai.FoodAnalysis
import app.chompass.services.decodeSampledBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.max

/**
 * Persisted analysis queue + prompt history (Codeberg #53). One JSON file
 * (`filesDir/chompass-analysis-queue.json`) holds the entries (a few KB —
 * photos never enter the JSON); JPEGs live in `filesDir/fudai-queue-images/`
 * referenced by filename. A dedicated image dir (NOT [app.chompass.services.FoodImageStore])
 * keeps queued photos immune to `pruneUnreferenced` by construction.
 *
 * Same discipline as [JsonBucketStore]: all I/O on [Dispatchers.IO], writes
 * tmp-file + atomic rename serialized by a [Mutex], in-memory cache, lenient
 * decode (corrupt file → empty, never a crash).
 *
 * Retention: DONE/FAILED history entries are pruned after
 * [HISTORY_RETENTION_DAYS]; PENDING entries (the user's intended work) are
 * never auto-pruned. Orphaned image files are deleted in the same sweep.
 */
class AnalysisQueueStore internal constructor(private val filesDir: File) {
    constructor(context: Context) : this(context.applicationContext.filesDir)

    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(QueuedAnalysis.serializer())
    private val io = Dispatchers.IO
    private val mutex = Mutex()
    private var loaded = false
    private val _entries = MutableStateFlow<List<QueuedAnalysis>>(emptyList())
    /** Entry-count LRU (no Android deps, so the store stays JVM-testable). */
    private val thumbnailCache = object : LinkedHashMap<String, Bitmap>(0, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean =
            size > THUMBNAIL_CACHE_ENTRIES
    }

    /** All entries, newest first; re-emits on every mutation. */
    val entries: StateFlow<List<QueuedAnalysis>> = _entries

    private fun file(): File = File(filesDir, FILE_NAME)

    private fun imageDir(): File = File(filesDir, DIR_NAME).apply { mkdirs() }

    // -- Reads ------------------------------------------------------------

    /** Loads the JSON once (idempotent) and applies retention pruning. */
    suspend fun ensureLoaded() = mutex.withLock {
        ensureLoadedLocked()
        pruneLocked()
    }

    private suspend fun ensureLoadedLocked() {
        if (loaded) return
        loaded = true
        val decoded = withContext(io) {
            val f = file()
            if (!f.isFile) emptyList()
            else runCatching { json.decodeFromString(listSerializer, f.readText()) }
                .getOrElse { emptyList() }
        }
        _entries.value = decoded.sortedByDescending { it.createdAt }
    }

    suspend fun item(id: UUID): QueuedAnalysis? {
        ensureLoaded()
        return _entries.value.firstOrNull { it.id == id }
    }

    // -- Writes -----------------------------------------------------------

    /** Insert-or-replace by id. */
    suspend fun upsert(entry: QueuedAnalysis) = mutex.withLock {
        ensureLoadedLocked()
        val next = _entries.value.filterNot { it.id == entry.id } + entry
        writeLocked(next)
    }

    suspend fun markDone(id: UUID, result: FoodAnalysis) = mutex.withLock {
        ensureLoadedLocked()
        val next = _entries.value.map { entry ->
            if (entry.id == id) entry.copy(status = QueueStatus.DONE, result = result, error = null)
            else entry
        }
        if (next != _entries.value) writeLocked(next)
    }

    /** Keeps the entry runnable (PENDING) with the last failure message. */
    suspend fun markFailed(id: UUID, error: String?) = mutex.withLock {
        ensureLoadedLocked()
        val next = _entries.value.map { entry ->
            if (entry.id == id) entry.copy(status = QueueStatus.PENDING, error = error)
            else entry
        }
        if (next != _entries.value) writeLocked(next)
    }

    suspend fun delete(id: UUID) = mutex.withLock {
        ensureLoadedLocked()
        val removed = _entries.value.filter { it.id == id }
        if (removed.isEmpty()) return
        deleteImageFilesLocked(removed.flatMap { it.imageFilenames })
        writeLocked(_entries.value - removed.toSet())
    }

    /** Deletes every finished (DONE/FAILED) entry and its photos. PENDING stays. */
    suspend fun clearHistory() = mutex.withLock {
        ensureLoadedLocked()
        val (pending, finished) = _entries.value.partition { it.status == QueueStatus.PENDING }
        if (finished.isEmpty()) return
        deleteImageFilesLocked(finished.flatMap { it.imageFilenames })
        writeLocked(pending)
    }

    // -- Images -----------------------------------------------------------

    /**
     * Persists [bytes] as capped JPEGs (1600px/80q — same encode as
     * [app.chompass.services.FoodImageStore]) named `<entryId>_<n>.jpg`.
     * Undecodable bytes are skipped (never stored raw). Returns the stored
     * filenames in input order.
     */
    suspend fun storeImages(entryId: UUID, bytes: List<ByteArray>): List<String> = withContext(io) {
        val dir = imageDir()
        bytes.mapIndexedNotNull { index, byteArray ->
            if (byteArray.isEmpty()) return@mapIndexedNotNull null
            val filename = "${entryId}_$index.jpg"
            val bitmap = decodeSampledBitmap(byteArray, FULL_IMAGE_MAX_DIMENSION) ?: return@mapIndexedNotNull null
            runCatching {
                FileOutputStream(File(dir, filename)).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, FULL_IMAGE_JPEG_QUALITY, out)
                }
                filename
            }.getOrNull()
        }
    }

    /** Full-size JPEG bytes for a stored filename (null when missing). */
    suspend fun loadImage(filename: String): ByteArray? = withContext(io) {
        val f = File(imageDir(), filename)
        if (!f.isFile) null else runCatching { f.readBytes() }.getOrNull()
    }

    /**
     * Sampled thumbnail decode with an LruCache (list rows never materialize
     * full-size bitmaps repeatedly). Runs on [Dispatchers.IO].
     */
    suspend fun thumbnail(filename: String, maxDimension: Int = THUMBNAIL_MAX_DIMENSION): Bitmap? {
        val key = "$filename:$maxDimension"
        thumbnailCache[key]?.takeUnless { it.isRecycled }?.let { return it }
        val bitmap = withContext(io) {
            val f = File(imageDir(), filename)
            if (!f.isFile) return@withContext null
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.absolutePath, bounds)
                val largest = max(bounds.outWidth, bounds.outHeight)
                val sampleSize = if (largest <= maxDimension || largest <= 0) 1 else {
                    var s = 1
                    while (largest / (s * 2) >= maxDimension) s *= 2
                    s
                }
                BitmapFactory.decodeFile(
                    f.absolutePath,
                    BitmapFactory.Options().apply { inSampleSize = sampleSize },
                )
            }.getOrNull()
        }
        if (bitmap != null) thumbnailCache[key] = bitmap
        return bitmap
    }

    suspend fun deleteImages(filenames: List<String>) = mutex.withLock {
        deleteImageFilesLocked(filenames)
    }

    // -- Retention --------------------------------------------------------

    /** Drops DONE/FAILED older than [retentionDays]; orphans deleted with them. */
    suspend fun prune(retentionDays: Int = HISTORY_RETENTION_DAYS) = mutex.withLock {
        ensureLoadedLocked()
        pruneLocked(retentionDays)
    }

    private suspend fun pruneLocked(retentionDays: Int = HISTORY_RETENTION_DAYS) {
        val cutoff = Instant.now().minus(retentionDays.toLong(), ChronoUnit.DAYS)
        val (kept, removed) = _entries.value.partition {
            it.status == QueueStatus.PENDING || it.createdAt >= cutoff
        }
        if (removed.isEmpty()) return
        deleteImageFilesLocked(removed.flatMap { it.imageFilenames })
        writeLocked(kept)
    }

    // -- Internals (callers hold the mutex) -------------------------------

    private suspend fun writeLocked(entries: List<QueuedAnalysis>) = withContext(io) {
        val target = file()
        val tmp = File(filesDir, "$FILE_NAME.tmp")
        tmp.writeText(json.encodeToString(listSerializer, entries))
        // renameTo is an atomic rename(2) on Android and replaces the target;
        // a stale tmp from a crashed write is ignored by every reader.
        if (!tmp.renameTo(target)) {
            throw IOException("analysis-queue write failed for $target")
        }
        _entries.value = entries.sortedByDescending { it.createdAt }
    }

    private suspend fun deleteImageFilesLocked(filenames: List<String>) = withContext(io) {
        val dir = imageDir()
        for (filename in filenames) {
            runCatching { File(dir, filename).delete() }
            thumbnailCache.remove("$filename:$THUMBNAIL_MAX_DIMENSION")
        }
    }

    companion object {
        const val FILE_NAME = "chompass-analysis-queue.json"
        const val DIR_NAME = "fudai-queue-images"
        /** DONE/FAILED history entries older than this are pruned; PENDING never. */
        const val HISTORY_RETENTION_DAYS = 7
        /** Longest edge for stored full images (same as FoodImageStore). */
        const val FULL_IMAGE_MAX_DIMENSION = 1600
        const val FULL_IMAGE_JPEG_QUALITY = 80
        private const val THUMBNAIL_MAX_DIMENSION = 320
        private const val THUMBNAIL_CACHE_ENTRIES = 32
    }
}
