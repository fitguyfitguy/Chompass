package app.chompass.ui.progress

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.chompass.R
import app.chompass.models.BodyMeasurement
import app.chompass.models.UnitFormat
import app.chompass.ui.components.ChompassDialog
import app.chompass.ui.components.UnitToggle
import app.chompass.ui.settings.NutritionPickerSheet

/**
 * One-site circumference editor shared by Settings → Body measurements and the
 * Progress body card's per-site `+` actions. [currentCm] seeds the wheel;
 * `onSave(null)` clears the site (the reset action).
 */
@Composable
internal fun BodyMeasurementEntryDialog(
    site: BodyMeasurement.Site,
    currentCm: Double?,
    useMetric: Boolean,
    onUnitChange: (Boolean) -> Unit,
    onSave: (Double?) -> Unit,
    onDismiss: () -> Unit,
) {
    var editorValue by remember(site) {
        mutableStateOf(
            currentCm?.let {
                if (useMetric) Math.round(it).toInt()
                else Math.round(UnitFormat.cmToInches(it)).toInt()
            } ?: if (useMetric) 80 else 32
        )
    }
    ChompassDialog(onDismissRequest = onDismiss) {
        UnitToggle(
            stringResource(R.string.unit_cm),
            stringResource(R.string.unit_in),
            useMetric,
            { metric ->
                if (metric != useMetric) {
                    editorValue = if (metric) {
                        UnitFormat.inchesToCmRounded(editorValue).coerceIn(10, 250)
                    } else {
                        UnitFormat.cmToInchesRounded(editorValue).coerceIn(4, 100)
                    }
                    onUnitChange(metric)
                }
            },
            Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        key(useMetric) {
            NutritionPickerSheet(
                label = stringResource(site.labelRes),
                showTitle = true,
                unit = stringResource(if (useMetric) R.string.unit_cm else R.string.unit_in),
                currentValue = editorValue,
                range = if (useMetric) 10..250 else 4..100,
                step = 1,
                onSave = { value ->
                    onSave(
                        if (useMetric) value.toDouble()
                        else UnitFormat.inchesToCm(value.toDouble()),
                    )
                },
                onResetToAuto = if (currentCm != null) {
                    { onSave(null) }
                } else null,
                resetLabel = stringResource(R.string.action_clear),
                onValueChange = { editorValue = it },
                onDismiss = onDismiss,
            )
        }
    }
}
