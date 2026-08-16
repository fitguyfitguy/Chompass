package app.chompass.services.ai

/**
 * Incrementally assembles a [PartialFoodAnalysis] from streamed model text.
 *
 * Only complete, type-valid top-level JSON values are accepted. Incomplete
 * strings/numbers remain buffered privately until a trailing delimiter closes them.
 * The final [FoodJsonParser.parseFood] remains the source of truth.
 */
class FoodPartialJsonAssembler {
    private val buffer = StringBuilder()
    private var lastEmitted: PartialFoodAnalysis? = null

    fun reset() {
        buffer.clear()
        lastEmitted = null
    }

    /** Append raw stream text (or a full response) and return a new partial when fields advance. */
    fun push(chunk: String): PartialFoodAnalysis? {
        if (chunk.isEmpty()) return lastEmitted
        buffer.append(chunk)
        val next = extract(buffer.toString()) ?: return lastEmitted
        if (next == lastEmitted) return lastEmitted
        lastEmitted = next
        return next
    }

    fun current(): PartialFoodAnalysis? = lastEmitted

    /** Full raw text buffered so far — used to recover a parseable response when a stream stalls. */
    fun snapshotText(): String = buffer.toString()

    internal fun extract(text: String): PartialFoodAnalysis? {
        val jsonSpan = FoodJsonParser.extractJson(text)
        if (jsonSpan.isBlank() || !jsonSpan.contains('{')) return null

        val name = completeString(jsonSpan, "name")
        val emoji = completeString(jsonSpan, "emoji")?.takeIf { it != "null" }
        val calories = completeNumber(jsonSpan, "calories")?.toInt()
        val protein = completeNumber(jsonSpan, "protein")
        val carbs = completeNumber(jsonSpan, "carbs")
        val fat = completeNumber(jsonSpan, "fat")
        val serving = completeNumber(jsonSpan, "serving_size_grams")
        val fiber = completeNumber(jsonSpan, "fiber")

        val microKeys = listOf(
            "sugar", "added_sugar", "fiber", "saturated_fat", "monounsaturated_fat",
            "polyunsaturated_fat", "cholesterol", "sodium", "potassium", "trans_fat",
            "calcium", "iron", "magnesium", "zinc", "vitamin_a", "vitamin_c",
            "vitamin_d", "vitamin_b12", "vitamin_e", "vitamin_k", "folate", "omega_3",
        )
        val micronutrientCount = microKeys.count { key ->
            completeNumber(jsonSpan, key) != null
        }

        val hasUnitOptions = completeArrayNonEmpty(jsonSpan, "unit_options") ||
            completeArrayNonEmpty(jsonSpan, "serving_unit_options")

        val partial = PartialFoodAnalysis(
            name = name,
            emoji = emoji,
            calories = calories?.coerceAtLeast(0),
            protein = protein?.takeIf { it >= 0.0 },
            carbs = carbs?.takeIf { it >= 0.0 },
            fat = fat?.takeIf { it >= 0.0 },
            servingSizeGrams = serving?.takeIf { it > 0.0 },
            fiber = fiber?.takeIf { it >= 0.0 },
            micronutrientCount = micronutrientCount,
            hasUnitOptions = hasUnitOptions,
            streaming = true,
        )
        return partial.takeIf { it.hasAnyField }
    }

    private fun completeString(json: String, key: String): String? {
        val start = keyValueStart(json, key) ?: return null
        if (start >= json.length) return null
        if (json[start] != '"') return null
        var i = start + 1
        var escape = false
        while (i < json.length) {
            val ch = json[i]
            if (escape) {
                escape = false
                i++
                continue
            }
            when (ch) {
                '\\' -> escape = true
                '"' -> {
                    val raw = json.substring(start + 1, i)
                    return unescapeJsonString(raw)
                }
            }
            i++
        }
        return null // string still open
    }

