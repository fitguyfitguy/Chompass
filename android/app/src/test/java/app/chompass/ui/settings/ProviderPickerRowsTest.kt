package app.chompass.ui.settings

import app.chompass.models.AIProvider
import app.chompass.services.ai.ProviderDirectoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderPickerRowsTest {
    private val directory = listOf(
        ProviderDirectoryEntry(
            id = "302ai",
            name = "302.AI",
            baseUrl = "https://api.302.ai/v1",
            docsUrl = "https://302.ai",
        ),
        ProviderDirectoryEntry(
            id = "abacus",
            name = "Abacus",
            baseUrl = "https://routellm.abacus.ai/v1",
            docsUrl = "https://abacus.ai",
        ),
    )

    private val labels = mapOf(
        AIProvider.GEMINI to "Google Gemini",
        AIProvider.OPENAI to "OpenAI",
        AIProvider.ANTHROPIC to "Anthropic Claude",
        AIProvider.XAI to "xAI Grok",
        AIProvider.OPENROUTER to "OpenRouter",
        AIProvider.TOGETHER_AI to "Together AI",
        AIProvider.GROQ to "Groq",
        AIProvider.HUGGING_FACE to "Hugging Face",
        AIProvider.FIREWORKS to "Fireworks AI",
        AIProvider.DEEP_INFRA to "DeepInfra",
        AIProvider.MISTRAL to "Mistral",
        AIProvider.OLLAMA to "Ollama (Local)",
        AIProvider.OLLAMA_CLOUD to "Ollama Cloud",
        AIProvider.CUSTOM_OPENAI to "Other URL",
        AIProvider.ON_DEVICE to "On-Device (Private)",
    )

    @Test
    fun emptyQuery_pinsShortcutsThenPhoneServerAndSortedCloud() {
        val rows = rows(query = "", onDeviceAvailable = true)
        assertEquals(
            listOf(
                AIProvider.GEMINI,
                AIProvider.OPENAI,
                AIProvider.ANTHROPIC,
                AIProvider.OPENROUTER,
                AIProvider.OLLAMA,
                AIProvider.ON_DEVICE,
            ),
            rows.take(6).map { (it as ProviderPickerRow.Builtin).provider },
        )
        assertEquals(
            listOf(
                "Google Gemini",
                "OpenAI",
                "Anthropic Claude",
                "OpenRouter",
                "Ollama (Local)",
                "On-Device (Private)",
            ),
            rows.take(6).map { it.label },
        )
        assertEquals(
            listOf(
                ProviderPickerChip.NATIVE,
                ProviderPickerChip.CLOUD,
                ProviderPickerChip.NATIVE,
                ProviderPickerChip.CLOUD,
                ProviderPickerChip.YOUR_SERVER,
                ProviderPickerChip.ON_PHONE,
            ),
            rows.take(6).map { it.chip },
        )
        assertTrue(rows.take(6).all { it.section == ProviderPickerSection.SHORTCUTS })

        assertEquals(ProviderPickerSection.PHONE, rows[6].section)
        assertEquals(AIProvider.ON_DEVICE, (rows[6] as ProviderPickerRow.Builtin).provider)
        assertEquals(ProviderPickerSection.SERVER, rows[7].section)
        assertEquals(AIProvider.OLLAMA, (rows[7] as ProviderPickerRow.Builtin).provider)
        assertEquals(ProviderPickerSection.SERVER, rows[8].section)
        assertTrue(rows[8] is ProviderPickerRow.OtherUrl)

        val cloud = rows.filter { it.section == ProviderPickerSection.CLOUD }
        assertEquals("302.AI", cloud[0].label)
        assertEquals("Abacus", cloud[1].label)
        assertEquals(ProviderPickerChip.CLOUD, cloud[0].chip)
        assertEquals(ProviderPickerChip.CLOUD, cloud[1].chip)
        assertTrue(rows.none { it is ProviderPickerRow.Builtin && it.provider == AIProvider.CUSTOM_OPENAI })
    }

    @Test
    fun queryGroq_returnsOnlyTheCloudRow() {
        val rows = rows(query = "groq")
        assertEquals(1, rows.size)
        val row = rows.single() as ProviderPickerRow.Builtin
        assertEquals(AIProvider.GROQ, row.provider)
        assertEquals("Groq", row.label)
        assertEquals(ProviderPickerChip.CLOUD, row.chip)
        assertEquals(ProviderPickerSection.CLOUD, row.section)
        assertTrue(rows.none { it is ProviderPickerRow.OtherUrl })
    }

    @Test
    fun queryLmStudio_returnsOnlyOtherUrl() {
        val rows = rows(query = "lm studio")
        assertEquals(1, rows.size)
        assertTrue(rows.single() is ProviderPickerRow.OtherUrl)
    }

    @Test
    fun queryUrl_includesOtherUrl() {
        val rows = rows(query = "https://example.test/v1")
        assertTrue(rows.any { it is ProviderPickerRow.OtherUrl })
    }

    @Test
    fun onDeviceUnavailable_omitsBothOnDeviceRowsAndThePhoneSection() {
        val rows = rows(query = "", onDeviceAvailable = false)
        assertTrue(rows.none { it is ProviderPickerRow.Builtin && it.provider == AIProvider.ON_DEVICE })
        assertTrue(rows.none { it.section == ProviderPickerSection.PHONE })
    }

    private fun rows(query: String, onDeviceAvailable: Boolean = true) = buildProviderPickerRows(
        query = query,
        onDeviceAvailable = onDeviceAvailable,
        directory = directory,
        builtinLabel = { labels.getValue(it) },
        builtinSubtitle = { subtitle(it) },
        otherUrlLabel = "Other URL",
        otherUrlSubtitle = "Type a base URL",
        hostKeySubtitle = { "$it · key" },
    )

    private fun subtitle(provider: AIProvider): String = when (provider) {
        AIProvider.GEMINI -> "Key · Google Search toggle"
        AIProvider.ANTHROPIC -> "Key · separate API"
        AIProvider.OLLAMA -> "This network · no key"
        AIProvider.ON_DEVICE -> "Private and offline. Less accurate than cloud AI; ~2 to 4 GB download"
        else -> {
            val host = runCatching { java.net.URI(provider.baseUrl).host }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: provider.baseUrl
            "$host · key"
        }
    }
}
