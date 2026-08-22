package app.chompass.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.chompass.R
import app.chompass.models.LocaleFormat
import app.chompass.services.ai.GoalRecalcTier
import app.chompass.services.ai.ImpliedWithheldReason
import app.chompass.services.ai.RecalcSheetData
import app.chompass.services.ai.RecalcSheetSource
import app.chompass.ui.components.ChompassBottomSheet
import app.chompass.ui.components.blockSheetDragAtScrollEdges
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.AppTextOpacity

/**
 * Transparency sheet shown after Recalculate Goals: the new targets, who
 * decided (provider/model badge + fallback note), how it decided (tier line),
 * the formula baseline (BMR → TDEE × activity → goal pace → formula target),
 * the data used (weigh-ins, logged intake, implied maintenance or why it was
 * withheld, measured Health Connect burn), previous → new with delta, and the
 * model's full reason (no 100-char squeeze).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecalcResultSheet(
    data: RecalcSheetData,
    onDismiss: () -> Unit,
) {
    val result = data.result
    val report = result.report
    val before = data.before
    val after = data.after
    val scroll = rememberScrollState()
    ChompassBottomSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .blockSheetDragAtScrollEdges(scroll)
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                if (data.source == RecalcSheetSource.ADAPTIVE) {
                    stringResource(R.string.settings_adaptive_goals)
                } else {
                    stringResource(R.string.vm_goals_recalculated)
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            // 1. New targets
            SheetSectionHeader(stringResource(R.string.recalc_sheet_new_targets))
            Text(
                stringResource(R.string.kcal_value_format, LocaleFormat.integer(result.calories)),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = AppColors.Calorie,
            )
            Text(
                stringResource(
                    R.string.recalc_sheet_macros_format,
                    result.protein, result.carbs, result.fat,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
            )
            HorizontalDivider()
            // 2. Who decided
            SheetSectionHeader(stringResource(R.string.recalc_sheet_who_decided))
            val provider = result.provider
            val providerLine = if (provider != null) {
                val model = result.model.orEmpty()
                if (model.isBlank()) stringResource(provider.displayNameRes)
                else stringResource(provider.displayNameRes) + " · " + model
            } else {
                stringResource(R.string.recalc_sheet_tier_formula)
            }
            SheetInfoRow(providerLine)
            if (result.fallbackFired && provider != null) {
                SheetInfoRow(
                    stringResource(
                        R.string.recalc_sheet_fallback_note,
                        result.primaryProvider?.let { stringResource(it.displayNameRes) } ?: "",
                        stringResource(provider.displayNameRes),
                    ),
                    dim = true,
                )
            }
            HorizontalDivider()
            // 3. How it decided
            SheetSectionHeader(stringResource(R.string.recalc_sheet_how_decided))
            val weighIns = report?.weighIns ?: 0
            val spanDays = report?.weightSpanDays ?: 0
            val tierLine = when {
                // Adaptive/deterministic entries (tier null) always show the formula line.
                result.tier != null && report?.measuredTdee != null && result.tier != GoalRecalcTier.SMART ->
                    stringResource(R.string.recalc_sheet_tier_measured)
                result.tier == GoalRecalcTier.SMART && weighIns > 0 ->
                    stringResource(R.string.recalc_sheet_tier_smart, weighIns, spanDays)
                result.tier == GoalRecalcTier.SAFE && weighIns > 0 ->
                    stringResource(R.string.recalc_sheet_tier_safe, weighIns, spanDays)
                else -> stringResource(R.string.recalc_sheet_tier_formula)
            }
            SheetInfoRow(tierLine)
            HorizontalDivider()
            // 4. Formula baseline (checkable chain)
            if (report != null) {
                SheetSectionHeader(stringResource(R.string.recalc_sheet_formula_baseline))
                SheetInfoRow(stringResource(R.string.recalc_sheet_bmr_row, LocaleFormat.integer(report.bmr)))
                SheetInfoRow(
                    stringResource(
                        R.string.recalc_sheet_tdee_row,
                        formatMultiplier(report.activityMultiplier),
                        LocaleFormat.integer(report.tdee),
                    ),
                )
                SheetInfoRow(stringResource(R.string.recalc_sheet_pace_row, signed(report.calorieAdjustment)))
                SheetInfoRow(stringResource(R.string.recalc_sheet_formula_target_row, LocaleFormat.integer(report.formulaCalories)))
                report.measuredTdee?.let {
                    SheetInfoRow(stringResource(R.string.recalc_sheet_measured_row, LocaleFormat.integer(it)))
                }
                HorizontalDivider()
            }
            // 5. Data used
            if (report != null) {
                SheetSectionHeader(stringResource(R.string.recalc_sheet_data_used))
                if (report.weighIns > 0) {
                    SheetInfoRow(stringResource(R.string.recalc_sheet_data_weighins, report.weighIns, report.weightSpanDays))
                } else {
                    SheetInfoRow(stringResource(R.string.recalc_sheet_data_no_weighins))
                }
                val loggedAvg = report.loggedDayAvgCalories
                if (report.foodDays > 0 && loggedAvg != null) {
                    SheetInfoRow(stringResource(R.string.recalc_sheet_data_intake, LocaleFormat.integer(loggedAvg), report.foodDays))
                } else {
                    SheetInfoRow(stringResource(R.string.recalc_sheet_data_no_intake))
                }
                when (report.impliedWithheld) {
                    ImpliedWithheldReason.THIN ->
                        SheetInfoRow(stringResource(R.string.recalc_sheet_data_implied_thin))
                    ImpliedWithheldReason.BELOW_FLOOR ->
                        SheetInfoRow(stringResource(R.string.recalc_sheet_data_implied_below_floor))
                    ImpliedWithheldReason.DISAGREE ->
                        SheetInfoRow(stringResource(R.string.recalc_sheet_data_implied_disagree))
                    null -> report.impliedMaintenance?.let {
                        SheetInfoRow(stringResource(R.string.recalc_sheet_data_implied, LocaleFormat.integer(it)))
                    }
                }
                HorizontalDivider()
            }
            // 6. Before → after
            SheetSectionHeader(stringResource(R.string.recalc_sheet_change))
            SheetInfoRow(stringResource(R.string.recalc_sheet_previous, LocaleFormat.integer(before.effectiveCalories)))
            val delta = after.effectiveCalories - before.effectiveCalories
            SheetInfoRow(
                stringResource(
                    R.string.recalc_sheet_new_with_delta,
                    LocaleFormat.integer(after.effectiveCalories),
                    signed(delta),
                ),
            )
            // 7. Model's full reason
            result.reason?.let { reason ->
                HorizontalDivider()
                SheetSectionHeader(stringResource(R.string.recalc_sheet_reason))
                Text(
                    reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                )
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            ) {
                Text(stringResource(R.string.action_done))
            }
        }
    }
}

@Composable
private fun SheetSectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SheetInfoRow(text: String, dim: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(
            alpha = if (dim) AppTextOpacity.Faint else AppTextOpacity.Muted,
        ),
    )
}

/** "1.55" / "1.375" — matches the prompt's multiplier formatting (no trailing zeros). */
private fun formatMultiplier(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

/** Signed integer for deltas: "+120" / "-120" / "0". */
private fun signed(value: Int): String =
    if (value > 0) "+" + LocaleFormat.integer(value) else LocaleFormat.integer(value)
