package app.chompass.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.chompass.R
import app.chompass.models.GroundingCandidate
import app.chompass.models.NutrientSourceKind
import app.chompass.services.grounding.GroundedFoodEntryService
import app.chompass.ui.components.FudGlassPrimaryButton
import app.chompass.ui.theme.AppColors
import kotlin.math.roundToInt

/**
 * Intermediate sheet when grounded entry needs candidate picks and/or portion
 * confirmation before collapsing into [FoodResultSheet].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroundedCandidateSheet(
    review: PendingGroundedReview,
    onDismiss: () -> Unit,
    onConfirm: (selectedSourceIds: Map<Int, String>, gramOverrides: Map<Int, Double>) -> Unit,
    isSubmitting: Boolean = false,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val resolutions = review.result.resolutions
    val selectedIds = remember {
        mutableStateMapOf<Int, String>().apply {
            resolutions.forEachIndexed { index, resolution ->
                val auto = resolution.selected?.sourceId
                    ?: resolution.candidates.firstOrNull()?.sourceId
                if (auto != null) put(index, auto)
            }
        }
    }
    val gramTexts = remember {
        mutableStateMapOf<Int, String>().apply {
            resolutions.forEachIndexed { index, resolution ->
                val grams = resolution.component.estimatedGrams
                    ?: resolution.analysis?.servingSizeGrams
                    ?: resolution.selected?.servingSizeGrams
                if (grams != null && grams > 0) {
                    put(index, if (grams % 1.0 == 0.0) grams.toInt().toString() else grams.toString())
                }
            }
        }
    }
    var submitted by remember { mutableStateOf(false) }
    val busy = isSubmitting || submitted

    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                stringResource(R.string.grounded_review_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.grounded_review_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            resolutions.forEachIndexed { index, resolution ->
                ComponentReviewBlock(
                    index = index,
                    resolution = resolution,
                    selectedId = selectedIds[index],
                    gramText = gramTexts[index].orEmpty(),
                    onSelect = { if (!busy) selectedIds[index] = it },
                    onGramChange = { if (!busy) gramTexts[index] = it },
                )
                Spacer(Modifier.height(14.dp))
            }

            FudGlassPrimaryButton(
                text = stringResource(R.string.grounded_review_continue),
                onClick = {
                    if (busy) return@FudGlassPrimaryButton
                    submitted = true
                    val grams = gramTexts.mapNotNull { (idx, text) ->
                        text.toDoubleOrNull()?.takeIf { it > 0 }?.let { idx to it }
                    }.toMap()
                    onConfirm(selectedIds.toMap(), grams)
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ComponentReviewBlock(
    index: Int,
    resolution: GroundedFoodEntryService.ComponentResolution,
    selectedId: String?,
    gramText: String,
    onSelect: (String) -> Unit,
    onGramChange: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            resolution.component.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        resolution.question?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val candidates = resolution.candidates.ifEmpty {
            listOfNotNull(resolution.selected)
        }
        candidates.forEach { candidate ->
            CandidateRow(
                candidate = candidate,
                selected = candidate.sourceId == selectedId,
                onClick = { onSelect(candidate.sourceId) },
            )
        }

        OutlinedTextField(
            value = gramText,
            onValueChange = onGramChange,
            label = { Text(stringResource(R.string.grounded_review_grams)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CandidateRow(
    candidate: GroundingCandidate,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) AppColors.Calorie else MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            )
            .background(
                if (selected) AppColors.Calorie.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                candidate.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
            )
            Text(
                sourceBadgeLabel(candidate.sourceKind),
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.Calorie,
            )
            val cal = candidate.caloriesPer100g?.roundToInt()
            if (cal != null) {
                Text(
                    stringResource(R.string.grounded_review_per_100g, cal),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun sourceBadgeLabel(kind: NutrientSourceKind): String =
    when (kind) {
        NutrientSourceKind.USDA -> stringResource(R.string.grounding_source_usda)
        NutrientSourceKind.OPEN_FOOD_FACTS -> stringResource(R.string.grounding_source_off)
        NutrientSourceKind.HISTORY -> stringResource(R.string.grounding_source_history)
        NutrientSourceKind.NUTRITION_LABEL -> stringResource(R.string.grounding_source_label)
        NutrientSourceKind.MODEL_ESTIMATE -> stringResource(R.string.grounding_source_estimate)
    }
