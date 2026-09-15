package app.chompass.services.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #103: model discovery on custom OpenAI-compatible endpoints. URL shape
 * (custom base URLs already carry /v1) and the tolerant parser (blank ids
 * skipped, malformed body -> empty, never a crash) are the contract.
 */
class OpenAiModelsClientTest {
    @Test
    fun parseModels_happyPath() {
        val body = """{"object":"list","data":[{"id":"gpt-4o-mini","object":"model"},{"id":"qwen2.5:7b"}]}"""

        assertEquals(listOf("gpt-4o-mini", "qwen2.5:7b"), OpenAiModelsClient.parseModels(body))
    }

    @Test
    fun parseModels_skipsBlankIds() {
        val body = """{"data":[{"id":"  "},{"id":"kept"},{"id":""}]}"""

        assertEquals(listOf("kept"), OpenAiModelsClient.parseModels(body))
    }

    @Test
    fun parseModels_malformedBody_returnsEmpty() {
        for (body in listOf("not json", """{"data":"wrong"}""", """{"other":1}""")) {
            assertEquals(body, emptyList<String>(), OpenAiModelsClient.parseModels(body))
        }
    }

    @Test
    fun modelsUrl_appendsModels_underNormalizedBase() {
        // Custom base URLs already include /v1 — no stripping.
        assertEquals("http://192.168.1.10:1234/v1/models", OpenAiModelsClient.modelsUrl("http://192.168.1.10:1234/v1"))
    }

    @Test
    fun modelsUrl_missingScheme_defaultsToHttps() {
        assertEquals("https://192.168.1.10:1234/v1/models", OpenAiModelsClient.modelsUrl("192.168.1.10:1234/v1"))
    }

    @Test
    fun modelsUrl_missingScheme_getsHttp() {
        assertTrue(OpenAiModelsClient.modelsUrl("192.168.1.10:1234/v1").startsWith("http"))
    }
}
