package app.chompass.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Height
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import app.chompass.R
import app.chompass.ui.navigation.ChompassRoutes
import app.chompass.models.UnitFormat

@Composable
internal fun SettingsPersonalSection(
    ui: SettingsUiState,
    profile: app.chompass.models.UserProfile?,
    latestMeasurement: app.chompass.models.BodyMeasurement?,
    nav: NavHostController,
    onOpenSheet: (SettingsSheet) -> Unit,
    onToggleUseBodyFatInBmr: (Boolean) -> Unit = {},
) {
    SectionCard(title = stringResource(R.string.settings_section_personal)) {
                profile?.let { p ->
                    SettingRow(stringResource(R.string.settings_gender), stringResource(p.gender.displayNameRes), icon = Icons.Outlined.Person, inlineMenu = true) { onOpenSheet(SettingsSheet.GENDER) }
                    HorizontalDivider()
                    SettingRow(stringResource(R.string.settings_birthday), birthdayDisplay(p), icon = Icons.Outlined.Cake) { onOpenSheet(SettingsSheet.BIRTHDAY) }
                    HorizontalDivider()
                    SettingRow(
                        stringResource(R.string.settings_height),
                        if (ui.heightMetric) stringResource(R.string.height_cm_format, p.heightCm.toInt())
                        else feetInchesLabel(p.heightCm.toInt()),
                        icon = Icons.Outlined.Height
                    ) { onOpenSheet(SettingsSheet.HEIGHT) }
                    HorizontalDivider()
                    SettingRow(
                        stringResource(R.string.settings_weight),
                        if (ui.weightMetric) stringResource(R.string.kg_value_format, p.weightKg)
                        else stringResource(R.string.lbs_value_format, UnitFormat.kgToLbs(p.weightKg)),
                        icon = Icons.Outlined.MonitorWeight
                    ) { onOpenSheet(SettingsSheet.WEIGHT) }
                    HorizontalDivider()
                    SettingRow(
                        stringResource(R.string.settings_body_fat),
                        p.bodyFatPercentage?.let { "${(it * 100).toInt()}%" } ?: stringResource(R.string.settings_not_set),
                        icon = Icons.Outlined.Percent
                    ) { onOpenSheet(SettingsSheet.BODY_FAT) }

                    if (p.bodyFatPercentage != null) {
                        HorizontalDivider()
                        ToggleRow(
                            label = stringResource(R.string.settings_use_body_fat_bmr),
                            checked = p.useBodyFatInBMR != false,
                            onChange = onToggleUseBodyFatInBmr,
                        )
                        HorizontalDivider()
                        SettingRow(
                            stringResource(R.string.settings_goal_body_fat),
                            p.goalBodyFatPercentage?.let { "${(it * 100).toInt()}%" } ?: stringResource(R.string.settings_not_set),
                            icon = Icons.Outlined.TrackChanges
                        ) { onOpenSheet(SettingsSheet.GOAL_BODY_FAT) }
                    }
                    HorizontalDivider()
                    // Optional tape-measure circumferences — extra signal for the AI goal calc +
                    // Coach. Never edits BMR / the body-fat field.
                    SettingRow(
                        stringResource(R.string.body_measurements_title),
                        latestMeasurement?.waistCm?.let { waist ->
                            if (ui.heightMetric) stringResource(R.string.settings_waist_cm_format, waist)
                            else stringResource(R.string.settings_waist_in_format, UnitFormat.cmToInches(waist))
                        } ?: stringResource(R.string.settings_not_set),
                        icon = Icons.Outlined.Straighten
                    ) { nav.navigate(ChompassRoutes.BODY_MEASUREMENTS) }
                }
    }
}