    private fun completeNumber(json: String, key: String): Double? {
        val start = keyValueStart(json, key) ?: return null
        if (start >= json.length) return null
        val ch0 = json[start]
        if (ch0 != '-' && ch0 != '+' && !ch0.isDigit()) {
            // null / true / false / string — not a number we care about
            return null
        }
        var i = start
        if (json[i] == '-' || json[i] == '+') i++
        var sawDigit = false
        while (i < json.length && json[i].isDigit()) {
            sawDigit = true
            i++
        }
        if (i < json.length && json[i] == '.') {
            i++
            while (i < json.length && json[i].isDigit()) {
                sawDigit = true
                i++
            }
        }
        if (i < json.length && (json[i] == 'e' || json[i] == 'E')) {
            i++
            if (i < json.length && (json[i] == '+' || json[i] == '-')) i++
            while (i < json.length && json[i].isDigit()) i++
        }
        if (!sawDigit) return null
        // Number is complete only when followed by a JSON delimiter.
        // End-of-buffer alone is not enough — more digits may still arrive.
        if (i >= json.length) return null
        val next = json[i]
        if (next !in ",}] \t\r\n") return null
        return json.substring(start, i).toDoubleOrNull()
    }

    private fun completeArrayNonEmpty(json: String, key: String): Boolean {
        val start = keyValueStart(json, key) ?: return false
        if (start >= json.length || json[start] != '[') return false
        var depth = 0
        var inString = false
        var escape = false
        var i = start
        while (i < json.length) {
            val ch = json[i]
            if (escape) {
                escape = false
                i++
                continue
            }
            if (ch == '\\' && inString) {
                escape = true
                i++
                continue
            }
            if (ch == '"') {
                inString = !inString
                i++
                continue
            }
            if (inString) {
                i++
                continue
            }
            when (ch) {
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) {
                        val body = json.substring(start + 1, i).trim()
                        return body.isNotEmpty() && body != "null"
                    }
                }
            }
            i++
        }
        return false
    }

    private fun keyValueStart(json: String, key: String): Int? {
        val needle = "\"$key\""
        var searchFrom = 0
        while (true) {
            val keyIdx = json.indexOf(needle, searchFrom)
            if (keyIdx < 0) return null
            // Ensure this is at object depth 1 (not nested), roughly: count braces before it.
            if (!isTopLevelKey(json, keyIdx)) {
                searchFrom = keyIdx + needle.length
                continue
            }
            var i = keyIdx + needle.length
            while (i < json.length && json[i].isWhitespace()) i++
            if (i >= json.length || json[i] != ':') {
                searchFrom = keyIdx + needle.length
                continue
            }
            i++
            while (i < json.length && json[i].isWhitespace()) i++
            return i
        }
    }

    private fun isTopLevelKey(json: String, keyIdx: Int): Boolean {
        var depth = 0
        var inString = false
        var escape = false
        for (i in 0 until keyIdx) {
            val ch = json[i]
            if (escape) {
                escape = false
                continue
            }
            if (ch == '\\' && inString) {
                escape = true
                continue
            }
            if (ch == '"') {
                inString = !inString
                continue
            }
            if (inString) continue
            when (ch) {
                '{', '[' -> depth++
                '}', ']' -> depth--
            }
        }
        return depth == 1
    }

    private fun unescapeJsonString(raw: String): String {
        val out = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            if (ch != '\\' || i + 1 >= raw.length) {
                out.append(ch)
                i++
                continue
            }
            when (val next = raw[i + 1]) {
                '"', '\\', '/' -> {
                    out.append(next); i += 2
                }
                'b' -> {
                    out.append('\b'); i += 2
                }
                'f' -> {
                    out.append('\u000c'); i += 2
                }
                'n' -> {
                    out.append('\n'); i += 2
                }
                'r' -> {
                    out.append('\r'); i += 2
                }
                't' -> {
                    out.append('\t'); i += 2
                }
                'u' -> if (i + 5 < raw.length) {
                    val hex = raw.substring(i + 2, i + 6)
                    out.append(hex.toIntOrNull(16)?.toChar() ?: '?')
                    i += 6
                } else {
                    out.append(ch); i++
                }
                else -> {
                    out.append(next); i += 2
                }
            }
        }
        return out.toString()
    }
}
