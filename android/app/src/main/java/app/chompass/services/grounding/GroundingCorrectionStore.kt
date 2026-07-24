package app.chompass.services.grounding

import java.util.concurrent.ConcurrentHashMap

/**
 * Local-only correction priors: when the user picks a source_id for a query,
 * prefer that alias on later grounded searches. Never leaves the device.
 */
object GroundingCorrectionStore {
    private val aliases = ConcurrentHashMap<String, Alias>()

    data class Alias(
        val sourceKind: String,
        val sourceId: String,
        val displayName: String?,
        val grams: Double? = null,
        val hitCount: Int = 1,
    )

    fun clear() = aliases.clear()

    fun record(
        query: String,
        sourceKind: String,
        sourceId: String,
        displayName: String? = null,
        grams: Double? = null,
    ) {
        val key = QueryNormalizer.normalizeQuery(query)
        if (key.isEmpty() || sourceId.isBlank()) return
        aliases.compute(key) { _, existing ->
            if (existing != null && existing.sourceId == sourceId && existing.sourceKind == sourceKind) {
                existing.copy(hitCount = existing.hitCount + 1, grams = grams ?: existing.grams)
            } else {
                Alias(sourceKind, sourceId, displayName, grams)
            }
        }
    }

    fun lookup(query: String): Alias? {
        val key = QueryNormalizer.normalizeQuery(query)
        if (key.isEmpty()) return null
        aliases[key]?.let { return it }
        // Prefix / containment soft match on stored keys.
        return aliases.entries
            .filter { (k, _) -> k == key || key.contains(k) || k.contains(key) }
            .maxByOrNull { it.value.hitCount }
            ?.value
    }

    fun boostFor(query: String, sourceId: String): Double {
        val alias = lookup(query) ?: return 0.0
        if (alias.sourceId != sourceId) return 0.0
        return 3.0 + (alias.hitCount.coerceAtMost(5) * 0.5)
    }

    fun snapshot(): Map<String, Alias> = aliases.toMap()

    fun size(): Int = aliases.size
}
