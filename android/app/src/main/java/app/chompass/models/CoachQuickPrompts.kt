package app.chompass.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * One row of the coach quick-prompt strip (Codeberg #155): a builtin chip
 * ([label] null, [id] is the stable builtin id) or a custom entry ([label]
 * and [prompt] set; [id] is a free-form list key).
 */
@Serializable
data class CoachQuickPromptEntry(
    val id: String,
    val label: String? = null,
    val prompt: String? = null,
) {
    val isBuiltin: Boolean get() = label == null
}

/**
 * Stored coach quick-prompt selection: order, removals, custom entries.
 * Null/blank/invalid storage falls back to the per-goal defaults; a
 * present-but-empty entries list is an explicit hidden strip (same semantics
 * as the tracker quick-kind selection).
 */
data class CoachQuickPrompts(val entries: List<CoachQuickPromptEntry>) {
    /** Stored entries with unknown builtin ids drop (garbage rule); customs keep. */
    fun resolveFor(builtinDefaults: List<CoachQuickPromptEntry>): List<CoachQuickPromptEntry> {
        val known = builtinDefaults.mapTo(HashSet()) { it.id }
        return entries.filter { it.isBuiltin && it.id in known || !it.isBuiltin }
    }

    fun toStorage(): String = json.encodeToString(entries)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Null/blank/invalid JSON → null = use the goal defaults. */
        fun fromStorage(raw: String?): CoachQuickPrompts? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                CoachQuickPrompts(json.decodeFromString<List<CoachQuickPromptEntry>>(raw))
            }.getOrNull()
        }
    }
}
