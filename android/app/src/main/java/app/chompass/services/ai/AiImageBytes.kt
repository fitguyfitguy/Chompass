package app.chompass.services.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

/** Downscale oversized JPEGs before vision API upload; local full-res storage is unchanged. */
object AiImageBytes {
    const val UPLOAD_MAX_DIMENSION = 1600
    const val UPLOAD_JPEG_QUALITY = 78

    /**
     * Downscales [bytes] to [maxDimension] for upload. Bounds-first sampling:
     * the full bitmap is NEVER decoded when the source is oversized, so a
     * hostile huge-dimension shared image cannot OOM the process (the analysis
     * path receives raw staged bytes, not the re-encoded store copy).
     */
    fun jpegForUpload(
        bytes: ByteArray,
        maxDimension: Int = UPLOAD_MAX_DIMENSION,
        quality: Int = UPLOAD_JPEG_QUALITY,
    ): ByteArray {
        if (bytes.isEmpty()) return bytes

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return bytes

        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= maxDimension) return bytes

        val sample = sampleSizeFor(longest, maxDimension)
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return bytes
        val decodedLongest = maxOf(bitmap.width, bitmap.height)
        val scale = maxDimension.toFloat() / decodedLongest.coerceAtLeast(1).toFloat()
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== bitmap) bitmap.recycle()
        return ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), out)
            out.toByteArray()
        }
    }

    /** Power-of-two sample so the decoded bitmap stays near [maxDimension]*2. */
    private fun sampleSizeFor(longest: Int, maxDimension: Int): Int {
        var sample = 1
        while (longest / (sample * 2) >= maxDimension) {
            sample *= 2
        }
        return sample
    }
}
