package app.chompass.models

import app.chompass.R
import app.chompass.services.health.HomeActivitySnapshot
import app.chompass.ui.theme.MacroKind

/** How the home calorie gauge interprets Health Connect active burn for the selected day. */
enum class HomeCalorieDisplayMode(val storageKey: String, val displayNameRes: Int) {
    STATIC("static", R.string.home_calorie_mode_static),
    ADD_ACTIVE("addActive", R.string.home_calorie_mode_add_active);

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
enum class ActiveCalorieSource(val storageKey: String) {
    UNAVAILABLE("unavailable"),
    ESTIMATED("estimated"),
    MEASURED("measured"),
    MANUAL("manual");

    companion object {
        fun fromStorage(raw: String?): ActiveCalorieSource =
            entries.firstOrNull { it.storageKey == raw } ?: UNAVAILABLE
    }
}

data class ResolvedActiveBurn(
    val calories: Int,
    val source: ActiveCalorieSource,
)

/**
 * Today's active burn vs a single "typical" reference, for the home hero's
 * inner arc. [typical] is the Health Connect 14-day average when available,
 * otherwise the PAL activity-level estimate; [typicalSource] says which.
 */
data class ActiveBurnArcData(
    val live: Int,
    val typical: Int,
    val typicalSource: ActiveCalorieSource,
)

object HomeCalorieDisplay {
    fun resolveActiveBurn(
        mode: HomeCalorieDisplayMode,
        snapshot: HomeActivitySnapshot,
        estimatedDailyActive: Int,
        manualActiveCalories: Int = 0,
    ): ResolvedActiveBurn? {
        val measured = if (snapshot.energyAvailable) {
            ResolvedActiveBurn(snapshot.activeCalories, ActiveCalorieSource.MEASURED)
        } else {
            null
        }
        val core = when (mode) {
            HomeCalorieDisplayMode.STATIC -> null
            HomeCalorieDisplayMode.ADD_ACTIVE -> measured ?: estimatedDailyActive
                .takeIf { it > 0 }
                ?.let { ResolvedActiveBurn(it, ActiveCalorieSource.ESTIMATED) }
        }
        val manual = manualActiveCalories.coerceAtLeast(0)
        if (mode == HomeCalorieDisplayMode.STATIC) return null
        val total = (core?.calories ?: 0) + manual
        if (total <= 0) return null
        val source = when {
            core?.source == ActiveCalorieSource.MEASURED -> ActiveCalorieSource.MEASURED
            core != null -> core.source
            else -> ActiveCalorieSource.MANUAL
        }
        return ResolvedActiveBurn(total, source)
    }

    fun effectiveMode(requested: HomeCalorieDisplayMode, burn: ResolvedActiveBurn?): HomeCalorieDisplayMode =
        when (requested) {
            HomeCalorieDisplayMode.STATIC -> HomeCalorieDisplayMode.STATIC
            HomeCalorieDisplayMode.ADD_ACTIVE ->
                if (burn != null && burn.calories > 0) requested else HomeCalorieDisplayMode.STATIC
        }

    fun gaugeBaseGoal(
        mode: HomeCalorieDisplayMode,
        effectiveCalories: Int,
        sedentaryBudget: Int,
    ): Int = when (mode) {
        HomeCalorieDisplayMode.ADD_ACTIVE -> sedentaryBudget
        HomeCalorieDisplayMode.STATIC -> effectiveCalories
    }

    fun effectiveGoal(mode: HomeCalorieDisplayMode, baseGoal: Int, activeCalories: Int): Int = when (mode) {
        HomeCalorieDisplayMode.ADD_ACTIVE -> baseGoal + activeCalories.coerceAtLeast(0)
        HomeCalorieDisplayMode.STATIC -> baseGoal
    }

    fun progressRatio(
        mode: HomeCalorieDisplayMode,
        eaten: Int,
        baseGoal: Int,
        activeCalories: Int,
    ): Float {
        if (baseGoal <= 0) return 0f
        val denominator = when (mode) {
            HomeCalorieDisplayMode.ADD_ACTIVE -> effectiveGoal(mode, baseGoal, activeCalories)
            HomeCalorieDisplayMode.STATIC -> baseGoal
        }
        if (denominator <= 0) return 0f
        return (eaten.toFloat() / denominator).coerceIn(0f, 1f)
    }

    fun remaining(
        mode: HomeCalorieDisplayMode,
        eaten: Int,
        baseGoal: Int,
        activeCalories: Int,
    ): Int = when (mode) {
        HomeCalorieDisplayMode.ADD_ACTIVE ->
            (effectiveGoal(mode, baseGoal, activeCalories) - eaten).coerceAtLeast(0)
        HomeCalorieDisplayMode.STATIC ->
            (baseGoal - eaten).coerceAtLeast(0)
    }

    /**
     * Data for the hero's inner active-burn arc. Unlike [resolveActiveBurn],
     * live and reference stay strictly separate: the estimate never stands in
     * for live burn. [liveHcActive] is today's measured Health Connect burn;
     * [healthConnectAverage] is the stored 14-day measured average. Returns
     * null when there is no live burn to draw.
     */
    fun activeBurnArc(
        liveHcActive: Int,
        manualActiveCalories: Int,
        healthConnectAverage: Int,
        estimatedDailyActive: Int,
    ): ActiveBurnArcData? {
        val live = liveHcActive.coerceAtLeast(0) + manualActiveCalories.coerceAtLeast(0)
        if (live <= 0) return null
        val typical = healthConnectAverage.takeIf { it > 0 } ?: estimatedDailyActive.takeIf { it > 0 } ?: 0
        val source = when {
            healthConnectAverage > 0 -> ActiveCalorieSource.MEASURED
            typical > 0 -> ActiveCalorieSource.ESTIMATED
            else -> ActiveCalorieSource.MANUAL
        }
        return ActiveBurnArcData(live, typical, source)
    }

    /**
     * How much of a typical day's active burn has been reached, clamped to
     * [0, 1]. Drives the hero's burn-thermometer alpha ramp. Returns 1 when
     * there is no reference (nothing to measure against).
     */
    fun activeBurnRampProgress(live: Int, typical: Int): Float {
        if (typical <= 0) return 1f
        return (live.toFloat() / typical).coerceIn(0f, 1f)
    }
}
