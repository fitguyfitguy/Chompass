package app.chompass.ui.components

/**
 * Parse a typed magnitude for number wheels. Returns null for empty / "-" /
 * unparsable input so the caller keeps the previous value (Codeberg #62).
 *
 * [decimalSeparator] is the locale mark (',' or '.'); the other mark is also
 * accepted so a leftover US keyboard still works.
 */
fun parseMagnitude(
    raw: String,
    min: Double,
    max: Double,
    step: Double,
    decimalSeparator: Char,
): Double? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty() || trimmed == "-" || trimmed == decimalSeparator.toString()) return null
    val normalized = buildString(trimmed.length) {
        for (ch in trimmed) {
            when (ch) {
                decimalSeparator, '.', ',' -> append('.')
                ' ' -> Unit
                else -> append(ch)
            }
        }
    }
    val parsed = normalized.toDoubleOrNull() ?: return null
    if (parsed.isNaN() || parsed.isInfinite()) return null
    val safeStep = if (step > 0.0) step else 1.0
    val snapped = min + kotlin.math.round((parsed - min) / safeStep) * safeStep
    return snapped.coerceIn(min, max)
}

fun parseMagnitudeInt(
    raw: String,
    min: Int,
    max: Int,
    step: Int,
    decimalSeparator: Char = '.',
): Int? = parseMagnitude(
    raw = raw,
    min = min.toDouble(),
    max = max.toDouble(),
    step = step.coerceAtLeast(1).toDouble(),
    decimalSeparator = decimalSeparator,
)?.toInt()
