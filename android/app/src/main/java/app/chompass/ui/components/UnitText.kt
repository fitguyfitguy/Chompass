package app.chompass.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.chompass.R
import app.chompass.models.EnergyFormat
import app.chompass.models.EnergyUnit
import app.chompass.models.LocaleFormat
import app.chompass.models.MacroValueFormatter
import app.chompass.ui.navigation.LocalEnergyUnit

/**
 * Display helpers for value + unit rows (UI-audit 2.2/2.3): group numbers with
 * the display locale's thousands separator and pull the unit from resources so
 * localized units (e.g. Russian "ккал"/"г") reach every row, not just the
 * picker sheets.
 */
@Composable
internal fun energyText(kcal: Int): String {
    val unit = LocalEnergyUnit.current
    return stringResource(
        R.string.energy_value_format,
        LocaleFormat.integer(EnergyFormat.quantity(kcal, unit)),
        stringResource(if (unit == EnergyUnit.KJ) R.string.unit_kj else R.string.unit_kcal),
    )
}

@Composable
internal fun energyUnitLabel(): String =
    stringResource(if (LocalEnergyUnit.current == EnergyUnit.KJ) R.string.unit_kj else R.string.unit_kcal)

/** "150 g" / "1,234.5 g" — whole grams group; fractions keep one decimal. */
@Composable
internal fun gramsText(value: Double): String {
    val isWhole = value == value.toInt().toDouble()
    val res = if (isWhole) R.string.grams_value_format else R.string.grams_value_decimal_format
    val formatted = if (isWhole) LocaleFormat.integer(value.toInt()) else LocaleFormat.decimal(value, 1)
    return stringResource(res, formatted)
}

/** "30g" — macro chips/totals keep the no-space form; unit comes from resources (UI-audit 2.3). */
@Composable
internal fun macroGramsText(value: Double): String =
    "${MacroValueFormatter.string(value)}${stringResource(R.string.unit_g)}"
