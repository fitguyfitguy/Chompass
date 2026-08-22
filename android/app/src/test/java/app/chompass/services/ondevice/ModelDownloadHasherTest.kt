package app.chompass.services.ondevice

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelDownloadHasherTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun sha256Hex_matchesKnownVector() {
        val file = tmp.newFile("abc.bin")
        file.writeBytes("abc".toByteArray())
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            ModelDownloadHasher.sha256Hex(file),
        )
    }

    @Test
    fun updateFromFile_prefixThenRest_matchesFullHash() {
        val bytes = "abcdefghij".toByteArray()
        val file = tmp.newFile("split.bin")
        file.writeBytes(bytes)
        val digest = MessageDigest.getInstance("SHA-256")
        ModelDownloadHasher.updateFromFile(digest, file, byteCount = 4)
        digest.update(bytes, 4, bytes.size - 4)
        assertEquals(ModelDownloadHasher.sha256Hex(file), ModelDownloadHasher.hex(digest))
    }

    @Test
    fun sidecar_roundTripAndRejectsJunk() {
        val part = tmp.newFile("model.part")
        val hex = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        assertNull(ModelDownloadHasher.readSidecar(part))
        ModelDownloadHasher.writeSidecar(part, hex.uppercase())
        assertEquals(hex, ModelDownloadHasher.readSidecar(part))
        ModelDownloadHasher.sidecar(part).writeText("not-a-hash")
        assertNull(ModelDownloadHasher.readSidecar(part))
        ModelDownloadHasher.deleteSidecar(part)
        assertNull(ModelDownloadHasher.readSidecar(part))
    }
}
