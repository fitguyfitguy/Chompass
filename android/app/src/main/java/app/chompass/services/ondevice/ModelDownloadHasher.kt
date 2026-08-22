package app.chompass.services.ondevice

import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException

/**
 * SHA-256 helpers for [ModelDownloadWorker]. The digest is updated while
 * bytes are written so a finished stream does not need a second multi-GB
 * read just to verify (that re-read was a crash / LMK window at 100%).
 *
 * A `.part.sha256` sidecar stores the hex digest once hashing finishes, so
 * a process death between hash and rename can skip the re-read on retry.
 */
internal object ModelDownloadHasher {
    fun sidecar(partFile: File): File = File("${partFile.path}.sha256")

    fun writeSidecar(partFile: File, hex: String) {
        sidecar(partFile).writeText(hex.lowercase())
    }

    fun readSidecar(partFile: File): String? =
        sidecar(partFile).takeIf { it.isFile }
            ?.readText()
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.length == 64 && it.all { ch -> ch in HEX } }

    fun deleteSidecar(partFile: File) {
        sidecar(partFile).delete()
    }

    fun hex(digest: MessageDigest): String =
        digest.digest().joinToString("") { "%02x".format(it) }

    fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        updateFromFile(digest, file)
        return hex(digest)
    }

    fun updateFromFile(
        digest: MessageDigest,
        file: File,
        byteCount: Long = file.length(),
        isActive: () -> Boolean = { true },
    ) {
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            var left = byteCount.coerceAtLeast(0L)
            while (left > 0L) {
                if (!isActive()) throw CancellationException()
                val n = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                if (n < 0) break
                digest.update(buffer, 0, n)
                left -= n
            }
        }
    }

    private val HEX = "0123456789abcdef".toSet()
}
