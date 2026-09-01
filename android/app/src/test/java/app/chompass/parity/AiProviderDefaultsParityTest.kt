package app.chompass.parity

import app.chompass.BuildConfig
import app.chompass.models.AIProvider
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
        // Debug builds default Gemini to Flash-Lite (free-tier 503s on 3.7
        // during device testing); the fixture stays release-truth for the
        // PWA, so only the Gemini defaultModel expectation is variant-aware.
        val expectedDefaultModel =
            if (BuildConfig.DEBUG && provider == AIProvider.GEMINI) {
                "gemini-3.5-flash-lite"
            } else {
                expected.getString("defaultModel")
            }
        assertEquals("$label defaultModel", expectedDefaultModel, provider.defaultModel)
        assertEquals(
            "$label defaultFallbackModel",
            expected.getString("defaultFallbackModel"),
            provider.defaultFallbackModel,
        )
        assertEquals("$label models", jsonStringList(expected.getJSONArray("models")), provider.models)
        assertEquals(
            "$label modelTiers",
            jsonStringStringMap(expected.optJSONObject("modelTiers")),
            provider.modelTiers,
        )
    }

    private fun jsonStringStringMap(obj: org.json.JSONObject?): Map<String, String> {
        if (obj == null) return emptyMap()
        val out = LinkedHashMap<String, String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            out[k] = obj.getString(k)
        }
        return out
    }

    private fun jsonStringList(arr: JSONArray): List<String> =
        (0 until arr.length()).map { arr.getString(it) }
}
