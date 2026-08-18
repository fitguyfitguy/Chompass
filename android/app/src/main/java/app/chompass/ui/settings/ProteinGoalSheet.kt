package app.chompass.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.models.ProteinTargetMode
import app.chompass.models.UserProfile
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.AppTextOpacity

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProteinGoalSheet(
    profile: UserProfile?,
    onModeChange: (ProteinTargetMode) -> Unit,
    onSaveGrams: (Int) -> Unit,
    onResetToAuto: (() -> Unit)?,
) {
    val mode = profile?.proteinTargetMode ?: ProteinTargetMode.Default
    val grams = profile?.effectiveProtein ?: 0
    val rate = profile?.proteinGramsPerKg
        ?: if (profile != null && mode.usesRate) grams / profile.proteinTargetBasisKg else null

    Text(
        stringResource(R.string.protein_target_mode_label),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
    )
    Spacer(Modifier.height(6.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ProteinTargetMode.entries.forEach { option ->
            FilterChip(
                selected = option == mode,
                onClick = { if (option != mode) onModeChange(option) },
                label = { Text(stringResource(option.displayNameRes)) },
            )
        }
    }
    if (mode == ProteinTargetMode.G_PER_KG_LBM && profile?.bodyFatPercentage == null) {
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.protein_target_lbm_missing_hint),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
        )
    }
    if (mode.usesRate && rate != null) {
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.protein_target_g_per_kg_format, rate, grams),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
        )
    }
    Spacer(Modifier.height(4.dp))

    NutritionPickerSheet(
        label = stringResource(R.string.macro_protein),
        unit = stringResource(R.string.unit_g),
        currentValue = grams,
        range = 10..500,
        step = 5,
        accentColor = AppColors.Protein,
        onSave = onSaveGrams,
        onResetToAuto = onResetToAuto,
    )
}
