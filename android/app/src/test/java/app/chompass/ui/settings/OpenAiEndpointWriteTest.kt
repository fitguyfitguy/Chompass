package app.chompass.ui.settings

import app.chompass.models.AIProvider
import app.chompass.models.AiProfile
import app.chompass.services.ai.ProviderDirectory
import app.chompass.services.ai.ProviderDirectoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

class OpenAiEndpointWriteTest {
    private val directory = ProviderDirectory(
        version = 1,
        source = "test",
        fetchedAt = "2026-01-01",
        entries = listOf(
            ProviderDirectoryEntry(
                id = "302",
                name = "302.AI",
                baseUrl = "https://api.302.ai/v1",
                docsUrl = "https://302.ai",
                visionModels = listOf("glm-5.3-flash"),
            ),
        ),
    )

    @Test
    fun geminiToDirectoryHost_switchesAndClearsKey() {
        val active = profile(AIProvider.GEMINI, model = "gemini-3.8-flash")
        val write = planOpenAiEndpointWrite(active, "https://api.302.ai/v1/", directory)
        assertTrue(write.changed)
        assertEquals(AIProvider.CUSTOM_OPENAI, write.provider)
        assertEquals("https://api.302.ai/v1", write.baseUrl)
        assertEquals("glm-5.3-flash", write.model)
        assertTrue(write.clearKey)
        assertTrue(write.clearVision)
    }

    @Test
    fun sameStoredUrl_isNoOp() {
        val active = profile(
            AIProvider.CUSTOM_OPENAI,
            baseUrl = "https://api.302.ai/v1",
            model = "glm-5.3-flash",
        )
        val write = planOpenAiEndpointWrite(active, "https://api.302.ai/v1/", directory)
        assertFalse(write.changed)
        assertEquals(active.provider, write.provider)
        assertEquals(active.baseUrl, write.baseUrl)
        assertEquals(active.model, write.model)
        assertFalse(write.clearKey)
        assertFalse(write.clearVision)
    }

    @Test
    fun sameHostDifferentPath_keepsKeyAndModel() {
        val active = profile(
            AIProvider.CUSTOM_OPENAI,
            baseUrl = "https://api.302.ai/v1",
            model = "kept-model",
        )
        val write = planOpenAiEndpointWrite(active, "https://api.302.ai/v1/openai", directory)
        assertTrue(write.changed)
        assertEquals("https://api.302.ai/v1/openai", write.baseUrl)
        assertEquals("kept-model", write.model)
        assertFalse(write.clearKey)
        assertFalse(write.clearVision)
    }

    @Test
    fun blankUrl_isNoOp() {
        val active = profile(AIProvider.GEMINI, model = "gemini-3.8-flash")
        val write = planOpenAiEndpointWrite(active, "   ", directory)
        assertFalse(write.changed)
        assertEquals(AIProvider.GEMINI, write.provider)
        assertEquals(active.baseUrl, write.baseUrl)
        assertEquals(active.model, write.model)
        assertFalse(write.clearKey)
    }

    private fun profile(
        provider: AIProvider,
        baseUrl: String? = null,
        model: String? = null,
    ) = AiProfile(
        id = UUID.randomUUID(),
        name = "Test",
        provider = provider,
        model = model,
        baseUrl = baseUrl,
        createdAt = Instant.EPOCH,
    )
}
