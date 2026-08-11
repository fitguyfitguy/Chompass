package app.chompass.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

/**
 * Local food-photo cache. Port of iOS FoodImageStore.
 * JPEGs live under filesDir/fudai-food-images/{uuid}.jpg so they stay out of
 * the DataStore blob (which would otherwise inflate past quick-read limits).
 */
class FoodImageStore(context: Context) {
    private val dir: File = File(context.filesDir, DIR_NAME).apply { mkdirs() }
    private val thumbnailDir: File = File(context.filesDir, THUMBNAIL_DIR_NAME).apply { mkdirs() }
    private val thumbnailCache = object : LruCache<String, Bitmap>(THUMBNAIL_CACHE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    /** Writes the bitmap as JPEG under a new filename. Returns filename or null. */
    fun store(bitmap: Bitmap, entryId: UUID): String? = runCatching {
        val filename = "${entryId}.jpg"
        val full = bitmap.scaledToMaxDimension(FULL_IMAGE_MAX_DIMENSION)
        FileOutputStream(File(dir, filename)).use { out ->
            full.compress(Bitmap.CompressFormat.JPEG, FULL_IMAGE_JPEG_QUALITY, out)
        }
        runCatching { writeThumbnail(filename, full) }
        filename
    }.getOrNull()

    /**
     * Persists [bytes] as a capped JPEG plus a 320px thumbnail. Camera/gallery
     * shots are downscaled on write so the log does not retain multi-megapixel
     * originals on disk.
     */
    fun storeBytes(bytes: ByteArray, entryId: UUID): String? = runCatching {
        val filename = "${entryId}.jpg"
        val fullBitmap = decodeSampled(bytes, FULL_IMAGE_MAX_DIMENSION)
        if (fullBitmap != null) {
            FileOutputStream(File(dir, filename)).use { out ->
                fullBitmap.compress(Bitmap.CompressFormat.JPEG, FULL_IMAGE_JPEG_QUALITY, out)
            }
            runCatching { writeThumbnail(filename, fullBitmap) }
        } else {
            File(dir, filename).writeBytes(bytes)
            runCatching {
                decodeSampled(bytes, THUMBNAIL_MAX_DIMENSION)?.let { writeThumbnail(filename, it) }
            }
        }
        filename
    }.getOrNull()

    fun load(filename: String): Bitmap? =
        runCatching { BitmapFactory.decodeFile(File(dir, filename).absolutePath) }.getOrNull()

    fun loadThumbnail(filename: String, maxDimension: Int = THUMBNAIL_MAX_DIMENSION): Bitmap? {
        val key = "$filename:$maxDimension"
        thumbnailCache.get(key)?.takeUnless { it.isRecycled }?.let { return it }

        val thumbFile = File(thumbnailDir, filename)
        val bitmap = when {
            thumbFile.exists() -> runCatching {
                BitmapFactory.decodeFile(thumbFile.absolutePath)
            }.getOrNull()
            else -> runCatching {
                val fullFile = File(dir, filename)
                decodeSampled(fullFile, maxDimension)?.also { writeThumbnail(filename, it) }
            }.getOrNull()
        }

        if (bitmap != null) thumbnailCache.put(key, bitmap)
        return bitmap
    }

    fun file(filename: String): File = File(dir, filename)

    fun delete(filename: String) {
        runCatching { File(dir, filename).delete() }
        runCatching { File(thumbnailDir, filename).delete() }
        evictThumbnails(filename)
    }

    fun clearAll() {
        dir.listFiles()?.forEach { runCatching { it.delete() } }
        thumbnailDir.listFiles()?.forEach { runCatching { it.delete() } }
        thumbnailCache.evictAll()
    }

    /**
     * Removes only image files that are no longer referenced by persisted app data.
     * Callers must include food-log, saved-meal, and pending-draft filenames in
     * [referencedFilenames]. Safe to run repeatedly, including at startup.
     */
    fun pruneUnreferenced(referencedFilenames: Set<String>) {
        val referenced = referencedFilenames.mapTo(mutableSetOf()) { File(it).name }

        dir.listFiles()
            ?.filter { it.isFile && it.name !in referenced }
            ?.forEach { delete(it.name) }

        // A crash can leave a thumbnail without its full-size image.
        thumbnailDir.listFiles()
            ?.filter { it.isFile && it.name !in referenced }
            ?.forEach { file ->
                runCatching { file.delete() }
                evictThumbnails(file.name)
            }
    }

    private fun writeThumbnail(filename: String, bitmap: Bitmap) {
        val thumb = bitmap.scaledToMaxDimension(THUMBNAIL_MAX_DIMENSION)
        FileOutputStream(File(thumbnailDir, filename)).use { out ->
            thumb.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_JPEG_QUALITY, out)
        }
        thumbnailCache.put("$filename:$THUMBNAIL_MAX_DIMENSION", thumb)
    }

    private fun decodeSampled(file: File, maxDimension: Int): Bitmap? {
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val sampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxDimension)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeFile(file.absolutePath, options)?.scaledToMaxDimension(maxDimension)
    }



    /**
     * Decodes [bytes] down to [maxDimension], then bakes the EXIF orientation in:
     * BitmapFactory ignores it, so camera/gallery JPEGs would otherwise be stored
     * (and shown) rotated 90°/180°.
     */
    private fun decodeSampled(bytes: ByteArray, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val sampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxDimension)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?.scaledToMaxDimension(maxDimension)
            ?.withExifOrientation(bytes)
    }

    private fun sampleSizeFor(width: Int, height: Int, maxDimension: Int): Int {
        val largest = max(width, height)
        if (largest <= maxDimension || largest <= 0) return 1
        var sampleSize = 1
        while (largest / (sampleSize * 2) >= maxDimension) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun Bitmap.scaledToMaxDimension(maxDimension: Int): Bitmap {
        val largest = max(width, height)
        if (largest <= maxDimension || largest <= 0) return this
        val scale = maxDimension.toFloat() / largest.toFloat()
        val targetWidth = (width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
    }

    private fun evictThumbnails(filename: String) {
        for (key in thumbnailCache.snapshot().keys) {
            if (key.startsWith("$filename:")) thumbnailCache.remove(key)
        }
    }

    companion object {
        private const val DIR_NAME = "fudai-food-images"
        private const val THUMBNAIL_DIR_NAME = "fudai-food-thumbnails"
        /** Longest edge for stored full images (edit sheet is 240dp). */
        const val FULL_IMAGE_MAX_DIMENSION = 1600
        const val FULL_IMAGE_JPEG_QUALITY = 80
        private const val THUMBNAIL_MAX_DIMENSION = 320
        private const val THUMBNAIL_JPEG_QUALITY = 76
        private const val THUMBNAIL_CACHE_KB = 12 * 1024
    }
}

/**
 * Rotates [Bitmap] per the EXIF orientation in [bytes] (no-op for upright
 * images). BitmapFactory drops EXIF, so raw camera/gallery JPEGs decode
 * rotated; baking the rotation in here keeps stored JPEGs upright.
 */
internal fun Bitmap.withExifOrientation(bytes: ByteArray): Bitmap = runCatching {
    val orientation = android.media.ExifInterface(ByteArrayInputStream(bytes))
        .getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)
    when (orientation) {
        android.media.ExifInterface.ORIENTATION_ROTATE_90 -> withRotation(90f)
        android.media.ExifInterface.ORIENTATION_ROTATE_180 -> withRotation(180f)
        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> withRotation(270f)
        else -> this
    }
}.getOrElse { this }

private fun Bitmap.withRotation(degrees: Float): Bitmap {
    val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}
