package app.chompass.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.models.BodyMeasurement
import app.chompass.models.UnitFormat
import app.chompass.ui.components.FudGlassSurface
import app.chompass.ui.progress.TimeRange
import java.util.Locale
import app.chompass.ui.theme.AppRadii
import app.chompass.ui.theme.AppTextOpacity

/**
 * Settings → App & Display → Customize progress. Groups everything that tunes the
 * Progress tab: the default time range (moved here from App & Display) and the
 * optional per-site body-measurement trend plots (off by default). Plot data comes
 * from Personal Info → Body measurements; enabling a site here just renders its
 * trend card on the Progress tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizeProgressScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(container))
    val ui by vm.ui.collectAsState()
    var sheet by remember { mutableStateOf<SettingsSheet?>(null) }
    val entries by container.bodyMeasurementRepository.entries.collectAsState(initial = emptyList())
    val heightUnit by container.prefs.heightUnit.collectAsState(initial = "cm")
    val heightMetric = heightUnit == "cm"
    val latest = entries.maxByOrNull { it.date }
    val selectedSites = ui.progressMeasurementSites

    SettingsSubScreen(
        title = stringResource(R.string.settings_customize_progress),
        onBack = onBack,
    ) {
        FudGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = AppRadii.SectionCard,
            padding = 0.dp,
            allowBlur = false,
        ) {
            SettingRow(
                label = stringResource(R.string.settings_progress_default_range),
                value = stringResource(TimeRange.fromStorageId(ui.progressDefaultRangeId).labelRes),
                onClick = { sheet = SettingsSheet.PROGRESS_DEFAULT_RANGE },
            )
        }

        FudGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = AppRadii.SectionCard,
            padding = 0.dp,
            allowBlur = false,
        ) {
            Column {
                Text(
                    stringResource(R.string.settings_progress_plots),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
                BodyMeasurement.Site.values().forEachIndexed { index, site ->
                    val storageId = site.storageId
                    MeasurementSiteToggleRow(
                        site = site,
                        checked = storageId in selectedSites,
                        subtitle = measurementSiteSubtitle(
                            context = LocalContext.current,
                            site = site,
                            latest = latest,
                            heightMetric = heightMetric,
                        ),
                        onChange = { on ->
                            vm.setProgressMeasurementSites(
                                if (on) selectedSites + storageId else selectedSites - storageId
                            )
                        },
                    )
                    if (index != BodyMeasurement.Site.values().lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    }
                }
            }
        }

        Text(
            stringResource(R.string.settings_progress_plots_subtitle),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }

    sheet?.let { s ->
        SettingsSheets(
            sheet = s,
            ui = ui,
            vm = vm,
            onDismiss = { sheet = null },
            onInvalidGoalWeight = {},
            onRebalanceBlocked = {},
        )
    }
}

@Composable
private fun MeasurementSiteToggleRow(
    site: BodyMeasurement.Site,
    checked: Boolean,
    subtitle: String,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                stringResource(site.labelRes),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
            )
        }
        Spacer(Modifier.padding(start = 8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun measurementSiteSubtitle(
    context: android.content.Context,
    site: BodyMeasurement.Site,
    latest: BodyMeasurement?,
    heightMetric: Boolean,
): String {
    val cm = latest?.value(site) ?: return context.getString(R.string.settings_progress_plots_no_data)
    return if (heightMetric) {
        context.getString(
            R.string.settings_progress_plots_latest,
            String.format(Locale.getDefault(), "%.0f", cm),
            context.getString(R.string.unit_cm),
        )
    } else {
        context.getString(
            R.string.settings_progress_plots_latest,
            String.format(Locale.getDefault(), "%.0f", UnitFormat.cmToInches(cm)),
            context.getString(R.string.unit_in),
        )
    }
}
