package app.chompass.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.outlined.LocalDining
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.models.ServingUnitInferenceMode
import app.chompass.ui.components.FudGlassDialog
import app.chompass.ui.components.FudGlassDialogActions

/**
 * Food & Entry settings: how logging behaves (units, sort, meal times) and how
 * photo analysis works (note prompt, portion clarify, constituents, serving
 * size detection). Provider wiring lives in AI & Speech.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodEntrySettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(container))
    val ui by vm.ui.collectAsState()
    var sheet by remember { mutableStateOf<SettingsSheet?>(null) }
    var showDefaultGramsInfo by remember { mutableStateOf(false) }

    SettingsSubScreen(
        title = stringResource(R.string.settings_group_food),
        onBack = onBack,
    ) {
        Text(
            stringResource(R.string.settings_food_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )

        SectionCard(title = stringResource(R.string.settings_food_section_logging)) {
            ToggleRowWithInfo(
                label = stringResource(R.string.settings_default_to_grams),
                checked = ui.preferGramsByDefault,
                icon = Icons.Outlined.LocalDining,
                onInfo = { showDefaultGramsInfo = true },
                onChange = vm::setPreferGramsByDefault
            )
            HorizontalDivider()
            SettingRow(
                stringResource(R.string.settings_food_log_sort),
                stringResource(ui.foodLogSortOrder.displayNameRes),
                icon = Icons.Filled.UnfoldMore
            ) { sheet = SettingsSheet.FOOD_LOG_SORT }
            HorizontalDivider()
            SettingRow(
                stringResource(R.string.settings_meal_times),
                stringResource(R.string.settings_meal_times_customize),
                icon = Icons.Outlined.Schedule,
            ) { sheet = SettingsSheet.MEAL_TIMES }
        }

        SectionCard(title = stringResource(R.string.settings_food_section_photo)) {
            ToggleRow(
                stringResource(R.string.settings_photo_note_prompt),
                checked = !ui.skipPhotoNotePrompt,
                icon = Icons.Outlined.Notes,
                onChange = { vm.setAskPhotoNotePrompt(it) }
            )
            SettingFootnote(stringResource(R.string.settings_photo_note_prompt_footer))
            HorizontalDivider()
            ToggleRow(
                stringResource(R.string.settings_portion_clarify),
                ui.portionClarifyEnabled,
                icon = Icons.Outlined.Straighten,
                onChange = { vm.setPortionClarifyEnabled(it) }
            )
            SettingFootnote(stringResource(R.string.settings_portion_clarify_footer))
            HorizontalDivider()
            val constituentsAvailable = ui.selectedAI != app.chompass.models.AIProvider.ON_DEVICE
            ToggleRow(
                stringResource(R.string.settings_meal_constituents),
                checked = constituentsAvailable && ui.mealConstituentsEnabled,
                icon = Icons.Outlined.Restaurant,
                enabled = constituentsAvailable,
                onChange = { vm.setMealConstituentsEnabled(it) }
            )
            SettingFootnote(
                stringResource(
                    if (constituentsAvailable) {
                        R.string.settings_meal_constituents_footer
                    } else {
                        R.string.settings_meal_constituents_footer_on_device
                    },
                ),
            )
        }

        SectionCard(title = stringResource(R.string.settings_food_section_serving)) {
            SettingRow(
                stringResource(R.string.settings_serving_unit_mode),
                stringResource(ui.servingUnitInferenceMode.displayNameRes),
                icon = Icons.Outlined.Tune
            ) { sheet = SettingsSheet.SERVING_UNIT_MODE }
            if (ui.servingUnitInferenceMode == ServingUnitInferenceMode.HEURISTIC) {
                HorizontalDivider()
                SettingRow(
                    stringResource(R.string.settings_serving_unit_heuristics),
                    stringResource(R.string.settings_tap_to_edit),
                    icon = Icons.Outlined.Tune
                ) { sheet = SettingsSheet.SERVING_UNIT_HEURISTICS }
            }
        }

        Spacer(Modifier.height(4.dp))
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

    if (showDefaultGramsInfo) {
        FudGlassDialog(onDismissRequest = { showDefaultGramsInfo = false }) {
            Text(stringResource(R.string.settings_default_to_grams), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.settings_default_to_grams_info),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
            )
            FudGlassDialogActions(
                primaryText = stringResource(R.string.action_ok),
                onPrimary = { showDefaultGramsInfo = false }
            )
        }
    }
}
