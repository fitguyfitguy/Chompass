package app.chompass.ui.home

import app.chompass.ui.components.rememberChompassSheetState
import app.chompass.ui.components.ChompassPinnedFooterSheet
import app.chompass.ui.components.kcalText
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.chompass.R
import app.chompass.models.FoodEntry
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.AppTextOpacity
import kotlin.math.roundToInt

/**
 * Confirmation shown when the user opens a `fudai://add-meal` link (issue #107). Lists the shared
 * meal(s) and adds them to the log on confirm — never silently, so a stray link can't add food
 * without the user seeing it first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportSharedMealSheet(
    meals: List<FoodEntry>,
    onAdd: (List<FoodEntry>) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberChompassSheetState()
    val totalCalories = meals.sumOf { it.calories }

    // Pinned-footer layout (Codeberg #84 class): a shared payload with many
    // meals used to grow the sheet past the screen, pushing Add-to-log out of
    // reach. The meal rows scroll; the CTA is measured first and stays pinned.
    ChompassPinnedFooterSheet(
        onDismiss = onDismiss,
        sheetState = state,
        body = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    pluralStringResource(R.plurals.import_add_meals_title, meals.size, meals.size),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )

                meals.forEach { meal ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        meal.emoji?.let {
                            Text(it, fontSize = 20.sp, modifier = Modifier.padding(end = 10.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(meal.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${meal.protein.roundToInt()}P · ${meal.carbs.roundToInt()}C · ${meal.fat.roundToInt()}F",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                            )
                        }
                        Text(
                            kcalText(meal.calories),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.Calorie,
                        )
                    }
                }

                Text(
                    stringResource(R.string.import_note),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted),
                )
            }
        },
        footer = {
            Column(
                Modifier
                    .fillMaxWidth()
                    // Pinned footer pads itself (no imePadding — no text inputs).
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppColors.CalorieGradient)
                        .clickable { onAdd(meals) }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (meals.size == 1) stringResource(R.string.import_add_to_log) else stringResource(R.string.import_add_to_log_many_format, meals.size, totalCalories),
                        color = AppColors.onCalorieGradient,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                }
            }
        },
    )
}
