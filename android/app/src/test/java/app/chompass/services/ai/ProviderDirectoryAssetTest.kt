package app.chompass.services.ai

import app.chompass.models.AIProvider
import app.chompass.services.ai.ProviderDirectory.Companion.findEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.URI

/**
 * Release-diff trust anchor for the bundled provider directory (Codeberg
 * #143): a regressed `scripts/generate_provider_directory.py` fails
 * `./gradlew test` inside `release:package`. Locates the committed asset by
 * walking up from `user.dir`, like ParityFixtures — no new Gradle property.
 */
class ProviderDirectoryAssetTest {
    private val directory: ProviderDirectory by lazy {
        val parsed = ProviderDirectory.parse(assetFile().readText())
        assertNotNull("provider_directory.json failed to parse", parsed)
        parsed!!
    }

    private fun assetFile(): File {
        var dir = File(System.getProperty("user.dir") ?: ".")
        repeat(6) {
            val candidate = File(dir, "android/app/src/main/assets/provider_directory.json")
            if (candidate.isFile) return candidate
            dir = dir.parentFile ?: return@repeat
        }
        error("Could not locate android/app/src/main/assets/provider_directory.json")
    }

    private fun hostOf(url: String): String = URI(url).host?.lowercase().orEmpty()

    private fun isIpLiteral(host: String): Boolean {
        if (host.isEmpty()) return true
        val stripped = host.removeSurrounding("[", "]")
        val parts = stripped.split(".")
        if (parts.size == 4 && parts.all { p -> p.toIntOrNull()?.let { it in 0..255 } == true }) return true
        return stripped.contains(':')
    }

    @Test
    fun `asset parses with expected header`() {
        assertEquals(1, directory.version)
        assertEquals("https://models.dev/api.json", directory.source)
        assertTrue(directory.fetchedAt.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$")))
        assertTrue(directory.entries.isNotEmpty())
    }

    @Test
    fun `ids and baseUrls are unique`() {
        val ids = directory.entries.map { it.id }
        assertEquals("duplicate ids", ids.size, ids.distinct().size)
        val urls = directory.entries.map { it.baseUrl.trim().trimEnd('/') }
        assertEquals("duplicate baseUrls", urls.size, urls.distinct().size)
    }

    @Test
    fun `all urls are https`() {
        directory.entries.forEach { entry ->
            assertTrue("${entry.id} baseUrl ${entry.baseUrl}", entry.baseUrl.startsWith("https://"))
            assertTrue("${entry.id} docsUrl ${entry.docsUrl}", entry.docsUrl.startsWith("https://"))
        }
    }

    @Test
    fun `no local or ip-literal hosts`() {
        directory.entries.forEach { entry ->
            val host = hostOf(entry.baseUrl)
            assertTrue("${entry.id} has empty host", host.isNotEmpty())
            assertTrue(
                "${entry.id} host $host is localhost/loopback",
                host !in setOf("localhost", "127.0.0.1", "::1", "0.0.0.0"),
            )
            assertTrue("${entry.id} host $host is an IP literal", !isIpLiteral(host))
        }
    }

    @Test
    fun `no curated card host overlap`() {
        val curatedHosts = AIProvider.entries
            .map { it.baseUrl }
            .filter { it.startsWith("https://") }
            .map(::hostOf)
            .toSet()
        directory.entries.forEach { entry ->
            assertTrue(
                "${entry.id} host ${hostOf(entry.baseUrl)} duplicates a curated card",
                hostOf(entry.baseUrl) !in curatedHosts,
            )
        }
    }

    @Test
    fun `vision model lists are capped and clean`() {
        directory.entries.forEach { entry ->
            val models = entry.visionModels
            assertTrue("${entry.id} has ${models.size} visionModels", models.size in 1..25)
            assertTrue("${entry.id} has blank visionModels", models.all { it.isNotBlank() })
            assertEquals(
                "${entry.id} has case-duplicate visionModels",
                models.size,
                models.distinctBy { it.lowercase() }.size,
            )
        }
    }

    @Test
    fun `findEntry matches entries with trailing slash and whitespace`() {
        val entry = directory.entries.first()
        assertEquals(entry, directory.findEntry(entry.baseUrl))
        assertEquals(entry, directory.findEntry(" ${entry.baseUrl}/ "))
        assertEquals(entry, directory.findEntry("  ${entry.baseUrl.trim()}  "))
    }

    @Test
    fun `findEntry misses unknown blank and null urls`() {
        assertNull(directory.findEntry("https://not-in-the-directory.example/v1"))
        assertNull(directory.findEntry(""))
        assertNull(directory.findEntry("   "))
        assertNull(directory.findEntry(null))
        // No scheme folding: the stored URL must match exactly (modulo trim/slash).
        val entry = directory.entries.first()
        val httpVariant = entry.baseUrl.replaceFirst("https://", "http://")
        if (httpVariant != entry.baseUrl) assertNull(directory.findEntry(httpVariant))
    }
}
