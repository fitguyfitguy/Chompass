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
import app.chompass.ui.components.FudGlassDialog
import app.chompass.ui.components.FudGlassSurface
import app.chompass.ui.components.FudGlassTextButton
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

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
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
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onBack() }
                            .padding(horizontal = 2.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = AppColors.Calorie,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.nav_settings),
                            color = AppColors.Calorie,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            item {
                Text(
                    stringResource(R.string.settings_other_nutrient_goals),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            item {
                FudGlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = AppRadii.SectionCard,
                    padding = 0.dp,
                    allowBlur = false
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
        FudGlassDialog(onDismissRequest = { editing = null }) {
            NutritionPickerSheet(
                label = stringResource(nutrient.displayNameRes),
                unit = stringResource(nutrient.unitRes),
                currentValue = ui.optionalNutrientGoals.valueFor(nutrient),
                range = nutrient.goalRange,
                step = nutrient.goalStep,
                accentColor = nutrient.macroAccentColor() ?: AppColors.Calorie,
                maxCustomGoal = nutrient.maxCustomGoal,
                conversionHintFor = if (nutrient == OptionalNutrient.VITAMIN_D) { v ->
                    String.format(java.util.Locale.getDefault(), iuTemplate, v, v * 40)
                } else null,
                onSave = { value ->
                    vm.setOptionalNutrientGoals(ui.optionalNutrientGoals.withValue(nutrient, value))
                    editing = null
                }
            )
            FudGlassTextButton(
                text = stringResource(R.string.action_cancel),
                onClick = { editing = null },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
            )
        }
    }
}
