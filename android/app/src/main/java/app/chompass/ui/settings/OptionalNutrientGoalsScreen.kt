package app.chompass.ui.settings

import androidx.navigation.NavHostController
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import app.chompass.AppContainer
import app.chompass.R
import app.chompass.models.OptionalNutrient
import app.chompass.ui.components.ChompassDialog
import app.chompass.ui.components.ChompassDialogActions
import app.chompass.ui.components.ChompassSurface
import app.chompass.ui.components.ChompassIconBubble
import app.chompass.ui.navigation.BottomNavScrollPadding
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.macroAccentColor
import app.chompass.ui.theme.AppRadii
import app.chompass.ui.theme.AppTextOpacity

@Composable
fun OptionalNutrientGoalsScreen(
    container: AppContainer,
    nav: NavHostController? = null,
    onBack: () -> Unit
) {
    val vm: SettingsViewModel = rememberSettingsViewModel(container, nav)
    val ui by vm.ui.collectAsState()
    var editing by remember { mutableStateOf<OptionalNutrient?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SettingsTopBar(
                title = stringResource(R.string.settings_other_nutrient_goals),
                onBack = onBack,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = 14.dp,
                bottom = BottomNavScrollPadding
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Opt-in AI estimate (Codeberg #20 phase 2: hidden with the master AI
            // switch off — this is a purely-LLM feature with no formula fallback).
            // Never fired automatically: Recalculate keeps optional goals untouched.
            if (ui.aiFeaturesEnabled) {
                item {
                    ChompassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = AppRadii.SectionCard,
                        padding = 0.dp,
                                ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !ui.estimatingOptionalNutrientGoals) {
                                    vm.estimateOptionalNutrientGoals()
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ChompassIconBubble(icon = Icons.Outlined.AutoAwesome, size = 22.dp, iconSize = 14.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.settings_optional_nutrient_estimate_ai),
                                    color = if (ui.estimatingOptionalNutrientGoals) {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Faint)
                                    } else {
                                        AppColors.Calorie
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    stringResource(R.string.settings_optional_nutrient_estimate_ai_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted)
                                )
                            }
                            if (ui.estimatingOptionalNutrientGoals) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
            item {
                ChompassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = AppRadii.SectionCard,
                    padding = 0.dp,
                        ) {
                    Column {
                        OptionalNutrient.values().forEachIndexed { index, nutrient ->
                            OptionalNutrientGoalRow(
                                nutrient = nutrient,
                                value = ui.optionalNutrientGoals.valueFor(nutrient),
                                onClick = { editing = nutrient }
                            )
                            if (index != OptionalNutrient.values().lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "Separate from calorie, protein, carb, and fat goals.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Muted),
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
            }
        }
    }

    editing?.let { nutrient ->
        val iuTemplate = stringResource(R.string.settings_picker_vitd_iu_hint)
        ChompassDialog(onDismissRequest = { editing = null }) {
            NutritionPickerSheet(
                label = stringResource(nutrient.displayNameRes),
                unit = stringResource(nutrient.unitRes),
                currentValue = ui.optionalNutrientGoals.valueFor(nutrient),
                range = nutrient.goalRange,
                step = nutrient.goalStep,
                accentColor = nutrient.macroAccentColor() ?: AppColors.Calorie,
                confirmAbove = nutrient.softMax,
                conversionHintFor = if (nutrient == OptionalNutrient.VITAMIN_D) { v ->
                    String.format(java.util.Locale.getDefault(), iuTemplate, v, v * 40)
                } else null,
                onSave = { value ->
                    vm.setOptionalNutrientGoals(ui.optionalNutrientGoals.withValue(nutrient, value))
                    editing = null
                },
                onDismiss = { editing = null }
            )
        }
    }

    ui.optionalNutrientEstimateAlertMessage?.let { message ->
        ChompassDialog(onDismissRequest = { vm.dismissOptionalNutrientEstimateAlert() }) {
            Text(
                stringResource(R.string.settings_optional_nutrient_estimate_failed_title),
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                message,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Secondary)
            )
            ChompassDialogActions(
                primaryText = stringResource(R.string.action_ok),
                onPrimary = { vm.dismissOptionalNutrientEstimateAlert() }
            )
        }
    }
}
