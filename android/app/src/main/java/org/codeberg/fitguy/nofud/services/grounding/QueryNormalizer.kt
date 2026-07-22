package org.codeberg.fitguy.nofud.services.grounding

import java.util.Locale

/**
 * Shared pre-retrieval query normalization for USDA / history search.
 * Keep in sync with `docs/benchmarks/food_accuracy/query_normalize.py`.
 */
object QueryNormalizer {

    private val SYNONYMS = mapOf(
        "yoghurt" to "yogurt",
        "yoghourt" to "yogurt",
        "brezel" to "pretzel",
        "laugenbrezel" to "pretzel",
        "aubergine" to "eggplant",
        "courgette" to "zucchini",
        "mince" to "ground",
        "minced" to "ground",
        "capsicum" to "pepper",
        "coriander" to "cilantro",
        "biscuit" to "cookie",
        "chips" to "fries",
        "prawn" to "shrimp",
        "prawns" to "shrimp",
    )

    private val UNIT_NOISE = setOf(
        "g", "gram", "grams", "kg", "mg", "ml", "l", "liter", "litre",
        "oz", "ounce", "ounces", "lb", "pound", "pounds",
        "cup", "cups", "tbsp", "tablespoon", "tablespoons",
        "tsp", "teaspoon", "teaspoons", "slice", "slices",
        "piece", "pieces", "serving", "servings",
        "large", "medium", "small", "can", "cans", "bottle",
        "glass", "glasses", "scoop", "scoops", "bar", "bars",
        "half", "quarter", "approx", "approximately", "about",
    )

    private val NUM = Regex("^\\d+([./]\\d+)?$")

    fun tokenize(text: String): List<String> =
        text.lowercase(Locale.US)
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .split(Regex("\\s+"))
            .filter { it.length >= 2 }

    fun normalizeTokens(
        text: String,
        stripUnits: Boolean = true,
        applySynonyms: Boolean = true,
    ): List<String> {
        val out = LinkedHashSet<String>()
        for (raw in tokenize(text)) {
            if (stripUnits && (NUM.matches(raw) || raw in UNIT_NOISE)) continue
            val tok = if (applySynonyms) SYNONYMS[raw] ?: raw else raw
            out += tok
        }
        return out.toList()
    }

    fun normalizeQuery(text: String): String =
        normalizeTokens(text).joinToString(" ")

    /** Strip quantity/unit noise from a brand+name+preparation search string. */
    fun searchQuery(vararg parts: String?): String =
        normalizeQuery(parts.filterNotNull().joinToString(" "))
}
