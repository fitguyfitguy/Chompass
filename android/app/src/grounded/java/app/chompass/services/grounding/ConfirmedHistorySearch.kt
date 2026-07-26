package app.chompass.services.grounding

import app.chompass.models.FoodEntry
import app.chompass.models.GroundingCandidate
import app.chompass.models.NutrientSourceKind
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.exp
import kotlin.math.ln

/**
 * Search confirmed diary / favorite entries as grounding candidates.
 * History may rerank plausible matches but must not invent nutrients for a
 * brand-new food, and portion is never auto-copied — only offered as evidence.
 */
object ConfirmedHistorySearch {

    data class HistoryHit(
        val entry: FoodEntry,
        val score: Double,
        val frequency: Int,
        val daysSince: Long,
        val matchedBy: String,
    )

    /**
     * @param entries confirmed saved entries (diary + favorites already merged by caller)
     * @param query free-text food name / component
     * @param now reference clock
     * @param minHistory how many prior logs of a food before frequency boost applies
     * @param maxBoost hard cap on the history prior (see [NutrientScaling.cappedHistoryBoost])
     */
    fun search(
        entries: List<FoodEntry>,
        query: String,
        now: Instant = Instant.now(),
        limit: Int = 5,
        minHistory: Int = 2,
        maxBoost: Double = 1.5,
    ): List<HistoryHit> {
        if (query.trim().isEmpty() || entries.isEmpty()) return emptyList()
        val qTokens = QueryNormalizer.normalizeTokens(query)
        if (qTokens.isEmpty()) return emptyList()
        val q = QueryNormalizer.normalizeQuery(query)

        // Collapse by favoriteKey → newest template + count.
        val groups = linkedMapOf<String, Pair<FoodEntry, Int>>()
        for (entry in entries.sortedByDescending { it.timestamp }) {
            val key = entry.favoriteKey
            if (key.isEmpty()) continue
            val existing = groups[key]
            if (existing == null) {
                groups[key] = entry to 1
            } else {
                groups[key] = existing.first to (existing.second + 1)
            }
        }

        val hits = mutableListOf<HistoryHit>()
        for ((_, pair) in groups) {
            val (template, count) = pair
            val name = QueryNormalizer.normalizeQuery(template.name)
            val lexical = lexicalScore(q, qTokens, name)
            if (lexical <= 0) continue
            val days = ChronoUnit.DAYS.between(template.timestamp, now).coerceAtLeast(0)
            // Mild recency decay: half-life ~30 days.
            val recency = exp(-ln(2.0) * days / 30.0)
            val freqBoost = if (count >= minHistory) {
                NutrientScaling.cappedHistoryBoost(0.25 * ln((count).toDouble()), maxBoost)
            } else {
                0.0
            }
            val correctionBoost = GroundingCorrectionStore.boostFor(query, template.favoriteKey)
            val score = lexical + recency + freqBoost + correctionBoost
            hits += HistoryHit(
                entry = template,
                score = score,
                frequency = count,
                daysSince = days,
                matchedBy = "history_lexical+prior",
            )
        }
        return hits.sortedByDescending { it.score }.take(limit)
    }

    fun toCandidate(hit: HistoryHit): GroundingCandidate {
        val e = hit.entry
        val grams = e.servingSizeGrams?.takeIf { it > 0 } ?: 100.0
        val scale = 100.0 / grams
        return GroundingCandidate(
            sourceKind = NutrientSourceKind.HISTORY,
            sourceId = e.favoriteKey,
            displayName = e.name,
            score = hit.score,
            caloriesPer100g = e.calories * scale,
            proteinPer100g = e.protein * scale,
            carbsPer100g = e.carbs * scale,
            fatPer100g = e.fat * scale,
            servingSizeGrams = e.servingSizeGrams,
            matchedBy = hit.matchedBy,
            datasetVersion = "local-history",
        )
    }

    private fun lexicalScore(query: String, tokens: List<String>, name: String): Double {
        if (name == query) return 8.0
        if (name.startsWith(query)) return 5.0
        if (name.contains(query)) return 3.0
        val nameTokens = QueryNormalizer.normalizeTokens(name).toSet()
        val overlap = tokens.count { it in nameTokens }
        if (overlap == 0) return 0.0
        return overlap * 1.2
    }
}
