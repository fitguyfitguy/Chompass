package app.chompass.services.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.chompass.services.scaledToMaxDimension
import app.chompass.services.withExifOrientation
import java.io.ByteArrayOutputStream

/** Normalize uploads to real JPEG for the vision APIs; local full-res storage is unchanged. */
object AiImageBytes {
    const val UPLOAD_MAX_DIMENSION = 1600
    const val UPLOAD_JPEG_QUALITY = 78

    /**
     * Normalizes [bytes] to a JPEG at most [maxDimension] on the longest side.
     * Every client labels its payload image/jpeg. Every decodable image
     * (HEIC, PNG, WebP, JPEG of any size) is decoded bounds-first — the full
     * bitmap is NEVER decoded when the source is oversized, so a hostile
     * huge-dimension shared image cannot OOM the process (the analysis path
     * receives raw staged bytes, not the re-encoded store copy) — rotated per
     * its EXIF orientation (BitmapFactory drops it) and re-encoded as JPEG.
     * Undecodable bytes throw [AiError.ImageConversionFailed] instead of
     * passing through mislabeled.
     *
     * #122 follow-up: the old small-JPEG pass-through let gallery/share
     * originals keep their EXIF (GPS included) on the way to cloud AI.
     * Everything is now decoded and re-encoded, so no metadata leaves the
     * device — matching the store, export, and sync copies.
     */
    fun jpegForUpload(
        bytes: ByteArray,
        maxDimension: Int = UPLOAD_MAX_DIMENSION,
        quality: Int = UPLOAD_JPEG_QUALITY,
    ): ByteArray {
        if (bytes.isEmpty()) return bytes

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw AiError.ImageConversionFailed

        val sample = sampleSizeFor(maxOf(bounds.outWidth, bounds.outHeight), maxDimension)
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: throw AiError.ImageConversionFailed
        val upright = decoded.withExifOrientation(bytes)
        if (upright !== decoded) decoded.recycle()
        val scaled = upright.scaledToMaxDimension(maxDimension)
        if (scaled !== upright) upright.recycle()
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
