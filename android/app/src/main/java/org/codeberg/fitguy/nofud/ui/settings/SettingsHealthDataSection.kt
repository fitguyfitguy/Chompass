package org.codeberg.fitguy.nofud.ui.settings

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
import org.codeberg.fitguy.nofud.R
import org.codeberg.fitguy.nofud.models.AutoBalanceMacro
import org.codeberg.fitguy.nofud.models.DietMode
import org.codeberg.fitguy.nofud.models.KetoCarbMode
import org.codeberg.fitguy.nofud.models.ServingUnitInferenceMode
import org.codeberg.fitguy.nofud.models.WeightGoal
import org.codeberg.fitguy.nofud.ui.components.FudIconBubble
import org.codeberg.fitguy.nofud.ui.navigation.NoFUDRoutes
import org.codeberg.fitguy.nofud.ui.theme.AppColors
import org.codeberg.fitguy.nofud.ui.theme.AppThemeColor
import org.codeberg.fitguy.nofud.ui.theme.warning
import java.util.Locale


import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Info
import androidx.compose.ui.graphics.Color
@Composable
internal fun SettingsHealthDataSection(
    ui: SettingsUiState,
    vm: SettingsViewModel,
    safetyMedicalExpanded: Boolean,
    onToggleSafetyMedical: () -> Unit,
    onHealthConnectToggle: (Boolean) -> Unit,
    onManageHealthAccess: () -> Unit,
    onShowExportDiary: () -> Unit,
    onShowExportBodyMetrics: () -> Unit,
    onImportDiary: () -> Unit,
    onImportBodyMetrics: () -> Unit,
    onShowClearFoodDialog: () -> Unit,
    onShowDeleteDialog: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.settings_section_health)) {
                ToggleRow(stringResource(R.string.settings_health_connect), ui.healthConnectEnabled, icon = Icons.Outlined.Favorite, onChange = onHealthConnectToggle)
                HorizontalDivider()
                SettingRow(
                    stringResource(R.string.settings_manage_health_access),
                    stringResource(R.string.settings_permissions),
                    icon = Icons.Outlined.Link,
                    onClick = onManageHealthAccess,
                )
                if (ui.healthConnectEnabled) {
                    Text(
                        stringResource(R.string.settings_health_companions),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
                    )
                    ToggleRow(
                        stringResource(R.string.settings_health_background_sync),
                        ui.healthBackgroundSyncEnabled,
                        onChange = { vm.setHealthBackgroundSyncEnabled(it) }
                    )
                    Text(
                        stringResource(R.string.settings_health_background_sync_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
                    )
                }
                HorizontalDivider()
                SettingRow(
                    label = stringResource(R.string.settings_safety_medical),
                    value = stringResource(
                        if (safetyMedicalExpanded) R.string.settings_safety_hide
                        else R.string.settings_safety_open
                    ),
                    icon = Icons.Outlined.Info
                ) { onToggleSafetyMedical() }
                if (safetyMedicalExpanded) {
                    HorizontalDivider()
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            stringResource(R.string.settings_safety_summary),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            stringResource(R.string.settings_safety_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
                        )
                        Text(
                            stringResource(R.string.settings_safety_consult),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
                        )
                        Text(
                            stringResource(R.string.settings_safety_ed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
                        )
                        Text(
                            stringResource(R.string.settings_safety_low_bf),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
                        )
                        Text(
                            stringResource(R.string.settings_safety_escalate),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
                        )
                    }
                    HorizontalDivider()
                } else {
                    HorizontalDivider()
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onShowExportDiary() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FudIconBubble(icon = Icons.Outlined.IosShare, size = 22.dp, iconSize = 14.dp, tint = AppColors.Calorie)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        stringResource(R.string.export_diary_title),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
                HorizontalDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onShowExportBodyMetrics() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FudIconBubble(icon = Icons.Outlined.MonitorWeight, size = 22.dp, iconSize = 14.dp, tint = AppColors.Calorie)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        stringResource(R.string.export_body_metrics_title),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
                HorizontalDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onImportDiary() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FudIconBubble(icon = Icons.Outlined.Link, size = 22.dp, iconSize = 14.dp, tint = AppColors.Calorie)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        stringResource(R.string.import_diary_title),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
                HorizontalDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            // CSV is often reported as octet-stream by SAF providers, so accept broadly.
                            onImportBodyMetrics()
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FudIconBubble(icon = Icons.Outlined.MonitorWeight, size = 22.dp, iconSize = 14.dp, tint = AppColors.Calorie)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        stringResource(R.string.import_body_metrics_title),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
                HorizontalDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onShowClearFoodDialog() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val warning = MaterialTheme.colorScheme.warning
                    FudIconBubble(icon = Icons.Outlined.DeleteSweep, size = 22.dp, iconSize = 14.dp, tint = warning)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        stringResource(R.string.settings_clear_food_log),
                        color = warning,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
                HorizontalDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onShowDeleteDialog() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val destructive = MaterialTheme.colorScheme.error
                    FudIconBubble(icon = Icons.Outlined.DeleteForever, size = 22.dp, iconSize = 14.dp, tint = destructive)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        stringResource(R.string.settings_delete_all_data),
                        color = destructive,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
    }
}
