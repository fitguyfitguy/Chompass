package org.codeberg.fitguy.nofud.services.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

/** Downscale oversized JPEGs before vision API upload; local full-res storage is unchanged. */
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
