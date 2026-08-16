package app.chompass.services

/**
 * Shared validation for externally-supplied values that land in the diary or in
 * AI prompts (deep-link meal imports, OFF database names, AI model output).
 * Policy: clamp finite numbers to bounds, never reject a plausibly-valid row;
 * strip control + bidi-override characters from free text.
 */
object InputSanitizer {
    const val MAX_NAME_LENGTH = 120
    const val MAX_NOTE_LENGTH = 500
    const val MAX_UNIT_LENGTH = 40
    const val MAX_EMOJI_LENGTH = 16
    const val MAX_CALORIES = 10_000.0
    const val MAX_MACRO_GRAMS = 2_000.0
    const val MAX_SERVING_GRAMS = 5_000.0
    const val MAX_QUANTITY = 100_000.0
    const val MAX_GRAMS_PER_UNIT = 100_000.0

    /** null for NaN/Infinity; clamped into [min, max]; [fallback] when null. */
    fun clamp(value: Double?, min: Double, max: Double, fallback: Double): Double {
        val v = value ?: return fallback
        if (!v.isFinite()) return fallback
        return v.coerceIn(min, max)
    }

    fun clamp(value: Int?, min: Int, max: Int, fallback: Int): Int {
        val v = value ?: return fallback
        return v.coerceIn(min, max)
    }

    /** null-preserving clamp: NaN/Infinity → null; otherwise clamped (>= 0). */
    fun nutrient(value: Double?): Double? {
        if (value == null) return null
        if (!value.isFinite()) return null
        return value.coerceIn(0.0, MAX_MACRO_GRAMS)
    }

    /**
     * null-preserving clamp for micronutrients, which arrive in mg / mcg units
     * (sodium, potassium, vitamins…) and routinely exceed gram-scale caps.
     * Absurd magnitudes are still bounded (1e5), negatives/NaN dropped.
     */
    fun micro(value: Double?): Double? {
        if (value == null) return null
        if (!value.isFinite()) return null
        return value.coerceIn(0.0, MAX_MICRO_UNITS)
    }

    const val MAX_MICRO_UNITS = 100_000.0

    fun calories(value: Int?): Int = clamp(value, 0, MAX_CALORIES.toInt(), 0)

    fun servingGrams(value: Double?): Double? {
        if (value == null) return null
        if (!value.isFinite()) return null
        return value.coerceIn(0.0, MAX_SERVING_GRAMS).takeIf { it > 0 }
    }

    fun quantity(value: Double?): Double? = nutrientOrNull(value, MAX_QUANTITY)

    fun gramsPerUnit(value: Double?): Double? = nutrientOrNull(value, MAX_GRAMS_PER_UNIT)

    private fun nutrientOrNull(value: Double?, max: Double): Double? {
        if (value == null) return null
        if (!value.isFinite()) return null
        return value.coerceIn(0.0, max)
    }

    /**
     * Trims [raw], strips C0/C1 controls and bidi override/embedding/isolate
     * chars (U+202A–U+202E, U+2066–U+2069 — the spoofing-relevant direction
     * controls; ZWJ etc. are intentionally kept for emoji sequences), then caps
     * length at [maxLength]. Returns null when nothing meaningful remains.
     */
    fun text(raw: String?, maxLength: Int): String? {
        val cleaned = raw
            ?.filterNot { it.isISOControl() || it.code in BIDI_OVERRIDES }
            ?.trim()
        if (cleaned.isNullOrEmpty()) return null
        return if (cleaned.length > maxLength) cleaned.take(maxLength).trim() else cleaned
    }

    fun emoji(raw: String?): String? = text(raw, MAX_EMOJI_LENGTH)

    /**
     * Neutralizes the prompt-data delimiters themselves so hostile content cannot
     * close a `<user_data>` / `<external_data>` block early and splice text into
     * the instructions region. Only the exact tag tokens are stripped; everything
     * else between the tags is treated as data by the model instruction anyway.
     */
    fun delimiterSafe(raw: String?): String? = raw
        ?.replace(USER_DATA_OPEN, "")
        ?.replace(USER_DATA_CLOSE, "")
        ?.replace(EXTERNAL_DATA_OPEN, "")
        ?.replace(EXTERNAL_DATA_CLOSE, "")

    const val USER_DATA_OPEN = "<user_data>"
    const val USER_DATA_CLOSE = "</user_data>"
    const val EXTERNAL_DATA_OPEN = "<external_data>"
    const val EXTERNAL_DATA_CLOSE = "</external_data>"

    private val BIDI_OVERRIDES = (0x202A..0x202E).toSet() + (0x2066..0x2069).toSet()
}
