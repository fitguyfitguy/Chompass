package org.codeberg.fitguy.nofud.models

import org.codeberg.fitguy.nofud.R
import org.codeberg.fitguy.nofud.ui.theme.MacroKind

/** How the home calorie gauge interprets Health Connect active burn for the selected day. */
enum class HomeCalorieDisplayMode(val storageKey: String, val displayNameRes: Int) {
    STATIC("static", R.string.home_calorie_mode_static),
    ADD_ACTIVE("addActive", R.string.home_calorie_mode_add_active),
    NET("net", R.string.home_calorie_mode_net),
    DUAL("dual", R.string.home_calorie_mode_dual);

    companion object {
        val Default = STATIC

        fun fromStorage(raw: String?): HomeCalorieDisplayMode =
            entries.firstOrNull { it.storageKey == raw } ?: Default
    }
}

/** Macros shown on food-log rows (subset of core nutrients). */
enum class FoodLogMacroChip(val storageKey: String, val glyph: String) {
    PROTEIN("protein", "P"),
    CARBS("carbs", "C"),
    FAT("fat", "F"),
    FIBER("fiber", "Fi"),
    SUGAR("sugar", "S");

    fun macroKind(): MacroKind? = when (this) {
        PROTEIN -> MacroKind.PROTEIN
        CARBS -> MacroKind.CARBS
        FAT -> MacroKind.FAT
        FIBER -> MacroKind.FIBER
        SUGAR -> null
    }

    fun valueFrom(entry: FoodEntry): Double = when (this) {
        PROTEIN -> entry.protein
        CARBS -> entry.carbs
        FAT -> entry.fat
        FIBER -> entry.fiber ?: 0.0
        SUGAR -> entry.sugar ?: 0.0
    }

    companion object {
        val DefaultSelection = listOf(PROTEIN, CARBS, FAT)
        val DefaultStorageValue = DefaultSelection.joinToString(",") { it.storageKey }

        fun fromStorage(raw: String?): List<FoodLogMacroChip> {
            val selected = raw
                ?.split(",")
                ?.mapNotNull { part ->
                    val key = part.trim()
                    entries.firstOrNull { it.storageKey == key || it.name == key }
                }
                .orEmpty()
            return normalized(selected)
        }

        fun toStorage(selection: List<FoodLogMacroChip>): String =
            normalized(selection).joinToString(",") { it.storageKey }

        fun normalized(selection: List<FoodLogMacroChip>): List<FoodLogMacroChip> =
            (selection.distinct().ifEmpty { DefaultSelection } + DefaultSelection)
                .distinct()
                .take(5)
    }
}

data class HomeDisplayPreferences(
    val nutrientCardCount: Int = DEFAULT_NUTRIENT_CARD_COUNT,
    val homeTopNutrients: List<HomeTopNutrient> = HomeTopNutrient.DefaultSelection,
    val showSteps: Boolean = false,
    val showActiveCalories: Boolean = false,
    val stepGoal: Int = DEFAULT_STEP_GOAL,
    val calorieDisplayMode: HomeCalorieDisplayMode = HomeCalorieDisplayMode.Default,
    val foodLogMacroChips: List<FoodLogMacroChip> = FoodLogMacroChip.DefaultSelection,
) {
    companion object {
        const val MIN_NUTRIENT_CARD_COUNT = 1
        const val MAX_NUTRIENT_CARD_COUNT = 4
        const val DEFAULT_NUTRIENT_CARD_COUNT = 4
        const val DEFAULT_STEP_GOAL = 10_000
        const val MIN_STEP_GOAL = 1_000
        const val MAX_STEP_GOAL = 50_000
    }
}

/** Pure calorie-gauge math for home + widgets. */
object HomeCalorieDisplay {
    fun effectiveMode(requested: HomeCalorieDisplayMode, energyAvailable: Boolean): HomeCalorieDisplayMode =
        if (!energyAvailable && requested != HomeCalorieDisplayMode.STATIC) {
            HomeCalorieDisplayMode.STATIC
        } else {
            requested
        }

    fun effectiveGoal(mode: HomeCalorieDisplayMode, baseGoal: Int, activeCalories: Int): Int = when (mode) {
        HomeCalorieDisplayMode.ADD_ACTIVE -> baseGoal + activeCalories.coerceAtLeast(0)
        HomeCalorieDisplayMode.STATIC,
        HomeCalorieDisplayMode.NET,
        HomeCalorieDisplayMode.DUAL -> baseGoal
    }

    fun progressRatio(
        mode: HomeCalorieDisplayMode,
        eaten: Int,
        baseGoal: Int,
        activeCalories: Int,
    ): Float {
        if (baseGoal <= 0) return 0f
        val numerator = when (mode) {
            HomeCalorieDisplayMode.NET -> (eaten - activeCalories).coerceAtLeast(0)
            else -> eaten
        }
        val denominator = when (mode) {
            HomeCalorieDisplayMode.ADD_ACTIVE -> effectiveGoal(mode, baseGoal, activeCalories)
            else -> baseGoal
        }
        if (denominator <= 0) return 0f
        return (numerator.toFloat() / denominator).coerceIn(0f, 1f)
    }

    fun remaining(
        mode: HomeCalorieDisplayMode,
        eaten: Int,
        baseGoal: Int,
        activeCalories: Int,
    ): Int = when (mode) {
        HomeCalorieDisplayMode.ADD_ACTIVE ->
            (effectiveGoal(mode, baseGoal, activeCalories) - eaten).coerceAtLeast(0)
        HomeCalorieDisplayMode.NET ->
            (baseGoal - (eaten - activeCalories)).coerceAtLeast(0)
        HomeCalorieDisplayMode.STATIC,
        HomeCalorieDisplayMode.DUAL ->
            (baseGoal - eaten).coerceAtLeast(0)
    }

    fun netCalories(eaten: Int, activeCalories: Int): Int = eaten - activeCalories
}
