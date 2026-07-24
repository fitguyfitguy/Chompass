package org.codeberg.fitguy.nofud.parity

import org.codeberg.fitguy.nofud.models.AIProvider
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Dual-side lock for BYOK provider defaults shared with the PWA via
 * `testdata/parity/ai-provider-defaults.json`.
 */
class AiProviderDefaultsParityTest {

    @Test
    fun geminiAnthropicOpenaiMatchParityFixture() {
        val root = ParityFixtures.readJson("ai-provider-defaults.json")
        val providers = root.getJSONObject("providers")

        assertProvider("gemini", AIProvider.GEMINI, providers.getJSONObject("gemini"))
        assertProvider("anthropic", AIProvider.ANTHROPIC, providers.getJSONObject("anthropic"))
        assertProvider("openai", AIProvider.OPENAI, providers.getJSONObject("openai"))
    }

    private fun assertProvider(
        label: String,
        provider: AIProvider,
        expected: org.json.JSONObject,
    ) {
        assertEquals("$label defaultModel", expected.getString("defaultModel"), provider.defaultModel)
        assertEquals(
            "$label defaultFallbackModel",
            expected.getString("defaultFallbackModel"),
            provider.defaultFallbackModel,
        )
        assertEquals("$label models", jsonStringList(expected.getJSONArray("models")), provider.models)
    }

    private fun jsonStringList(arr: JSONArray): List<String> =
        (0 until arr.length()).map { arr.getString(it) }
}
