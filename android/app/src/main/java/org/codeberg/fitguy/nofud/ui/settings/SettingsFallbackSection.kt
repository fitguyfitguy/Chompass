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
import java.util.Locale


import org.codeberg.fitguy.nofud.models.AIProvider
@Composable
internal fun SettingsFallbackSection(ui: SettingsUiState, vm: SettingsViewModel, onOpenSheet: (SettingsSheet) -> Unit) {
    SectionCard(title = stringResource(R.string.settings_section_fallback)) {
                ToggleRow(
                    stringResource(R.string.settings_enable_fallback),
                    ui.fallbackEnabled,
                    icon = Icons.Outlined.Refresh,
                    onChange = { vm.setFallbackEnabled(it) }
                )
                if (ui.fallbackEnabled) {
                    HorizontalDivider()
                    SettingRow(
                        stringResource(R.string.settings_ai_provider),
                        stringResource(ui.fallbackProvider.displayNameRes),
                        icon = Icons.Outlined.SmartToy
                    ) { onOpenSheet(SettingsSheet.FALLBACK_PROVIDER) }
                    HorizontalDivider()
                    SettingRow(
                        stringResource(R.string.settings_ai_model),
                        ui.fallbackModel.ifEmpty { stringResource(R.string.settings_ai_model_unset) },
                        icon = Icons.Outlined.Tune
                    ) { onOpenSheet(SettingsSheet.FALLBACK_MODEL) }
                    if (ui.fallbackProvider.requiresApiKey) {
                        HorizontalDivider()
                        SettingRow(
                            stringResource(R.string.settings_api_key),
                            ui.fallbackApiKeyMasked.ifEmpty { stringResource(R.string.settings_not_set) },
                            icon = Icons.Outlined.Key
                        ) { onOpenSheet(SettingsSheet.FALLBACK_KEY) }
                    }
                    if (ui.fallbackProvider.requiresCustomEndpoint || ui.fallbackProvider == AIProvider.OLLAMA) {
                        HorizontalDivider()
                        SettingRow(
                            if (ui.fallbackProvider.requiresCustomEndpoint) stringResource(R.string.settings_base_url) else stringResource(R.string.settings_server_url),
                            stringResource(R.string.settings_tap_to_edit),
                            icon = Icons.Outlined.Link
                        ) { onOpenSheet(SettingsSheet.FALLBACK_BASE_URL) }
                    }
                    Text(
                        stringResource(R.string.settings_fallback_footer),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
    }
}
