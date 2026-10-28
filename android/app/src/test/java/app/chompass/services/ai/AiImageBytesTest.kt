package app.chompass.services.ai

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Upload normalization: every AI client labels its payload image/jpeg, so
 * jpegForUpload must hand it real JPEG bytes — every input (HEIC/PNG/WebP and
 * JPEG of any size) is decoded, EXIF rotation baked in, downscaled to the
 * 1600 px cap, and re-encoded so no source metadata reaches the provider —
 * and never pass raw undecodable bytes through.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AiImageBytesTest {
    private fun jpegBytes(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.RED)
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.toByteArray()
        }
    }

    private fun pngBytes(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
    }

    private fun decodeDimensions(bytes: ByteArray): Pair<Int, Int> {
        val opts = BitmapFactory.Options()
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        return opts.outWidth to opts.outHeight
    }

    @Test
    fun realSmallJpeg_reencodedWithoutMetadata() {
        // #122 follow-up: small JPEGs used to pass through untouched, so
        // gallery/share originals carried EXIF (GPS included) to cloud AI.
        // Every upload is re-encoded and leaves metadata behind.
        val plain = jpegBytes(320, 240)
        val exifPayload = "Exif\u0000\u0000II*\u0000GPS marker".toByteArray(Charsets.US_ASCII)
        val app1 = byteArrayOf(
            0xFF.toByte(), 0xE1.toByte(),
            ((exifPayload.size + 2) shr 8).toByte(),
            ((exifPayload.size + 2) and 0xFF).toByte(),
        ) + exifPayload
        val withExif = plain.sliceArray(0..1) + app1 + plain.sliceArray(2 until plain.size)
        assertTrue(
            "test input must carry the EXIF marker",
            String(withExif, Charsets.US_ASCII).contains("Exif"),
        )

        val out = AiImageBytes.jpegForUpload(withExif)

        assertFalse("output must not carry the EXIF marker", String(out, Charsets.US_ASCII).contains("Exif"))
        assertEquals(320 to 240, decodeDimensions(out))
    }

    @Test
    fun pngBytes_reencodedAsJpeg() {
        val bytes = pngBytes(320, 240)
        assertFalse("input must not already be JPEG", bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte())

        val out = AiImageBytes.jpegForUpload(bytes)

        assertEquals(0xFF, out[0].toInt() and 0xFF)
        assertEquals(0xD8, out[1].toInt() and 0xFF)
        assertEquals(0xFF, out[2].toInt() and 0xFF)
        assertEquals(320 to 240, decodeDimensions(out))
    }

    @Test
    fun oversizedJpeg_downscaledWithinBounds() {
        val bytes = jpegBytes(2400, 1200)

        val out = AiImageBytes.jpegForUpload(bytes)

        val (width, height) = decodeDimensions(out)
        assertTrue(maxOf(width, height) <= AiImageBytes.UPLOAD_MAX_DIMENSION)
    }

    @Test
    fun exifRotatedJpeg_outputUpright() {
        // Landscape source with ROTATE_90: displays show it portrait, but
        // BitmapFactory drops the flag, so the upload must bake the rotation in.
        val bitmap = Bitmap.createBitmap(2000, 1000, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.GREEN)
        val file = File(RuntimeEnvironment.getApplication().cacheDir, "exif_test.jpg")
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
        ExifInterface(file.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val bytes = file.readBytes()

        val out = AiImageBytes.jpegForUpload(bytes)

        val (width, height) = decodeDimensions(out)
        assertTrue("expected portrait output, got ${width}x$height", height > width)
        file.delete()
    }

    @Test
    fun undecodableBytes_throwsImageConversionFailed() {
        val bytes = byteArrayOf(0x00, 0x01, 0x02, 0x03)

        assertThrows(AiError.ImageConversionFailed::class.java) {
            AiImageBytes.jpegForUpload(bytes)
        }
    }
}
