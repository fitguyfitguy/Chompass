package app.chompass.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.Height
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocalDining
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import app.chompass.R
import app.chompass.models.AutoBalanceMacro
import app.chompass.models.DietMode
import app.chompass.models.KetoCarbMode
import app.chompass.models.ServingUnitInferenceMode
import app.chompass.models.WeightGoal
import app.chompass.ui.components.FudIconBubble
import app.chompass.ui.navigation.ChompassRoutes
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.AppThemeColor
import java.util.Locale


@Composable
internal fun SettingsPersonalSection(
    ui: SettingsUiState,
    profile: app.chompass.models.UserProfile?,
    latestMeasurement: app.chompass.models.BodyMeasurement?,
    nav: NavHostController,
    onOpenSheet: (SettingsSheet) -> Unit,
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
                        if (ui.weightMetric) String.format(Locale.US, "%.1f kg", p.weightKg)
                        else String.format(Locale.US, "%.1f lbs", p.weightKg * 2.20462),
                        icon = Icons.Outlined.MonitorWeight
                    ) { onOpenSheet(SettingsSheet.WEIGHT) }
                    HorizontalDivider()
                    SettingRow(
                        stringResource(R.string.settings_body_fat),
                        p.bodyFatPercentage?.let { "${(it * 100).toInt()}%" } ?: stringResource(R.string.settings_not_set),
                        icon = Icons.Outlined.Percent
                    ) { onOpenSheet(SettingsSheet.BODY_FAT) }

                    // Goal Body Fat only renders when the user actually has a body
                    // fat % set — avoids surfacing irrelevant controls to users who
                    // never opted in. When body fat is set it is always used for BMR
                    // (Katch-McArdle); otherwise Mifflin-St Jeor — no manual toggle.
                    if (p.bodyFatPercentage != null) {
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
                            else stringResource(R.string.settings_waist_in_format, waist / 2.54)
                        } ?: stringResource(R.string.settings_not_set),
                        icon = Icons.Outlined.Straighten
                    ) { nav.navigate(ChompassRoutes.BODY_MEASUREMENTS) }
                }
    }
}
