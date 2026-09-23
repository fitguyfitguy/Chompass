package app.chompass.services.ai

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #103: model discovery on custom OpenAI-compatible endpoints. URL shape
 * (custom base URLs already carry /v1) and the tolerant parser (blank ids
 * skipped, malformed body -> empty, never a crash) are the contract.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
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

    // #107: runtime OpenAI lineup merged over the curated list.

    private fun model(
        id: String,
        created: Long = 0L,
        ownedBy: String = "openai",
        shutdownDate: String = "",
    ) = OpenAiModelsClient.OpenAiModel(id, created, ownedBy, shutdownDate)

    @Test
    fun parseModelsWithMeta_readsFilterFields() {
        val body = """
            {"data":[
                {"id":"gpt-5.6-sol","created":1758000000,"owned_by":"openai"},
                {"id":"gpt-3.5-turbo","created":1700000000,"owned_by":"openai","shutdown_date":"2026-11-01"}
            ]}
        """.trimIndent()

        val parsed = OpenAiModelsClient.parseModelsWithMeta(body)
        assertEquals(2, parsed.size)
        assertEquals("gpt-5.6-sol", parsed[0].id)
        assertEquals(1758000000L, parsed[0].created)
        assertEquals("openai", parsed[0].ownedBy)
        assertEquals("", parsed[0].shutdownDate)
        assertEquals("2026-11-01", parsed[1].shutdownDate)
    }

    @Test
    fun parseModelsWithMeta_malformedBody_returnsEmpty() {
        for (body in listOf("not json", """{"data":"wrong"}""")) {
            assertEquals(body, emptyList<OpenAiModelsClient.OpenAiModel>(), OpenAiModelsClient.parseModelsWithMeta(body))
        }
    }

    @Test
    fun filterGptLineup_shapeOwnerShutdownAndSort() {
        val models = listOf(
            model("gpt-4o-mini", created = 100),
            model("gpt-3.5-turbo", created = 999), // shape rejected
            model("gpt-5.6-sol", created = 998, ownedBy = "system"), // not the official owner
            model("gpt-4.1", created = 500),
            model("gpt-5-retiring", created = 700, shutdownDate = "2026-12-01"), // announced shutdown
            model("gpt-6-luna", created = 800),
        )

        assertEquals(
            listOf("gpt-6-luna", "gpt-4.1", "gpt-4o-mini"),
            OpenAiModelsClient.filterGptLineup(models),
        )
    }

    @Test
    fun mergeOverCurated_keepsCuratedOrder_appendsRuntimeIds() {
        val curated = app.chompass.models.AIProvider.OPENAI.models
        // sol and 4o-mini are already curated; only astra appends.
        val runtime = listOf("gpt-6-astra", "gpt-5.6-sol", "gpt-4o-mini")

        assertEquals(
            curated + listOf("gpt-6-astra"),
            OpenAiModelsClient.mergeOverCurated(curated, runtime),
        )
    }

    @Test
    fun mergeOverCurated_nullOrEmpty_runtime_returnsCuratedCopy() {
        val curated = app.chompass.models.AIProvider.OPENAI.models
        assertEquals(curated, OpenAiModelsClient.mergeOverCurated(curated, null))
        assertEquals(curated, OpenAiModelsClient.mergeOverCurated(curated, emptyList()))
    }

    @Test
    fun offlineFallback_malformedFetch_yieldsCuratedList() {
        // Fetch failure -> empty parse -> empty filter -> curated picker list.
        val curated = app.chompass.models.AIProvider.OPENAI.models
        val filtered = OpenAiModelsClient.filterGptLineup(OpenAiModelsClient.parseModelsWithMeta("not json"))
        assertEquals(curated, OpenAiModelsClient.mergeOverCurated(curated, filtered))
    }
}
