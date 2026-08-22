package app.chompass.services.ondevice

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelDownloadStorageTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun occupiedBytes_countsModelsPartAndCache() {
        val models = tmp.newFolder("models")
        val cache = tmp.newFolder("cache")
        File(models, "gemma.litertlm.part").writeBytes(ByteArray(100))
        File(models, "gemma.litertlm.part.sha256").writeText("ab")
        File(File(cache, "litert").apply { mkdirs() }, "kernel.bin").writeBytes(ByteArray(40))
        File(File(cache, "litert-mtp").apply { mkdirs() }, "mtp.bin").writeBytes(ByteArray(10))
        assertEquals(152L, ModelDownloadStorage.occupiedBytes(models, cache))
    }

    @Test
    fun deleteAll_removesLegacyFilenamesToo() {
        val models = tmp.newFolder("models-legacy")
        val cache = tmp.newFolder("cache-legacy")
        File(models, "gemma-4-E2B-it.litertlm").writeBytes(ByteArray(50))
        File(models, "gemma-4-E2B-it.litertlm.part").writeBytes(ByteArray(80))
        ModelDownloadStorage.deleteAll(models, cache)
        assertEquals(0L, ModelDownloadStorage.occupiedBytes(models, cache))
    }

    @Test
    fun deleteAll_removesModelsAndCaches() {
        val models = tmp.newFolder("models")
        val cache = tmp.newFolder("cache")
        File(models, "gemma.litertlm.part").writeBytes(ByteArray(100))
        val litert = File(cache, "litert").apply { mkdirs() }
        File(litert, "kernel.bin").writeBytes(ByteArray(40))
        ModelDownloadStorage.deleteAll(models, cache)
        assertEquals(0, models.listFiles()?.size ?: 0)
        assertFalse(litert.exists())
        assertEquals(0L, ModelDownloadStorage.occupiedBytes(models, cache))
        assertTrue(models.exists())
    }
}
