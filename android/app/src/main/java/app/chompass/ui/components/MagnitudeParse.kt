package app.chompass.ui.components

import kotlin.math.roundToInt

/**
 * Parse a typed magnitude for number wheels. Returns null for empty / "-" /
 * unparsable input so the caller keeps the previous value (Codeberg #62).
 *
 * [decimalSeparator] is the locale mark (',' or '.'); the other mark is also
 * accepted so a leftover US keyboard still works.
 */
@Suppress("UNUSED_PARAMETER")
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
    // Keep the typed number. Wheel step is only for spinning, not for keypad commit.
    return parsed.coerceIn(min, max)
}

/** Integer column + tenths digit for [SplitDecimalWheelPicker]. Uses round-to-nearest
 *  on the 0.1 grid so IEEE leftovers do not paint 80.3 as 80.2 (Codeberg #63). */
fun splitDecimalParts(value: Double, min: Int, max: Int): Pair<Int, Int> {
    val lo = min.toDouble()
    val hi = max.toDouble()
    val clamped = value.coerceIn(lo, hi)
    val totalTenths = (clamped * 10.0).roundToInt().coerceIn(min * 10, max * 10)
    return (totalTenths / 10) to (totalTenths % 10)
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
