package app.chompass.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.models.BodyMeasurement
import app.chompass.models.UnitFormat
import java.util.Locale
import app.chompass.ui.theme.AppTextOpacity

private fun formatMeasurementLength(context: android.content.Context, cm: Double, useMetric: Boolean): String =
    if (useMetric) String.format(Locale.getDefault(), "%.1f %s", cm, context.getString(R.string.unit_cm))
    else String.format(Locale.getDefault(), "%.1f %s", UnitFormat.cmToInches(cm), context.getString(R.string.unit_in))

private fun formatMeasurementChange(context: android.content.Context, deltaCm: Double, useMetric: Boolean): String =
    if (useMetric) "${UnitFormat.signedDelta(deltaCm)} ${context.getString(R.string.unit_cm)}"
    else "${UnitFormat.signedDelta(UnitFormat.cmToInches(deltaCm))} ${context.getString(R.string.unit_in)}"

/**
 * One Progress-tab plot card for a single measurement site: header (site name +
 * latest value), Current / Net-change stat badges, and a compact trend chart
 * over the selected time range. Renders nothing when the range holds no data
 * for the site — an enabled site only ever appears with its chart.
 */
@Composable
internal fun MeasurementPlotCard(
    site: BodyMeasurement.Site,
    entries: List<BodyMeasurement>,
    useMetric: Boolean,
    chartsImmediate: Boolean = false,
) {
    val series = remember(entries, site) {
        entries.mapNotNull { entry ->
            entry.value(site)?.let { cm -> TrendPoint(entry.date.toEpochMilli(), cm) }
        }
    }
    if (series.isEmpty()) return
    val latest = series.last().value
    val first = series.first().value
    val netChange = latest - first
    val context = LocalContext.current

    CardSection {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(site.labelRes),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    formatMeasurementLength(context, latest, useMetric),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                )
            }
            StatBadgeRow(
                listOf(
                    stringResource(R.string.progress_stat_current) to formatMeasurementLength(context, latest, useMetric),
                    stringResource(R.string.progress_stat_net_change) to formatMeasurementChange(context, netChange, useMetric),
                )
            )
            DeferredChart(immediate = chartsImmediate) {
                MeasurementChartCanvas(series = series, immediate = chartsImmediate)
            }
        }
    }
}
