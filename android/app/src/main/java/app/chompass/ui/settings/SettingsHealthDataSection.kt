package app.chompass.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.chompass.R
import app.chompass.ui.components.FudIconBubble
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.warning

@Composable
internal fun SettingsHealthDataSection(
    ui: SettingsUiState,
    safetyMedicalExpanded: Boolean,
    onToggleSafetyMedical: () -> Unit,
    onHealthConnectToggle: (Boolean) -> Unit,
    onManageHealthAccess: () -> Unit,
    backgroundSyncSupported: Boolean,
    onBackgroundSyncToggle: (Boolean) -> Unit,
    onShowExportDiary: () -> Unit,
    onShowExportBodyMetrics: () -> Unit,
    onImportDiary: () -> Unit,
    onImportBodyMetrics: () -> Unit,
    onShowClearFoodDialog: () -> Unit,
    onShowDeleteDialog: () -> Unit,
    onOpenSync: () -> Unit,
    syncSummary: String?,
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
                    if (backgroundSyncSupported) {
                        ToggleRow(
                            stringResource(R.string.settings_health_background_sync),
                            ui.healthBackgroundSyncEnabled,
                            onChange = onBackgroundSyncToggle
                        )
                        Text(
                            stringResource(R.string.settings_health_background_sync_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
                        )
                    } else {
                        Text(
                            stringResource(R.string.settings_health_background_sync_unsupported),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
                        )
                    }
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
                        Text(
                            stringResource(R.string.settings_safety_water),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
                        )
                        Text(
                            stringResource(R.string.settings_accuracy_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        Text(
                            stringResource(R.string.settings_accuracy_body),
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
                        .clickable { onImportBodyMetrics() }
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
                // Cross-link (Rule A): the WebDAV form lives in its own Sync screen.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSync() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FudIconBubble(icon = Icons.Outlined.Sync, size = 22.dp, iconSize = 14.dp, tint = AppColors.Calorie)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_sync_section),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        if (syncSummary != null) {
                            Text(
                                syncSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            )
                        }
                    }
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    )
                }
    }

    // Danger zone: destructive actions are visually separated from routine
    // transfer actions so they can't be tapped by accident.
    SectionCard(title = stringResource(R.string.settings_danger_zone)) {
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
                SettingFootnote(stringResource(R.string.settings_danger_zone_caption))
    }
}
