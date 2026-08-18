package app.chompass.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.chompass.R
import app.chompass.models.LocaleFormat

/**
 * Display helpers for value + unit rows (UI-audit 2.2/2.3): group numbers with
 * the display locale's thousands separator and pull the unit from resources so
 * localized units (e.g. Russian "ккал"/"г") reach every row, not just the
 * picker sheets.
 */
@Composable
internal fun kcalText(value: Int): String =
    stringResource(R.string.kcal_value_format, LocaleFormat.integer(value))

/** "150 g" / "1,234.5 g" — whole grams group; fractions keep one decimal. */
@Composable
internal fun gramsText(value: Double): String {
    val isWhole = value == value.toInt().toDouble()
    val res = if (isWhole) R.string.grams_value_format else R.string.grams_value_decimal_format
    val formatted = if (isWhole) LocaleFormat.integer(value.toInt()) else LocaleFormat.decimal(value, 1)
    return stringResource(res, formatted)
}
