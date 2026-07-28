package app.chompass.services

import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import zxingcpp.BarcodeReader

/**
 * Still-image barcode decode for AI photo inputs (not the live camera scanner).
 * Best-effort: returns empty on miss / decode failure.
 */
object BarcodeImageDecoder {
    private const val MAX_IMAGES = 10
    private val readerMutex = Mutex()

    private val reader by lazy {
        BarcodeReader(
            BarcodeReader.Options().apply {
                formats = setOf(BarcodeReader.Format.EAN_UPC)
                tryHarder = true
                tryRotate = true
                tryInvert = true
                tryDownscale = true
                textMode = BarcodeReader.TextMode.PLAIN
            }
        )
    }

    /**
     * Decodes distinct barcode strings from JPEG/PNG bytes.
     * Decodes images in parallel; reader calls are serialized (zxing-cpp is not thread-safe).
     * Never throws to callers.
     */
    suspend fun decodeAll(imageBytesList: List<ByteArray>): List<String> = coroutineScope {
        val images = imageBytesList.asSequence().filter { it.isNotEmpty() }.take(MAX_IMAGES).toList()
        if (images.isEmpty()) return@coroutineScope emptyList()
        val found = LinkedHashSet<String>()
        images.map { bytes ->
            async(Dispatchers.Default) { decodeOne(bytes) }
        }.awaitAll().forEach { found.addAll(it) }
        found.toList()
    }

    /** Decode barcodes from a single image; empty on failure. */
    suspend fun decodeOne(imageBytes: ByteArray): List<String> {
        if (imageBytes.isEmpty()) return emptyList()
        return runCatching {
            val bitmap = withContext(Dispatchers.Default) {
                BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            } ?: return emptyList()
            try {
                readerMutex.withLock {
                    reader.read(bitmap)
                        .mapNotNull { it.text?.trim()?.takeIf(String::isNotEmpty) }
                        .distinct()
                }
            } finally {
                bitmap.recycle()
            }
        }.getOrElse { emptyList() }
    }
}
