package app.chompass.models

import kotlinx.serialization.Serializable
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Result of a per-entry serving edit (the serving-card pencil): the renamed /
 * re-weighted [updated] option and the [options] list the host should store.
 */
data class ServingEditResult(
    val updated: ServingUnitOption,
    val options: List<ServingUnitOption>,
)

@Serializable
data class ServingUnitOption(
    val unit: String,
    val gramsPerUnit: Double,
    val quantity: Double? = null
) : java.io.Serializable {
    val id: String get() = normalizedUnit

    val normalizedUnit: String
        get() = unit.trim().lowercase(Locale.US)

    val isGramUnit: Boolean
        get() = normalizedUnit in setOf("g", "gram", "grams")

    val isValid: Boolean
        get() = normalizedUnit.isNotEmpty() && gramsPerUnit > 0

    fun quantityFor(totalGrams: Double): Double {
        quantity?.takeIf { it > 0 }?.let { return it }
        return if (gramsPerUnit > 0) totalGrams / gramsPerUnit else totalGrams
    }

    fun displayUnit(
        quantity: Double?,
        servingLabel: String? = null,
        servingPluralLabel: String? = null,
        culinaryLabels: Map<String, Pair<String, String>> = emptyMap(),
    ): String {
        val singular = quantity == null || kotlin.math.abs(quantity - 1.0) <= 0.0001
        val culinaryKey = culinaryUnitKey(normalizedUnit)
        if (culinaryKey != null) {
            culinaryLabels[culinaryKey]?.let { (label, plural) ->
                return if (singular) label else plural
            }
        }
        // App-generated "serving" option (OFF barcode / AI fallback): the raw
        // unit string is English, so the UI passes the localized label(s).
        if (normalizedUnit in SERVING_UNITS && servingLabel != null) {
            return if (singular) servingLabel else (servingPluralLabel ?: servingLabel)
        }
        if (singular) return unit
        return when (normalizedUnit) {
            "g", "gram", "grams", "kg", "mg", "ml", "l", "oz", "fl oz", "tbsp", "tsp" -> unit
            "piece" -> "pieces"
            else -> if (unit.endsWith("s")) unit else "${unit}s"
        }
    }

    companion object {
        /** App-generated "serving" unit ids (OFF barcode lookup / AI fallback). */
        private val SERVING_UNITS = setOf("serving", "servings")

        /** Canonical key for a known English culinary unit (and common aliases). */
        fun culinaryUnitKey(normalizedUnit: String): String? = when (normalizedUnit) {
            "cup", "cups", "c" -> "cup"
            "tbsp", "tblsp", "tablespoon", "tablespoons" -> "tbsp"
            "tsp", "teaspoon", "teaspoons" -> "tsp"
            else -> null
        }

        val grams = ServingUnitOption(unit = "g", gramsPerUnit = 1.0)

        fun normalizedOptions(options: List<ServingUnitOption>, totalGrams: Double): List<ServingUnitOption> {
            val seen = mutableSetOf<String>()
            val normalized = mutableListOf<ServingUnitOption>()
            for (raw in options) {
                val option = if (raw.quantity == null && raw.gramsPerUnit > 0) {
                    raw.copy(quantity = totalGrams / raw.gramsPerUnit)
                } else {
                    raw
                }
                if (!option.isValid || option.isGramUnit || option.id in seen) continue
                seen.add(option.id)
                normalized.add(option)
            }
            return normalized.take(4)
        }

        fun pickerOptions(options: List<ServingUnitOption>): List<ServingUnitOption> {
            val seen = mutableSetOf(grams.id)
            val nonGram = options.filter { option ->
                option.isValid && !option.isGramUnit && seen.add(option.id)
            }
            return listOf(grams) + nonGram
        }

        fun optionMatching(id: String, options: List<ServingUnitOption>): ServingUnitOption =
            pickerOptions(options).firstOrNull { it.id == id } ?: grams

        /**
         * Serving-scaling rule (Codeberg #10 follow-up): an entry without a
         * recorded serving has macros that are absolute portion totals, so
         * weight edits must never scale them. Only entries with a recorded
         * serving scale from it.
         */
        fun servingScale(
            recordedServingGrams: Double?,
            servingGrams: Double,
            baseServingGrams: Double,
        ): Double =
            if (recordedServingGrams == null || baseServingGrams <= 0) 1.0
            else servingGrams / baseServingGrams

        /**
         * Serving to persist (Codeberg #10 follow-up): correcting the weight on
         * an entry without a recorded serving records it (macros stay
         * untouched); leaving it alone keeps the entry serving-less, so a later
         * edit cannot corrupt macros either. Entries with a recorded serving
         * persist the edited weight as before.
         */
        fun persistedServingGrams(
            recordedServingGrams: Double?,
            servingTouched: Boolean,
            servingGrams: Double,
        ): Double? =
            if (recordedServingGrams != null || servingTouched) servingGrams else null

        /**
         * Applies a per-entry custom serving edit (the serving-card pencil,
         * Codeberg #10 follow-up). Starting from a non-gram unit, the selected
         * option is renamed / re-weighted in place. Starting from plain grams,
         * the custom serving is **added alongside** the grams unit (which keeps
         * its 1 g identity) and a rename is required — committing a gram-named
         * option would corrupt the grams unit. Returns null when [grams] is not
         * positive or the edit would be a gram-named gram-unit no-op.
         */
        fun servingEdit(
            selectedUnitId: String,
            selectedOption: ServingUnitOption,
            unitOptions: List<ServingUnitOption>,
            name: String,
            grams: Double,
        ): ServingEditResult? {
            if (grams <= 0) return null
            val customName = name.trim().ifEmpty { selectedOption.unit }
            val gramNamed = ServingUnitOption(customName, gramsPerUnit = 1.0).isGramUnit
            if (selectedOption.isGramUnit && gramNamed) return null
            val updated = selectedOption.copy(unit = customName, gramsPerUnit = grams)
            val options = if (selectedOption.isGramUnit) {
                unitOptions.filter { !it.isGramUnit && it.id != updated.id } + updated
            } else {
                unitOptions.map { if (it.id == selectedUnitId) updated else it }
            }
            return ServingEditResult(updated, options)
        }

        fun initialUnitId(
            preferredUnit: String?,
            options: List<ServingUnitOption>
        ): String {
            val pickerOptions = pickerOptions(options)
            val preferredId = preferredUnit?.trim()?.lowercase(Locale.US)
            if (preferredId != null && pickerOptions.any { it.id == preferredId }) return preferredId
            return options.firstOrNull()?.id ?: grams.id
        }

        fun initialQuantityText(
            totalGrams: Double,
            selectedUnitId: String,
            selectedQuantity: Double?,
            options: List<ServingUnitOption>
        ): String {
            val option = optionMatching(selectedUnitId, options)
            if (selectedQuantity != null && selectedQuantity > 0 && !option.isGramUnit) {
                return formatQuantity(selectedQuantity)
            }
            val quantity = if (option.gramsPerUnit > 0) totalGrams / option.gramsPerUnit else totalGrams
            return formatQuantity(quantity)
        }

        fun formatQuantity(value: Double): String {
            if (value == value.toInt().toDouble()) return value.toInt().toString()
            val formatted = if (kotlin.math.abs(value) < 10) {
                String.format(Locale.US, "%.2f", value)
            } else {
                String.format(Locale.US, "%.1f", value)
            }
            return formatted.trimEnd('0').trimEnd('.')
        }

        fun parseQuantity(value: String, locale: Locale = Locale.getDefault()): Double? {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) return null
            trimmed.toDoubleOrNull()?.let { return it }

            if (trimmed.contains(',') && !trimmed.contains('.')) {
                trimmed.replace(',', '.').toDoubleOrNull()?.let { return it }
            }

            val symbols = DecimalFormatSymbols.getInstance(locale)
            val decimal = symbols.decimalSeparator
            if (decimal == '.' || !trimmed.contains(decimal)) return null

            var normalized = trimmed
            val grouping = symbols.groupingSeparator
            if (grouping != decimal) {
                normalized = normalized.replace(grouping.toString(), "")
            }
            normalized = normalized.replace(decimal, '.')
            return normalized.toDoubleOrNull()
        }

        /**
         * Parse a quantity-field input that may be a relative edit or a small
         * arithmetic expression:
         *
         *  - a leading `+` / `-` (ASCII or U+2212 minus) is a delta on
         *    [current] — "+20" means current + 20 (e.g. "I ate 50 g, then
         *    20 g more" → type "+20");
         *  - a string containing an infix operator (`+ - × ÷ * /`) is an
         *    absolute expression — "50×2" → 100, "200−30" → 170, with `× ÷`
         *    binding tighter than `+ -`;
         *  - anything else falls back to [parseQuantity].
         *
         * Callers must ignore non-positive results, as they do for plain
         * quantities. A lone sign, an empty delta, a malformed expression,
         * or a division by zero returns null.
         */
        fun applyDeltaInput(value: String, current: Double?, locale: Locale = Locale.getDefault()): Double? {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) return null
            val first = trimmed.first()
            if (first == '+' || first == '-' || first == MINUS_SIGN) {
                val rest = trimmed.substring(1).trim()
                if (rest.isEmpty()) return null
                val delta = parseQuantity(rest, locale) ?: return null
                val base = current ?: 0.0
                return if (first == '-' || first == MINUS_SIGN) base - delta else base + delta
            }
            if (isQuantityExpression(trimmed)) return evaluateExpression(trimmed, locale)
            return parseQuantity(trimmed, locale)
        }

        /**
         * True when [value] is a plain-number input that also contains an
         * infix operator (e.g. "50×2", "200−30") — i.e. something that
         * [applyDeltaInput] evaluates as an arithmetic expression rather
         * than an absolute number. A leading sign is a delta, not an
         * expression. Callers use this to keep the typed expression visible
         * with a live result instead of collapsing it to a number.
         */
        fun isQuantityExpression(value: String): Boolean {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) return false
            val first = trimmed.first()
            if (first == '+' || first == '-' || first == MINUS_SIGN) return false
            return trimmed.any { it in EXPRESSION_OPERATORS }
        }

        private const val MINUS_SIGN = '−'

        private val EXPRESSION_OPERATORS = setOf('+', '-', '−', '×', '÷', '*', '/')

        /**
         * Left-to-right arithmetic with `× ÷` binding tighter than `+ −`.
         * Tokens are locale-aware numbers ([parseQuantity]) and single-char
         * operators. Malformed chains, empty operands, and division by zero
         * return null.
         */
        private fun evaluateExpression(value: String, locale: Locale): Double? {
            val tokens = mutableListOf<Any>()
            val number = StringBuilder()
            for (ch in value) {
                if (ch in EXPRESSION_OPERATORS) {
                    if (number.isNotEmpty()) {
                        tokens.add(parseQuantity(number.toString(), locale) ?: return null)
                        number.clear()
                    }
                    tokens.add(ch)
                } else {
                    number.append(ch)
                }
            }
            if (number.isNotEmpty()) {
                tokens.add(parseQuantity(number.toString(), locale) ?: return null)
            }
            if (tokens.isEmpty() || tokens.first() !is Double || tokens.last() !is Double) return null

            val values = mutableListOf<Double>()
            val ops = mutableListOf<Char>()
            for (token in tokens) {
                when (token) {
                    is Double -> values.add(token)
                    is Char -> ops.add(token)
                }
            }
            if (values.size != ops.size + 1) return null

            // Pass 1: × ÷ * / (left to right).
            var i = 0
            while (i < ops.size) {
                val op = ops[i]
                if (op == '×' || op == '*' || op == '÷' || op == '/') {
                    val right = values[i + 1]
                    val result = if (op == '÷' || op == '/') {
                        if (right == 0.0) return null else values[i] / right
                    } else {
                        values[i] * right
                    }
                    values[i] = result
                    values.removeAt(i + 1)
                    ops.removeAt(i)
                } else {
                    i++
                }
            }

            // Pass 2: + - − (left to right).
            var acc = values.first()
            for (j in ops.indices) {
                val op = ops[j]
                val right = values[j + 1]
                acc = if (op == '-' || op == '−') acc - right else acc + right
            }
            return acc
        }
    }
}
