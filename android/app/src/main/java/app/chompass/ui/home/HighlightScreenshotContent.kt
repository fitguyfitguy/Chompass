package app.chompass.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.R
import app.chompass.models.FoodConstituent
import app.chompass.models.Recipe
import app.chompass.ui.components.MacroChip
import app.chompass.ui.components.isDarkTheme
import app.chompass.ui.components.kcalText
import app.chompass.ui.theme.AppRadii
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.MacroKind

/**
 * Home with a static meal-review sheet showing editable constituents
 * (release / marketing screenshots; no ModalBottomSheet).
 */
@Composable
internal fun HomeMealComponentsScreenshotContent(
    ui: HomeUiState,
    constituents: List<FoodConstituent>,
    mealName: String,
    mealCalories: Int,
    mealEmoji: String,
    weekStartsOnMonday: Boolean = true,
) {
    Box(Modifier.fillMaxSize()) {
        HomeScreenPreviewContent(ui = ui, weekStartsOnMonday = weekStartsOnMonday)
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxSize(0.72f),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "$mealEmoji  $mealName",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    kcalText(mealCalories),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Calorie,
                )
                ConstituentsSection(
                    rows = constituents,
                    expanded = true,
                    onExpandedChange = {},
                    onRowsChange = {},
                )
            }
        }
    }
}

/**
 * Home with a static Saved Meals → Recipes sheet for marketing screenshots.
 */
@Composable
internal fun HomeRecipesScreenshotContent(
    ui: HomeUiState,
    recipes: List<Recipe>,
    weekStartsOnMonday: Boolean = true,
) {
    val isDark = isDarkTheme()
    val rowFill = if (isDark) AppColors.TranslucentSurfaceDark else AppColors.TranslucentSurfaceLight
    val rowBorder = if (isDark) AppColors.HairlineBorderDark else AppColors.HairlineBorderLight
    Box(Modifier.fillMaxSize()) {
        HomeScreenPreviewContent(ui = ui, weekStartsOnMonday = weekStartsOnMonday)
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxSize(0.78f),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 16.dp),
            ) {
                Text(
                    stringResource(R.string.saved_meals_tab_recipes),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    textAlign = TextAlign.Center,
                )
                ScreenshotRecipesTabStrip()
                Spacer(Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    recipes.forEach { recipe ->
                        ScreenshotRecipeRow(
                            recipe = recipe,
                            rowFill = rowFill,
                            rowBorder = rowBorder,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenshotRecipesTabStrip() {
    val tabs = listOf(
        R.string.saved_meals_tab_recents,
        R.string.saved_meals_tab_frequent,
        R.string.saved_meals_tab_favorites,
        R.string.saved_meals_tab_recipes,
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEachIndexed { index, labelRes ->
            val selected = index == tabs.lastIndex
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (selected) AppColors.Calorie.copy(alpha = 0.22f)
                        else Color.Transparent
                    )
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(labelRes),
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) AppColors.Calorie
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ScreenshotRecipeRow(
    recipe: Recipe,
    rowFill: Color,
    rowBorder: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadii.Container))
            .background(rowFill)
            .border(0.5.dp, rowBorder, RoundedCornerShape(AppRadii.Container))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                .border(1.dp, AppColors.Calorie.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                recipe.emoji ?: recipe.ingredients.firstOrNull()?.emoji ?: "🍽",
                fontSize = 28.sp,
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(recipe.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 2)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    kcalText(recipe.totalCalories),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.Calorie,
                )
                Text("·", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                Text(
                    stringResource(R.string.recipe_ingredient_count_format, recipe.ingredients.size),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MacroChip(MacroKind.PROTEIN, recipe.totalProtein)
                MacroChip(MacroKind.CARBS, recipe.totalCarbs)
                MacroChip(MacroKind.FAT, recipe.totalFat)
            }
        }
        Icon(
            Icons.Filled.AddCircle,
            contentDescription = stringResource(R.string.cd_log),
            tint = AppColors.Calorie,
            modifier = Modifier.size(22.dp),
        )
    }
}
