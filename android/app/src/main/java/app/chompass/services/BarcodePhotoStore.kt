package app.chompass.services

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Disk cache for Open Food Facts product front photos, keyed by the
 * normalized barcode code (the same key as the analysis cache in
 * [app.chompass.data.PreferencesStoreBarcodeCache]). The first successful
 * scan stores the photo; later scans of the same code render it from disk
 * with no network request (Codeberg #160).
 *
 * Entries never expire on their own — the analysis cache never expires, and
 * a photo outliving its entry would only re-download. GC is monotone via
 * [retain]: the lookup path calls it with the cache keys that survived LRU
 * eviction, and photos whose key is gone are deleted (a deleted-and-still-
 * wanted photo just re-downloads).
 */
class BarcodePhotoStore(appContext: Context) {
    private val dir = File(appContext.filesDir, "barcode_photos")

    private fun fileFor(code: String) = File(dir, "$code.jpg")

    /** Stored photo bytes for [code], or null when absent/unreadable. */
    suspend fun get(code: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = fileFor(code)
        if (!file.isFile) return@withContext null
        runCatching { file.readBytes() }.getOrNull()
    }

    /** Persists [bytes] under [code]; atomic rename so a torn file never serves. */
    suspend fun put(code: String, bytes: ByteArray): Unit = withContext(Dispatchers.IO) {
        runCatching {
            dir.mkdirs()
            val target = fileFor(code)
            val part = File(dir, "${target.name}.part")
            part.writeBytes(bytes)
            if (!part.renameTo(target)) {
                target.writeBytes(bytes)
                part.delete()
            }
        }
    }

    /**
     * Deletes every stored photo whose code is not in [keptCodes], including
     * stray `.part` leftovers. Best-effort: individual failures are ignored.
     */
    suspend fun retain(keptCodes: Set<String>): Unit = withContext(Dispatchers.IO) {
        val files = runCatching { dir.listFiles() }.getOrNull() ?: return@withContext
        for (file in files) {
            if (file.nameWithoutExtension !in keptCodes) {
                runCatching { file.delete() }
            }
        }
    }
}
