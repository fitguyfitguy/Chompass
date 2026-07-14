package org.codeberg.fitguy.nofud.services.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

/**
 * Downscale oversized camera/gallery JPEGs before vision-model API calls so
 * uploads stay smaller (faster TTFB, less mobile data). Images already within
 * [UPLOAD_MAX_DIMENSION] are sent unchanged — no extra JPEG generation loss.
 * Full-resolution bytes are still stored locally via [org.codeberg.fitguy.nofud.services.FoodImageStore].
 *
 * Note: Gemini vision token cost is driven by [media resolution / pixel budget on
 * the API side](https://ai.google.dev/gemini-api/docs/media-resolution), not raw
 * file size. This helper does not change RPM/RPD rate limits.
 */
object AiImageBytes {
    const val UPLOAD_MAX_DIMENSION = 1600
    const val UPLOAD_JPEG_QUALITY = 78

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

        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
        val ratio = maxDimension.toFloat() / longest.toFloat()
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
        return ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), out)
            out.toByteArray()
        }
    }
}
