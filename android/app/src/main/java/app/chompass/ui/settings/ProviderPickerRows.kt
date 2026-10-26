package app.chompass.ui.settings

import app.chompass.models.AIProvider
import app.chompass.services.ai.ProviderDirectoryEntry

enum class ProviderPickerSection { SHORTCUTS, PHONE, SERVER, CLOUD }

enum class ProviderPickerChip { ON_PHONE, YOUR_SERVER, NATIVE, CLOUD }

sealed class ProviderPickerRow {
    abstract val section: ProviderPickerSection
    abstract val chip: ProviderPickerChip
    abstract val label: String
    abstract val subtitle: String
    abstract val filterText: String

    data class Builtin(
        val provider: AIProvider,
        override val section: ProviderPickerSection,
        override val chip: ProviderPickerChip,
        override val label: String,
        override val subtitle: String,
        override val filterText: String,
    ) : ProviderPickerRow()

    data class Directory(
        val entry: ProviderDirectoryEntry,
        override val label: String,
        override val subtitle: String,
        override val filterText: String,
    ) : ProviderPickerRow() {
        override val section = ProviderPickerSection.CLOUD
        override val chip = ProviderPickerChip.CLOUD
    }

    data class OtherUrl(
        override val label: String,
        override val subtitle: String,
    ) : ProviderPickerRow() {
        override val section = ProviderPickerSection.SERVER
        override val chip = ProviderPickerChip.YOUR_SERVER
        override val filterText = "$label $subtitle custom url"
    }
}

/**
 * Settings provider sheet rows. [AIProvider.CUSTOM_OPENAI] is never a row:
 * a directory hit is a [ProviderPickerRow.Directory], and a miss is [ProviderPickerRow.OtherUrl].
 * Shortcuts are pinned only for an empty query so a search hit is not listed twice.
 */
fun buildProviderPickerRows(
    query: String,
    onDeviceAvailable: Boolean,
    directory: List<ProviderDirectoryEntry>,
    builtinLabel: (AIProvider) -> String,
    builtinSubtitle: (AIProvider) -> String,
    otherUrlLabel: String,
    otherUrlSubtitle: String,
    hostKeySubtitle: (String) -> String,
): List<ProviderPickerRow> {
    val q = query.trim()
    val rows = mutableListOf<ProviderPickerRow>()
    if (q.isEmpty()) {
        rows += shortcut(AIProvider.GEMINI, ProviderPickerChip.NATIVE, builtinLabel, builtinSubtitle)
        rows += shortcut(AIProvider.OPENAI, ProviderPickerChip.CLOUD, builtinLabel, builtinSubtitle)
        rows += shortcut(AIProvider.ANTHROPIC, ProviderPickerChip.NATIVE, builtinLabel, builtinSubtitle)
        rows += shortcut(AIProvider.OPENROUTER, ProviderPickerChip.CLOUD, builtinLabel, builtinSubtitle)
        rows += shortcut(AIProvider.OLLAMA, ProviderPickerChip.YOUR_SERVER, builtinLabel, builtinSubtitle)
        if (onDeviceAvailable) {
            rows += shortcut(AIProvider.ON_DEVICE, ProviderPickerChip.ON_PHONE, builtinLabel, builtinSubtitle)
        }
    }
    if (onDeviceAvailable) {
        rows += builtinRow(
            AIProvider.ON_DEVICE,
            ProviderPickerSection.PHONE,
            ProviderPickerChip.ON_PHONE,
            builtinLabel,
            builtinSubtitle,
        )
    }
    rows += builtinRow(
        AIProvider.OLLAMA,
        ProviderPickerSection.SERVER,
        ProviderPickerChip.YOUR_SERVER,
        builtinLabel,
        builtinSubtitle,
    )
    rows += ProviderPickerRow.OtherUrl(otherUrlLabel, otherUrlSubtitle)
    rows += cloudRows(directory, builtinLabel, builtinSubtitle, hostKeySubtitle)
    if (q.isEmpty()) return rows
    val matched = rows.filter { row ->
        val textHit = row.filterText.contains(q, ignoreCase = true)
        if (row is ProviderPickerRow.OtherUrl) {
            textHit || q.contains("://") || (q.contains('.') && q.none { it.isWhitespace() })
        } else {
            textHit
        }
    }
    if (matched.isNotEmpty()) return matched
    return listOf(ProviderPickerRow.OtherUrl(otherUrlLabel, otherUrlSubtitle))
}

private fun shortcut(
    provider: AIProvider,
    chip: ProviderPickerChip,
    builtinLabel: (AIProvider) -> String,
    builtinSubtitle: (AIProvider) -> String,
): ProviderPickerRow = builtinRow(
    provider,
    ProviderPickerSection.SHORTCUTS,
    chip,
    builtinLabel,
    builtinSubtitle,
)

private fun builtinRow(
    provider: AIProvider,
    section: ProviderPickerSection,
    chip: ProviderPickerChip,
    builtinLabel: (AIProvider) -> String,
    builtinSubtitle: (AIProvider) -> String,
): ProviderPickerRow.Builtin {
    val label = builtinLabel(provider)
    val subtitle = builtinSubtitle(provider)
    val host = runCatching { java.net.URI(provider.baseUrl).host }.getOrNull().orEmpty()
    return ProviderPickerRow.Builtin(
        provider = provider,
        section = section,
        chip = chip,
        label = label,
        subtitle = subtitle,
        filterText = "$label $subtitle $host",
    )
}

private fun cloudRows(
    directory: List<ProviderDirectoryEntry>,
    builtinLabel: (AIProvider) -> String,
    builtinSubtitle: (AIProvider) -> String,
    hostKeySubtitle: (String) -> String,
): List<ProviderPickerRow> {
    val builtins = AIProvider.entries.mapNotNull { provider ->
        when (provider) {
            AIProvider.CUSTOM_OPENAI, AIProvider.OLLAMA, AIProvider.ON_DEVICE -> null
            else -> {
                val chip = if (provider == AIProvider.GEMINI || provider == AIProvider.ANTHROPIC) {
                    ProviderPickerChip.NATIVE
                } else {
                    ProviderPickerChip.CLOUD
                }
                builtinRow(provider, ProviderPickerSection.CLOUD, chip, builtinLabel, builtinSubtitle)
            }
        }
    }
    val hosts = directory.map { entry ->
        val host = runCatching { java.net.URI(entry.baseUrl).host }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: entry.baseUrl
        ProviderPickerRow.Directory(
            entry = entry,
            label = entry.name,
            subtitle = hostKeySubtitle(host),
            filterText = "${entry.name} ${entry.baseUrl}",
        )
    }
    return (builtins + hosts).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
}
