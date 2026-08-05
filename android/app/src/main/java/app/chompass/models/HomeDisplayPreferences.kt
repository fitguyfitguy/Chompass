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
 * The home hero's energy-balance shades: live active burn vs the day's active
 * norm (measured Health Connect 14-day average, else PAL estimate). Draw-only —
 * never feeds budget math. See [HomeCalorieDisplay.burnShadeArcEnd].
 */
data class ActiveBurnShade(
    val live: Int,
    val typical: Int,
    val source: ActiveCalorieSource,
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

    // ── Burn shade geometry (drawing only; never budget math) ─────────────

    /**
     * Visual arc end for the hero's burn shades: the projected daily total burn,
     * sedentary base + the day's active norm. In ADD_ACTIVE with measured data
     * this equals the measured TDEE (basal + 14-day active average); with the
     * PAL estimate it equals the profile TDEE. The arc is fixed by the norm so
     * live burn can grow past it toward the full ring on high-activity days.
     */
    fun burnShadeArcEnd(baseGoal: Int, typical: Int): Int =
        (baseGoal + typical.coerceAtLeast(0)).coerceAtLeast(0)

    /** Eaten fill fraction on the shade arc (single energy scale, 0..1). */
    fun burnShadeEatenFraction(eaten: Int, baseGoal: Int, typical: Int): Float {
        val end = burnShadeArcEnd(baseGoal, typical)
        if (end <= 0) return 0f
        return (eaten.coerceAtLeast(0).toFloat() / end).coerceIn(0f, 1f)
    }

    /** Fraction of the shade arc at which the sedentary base ends (active zone starts). */
    fun burnShadeBaseFraction(baseGoal: Int, typical: Int): Float {
        val end = burnShadeArcEnd(baseGoal, typical)
        if (end <= 0) return 0f
        return (baseGoal.toFloat() / end).coerceIn(0f, 1f)
    }

    /** Fraction of the shade arc covered by the typical active zone. */
    fun burnShadeTypicalFraction(baseGoal: Int, typical: Int): Float {
        val end = burnShadeArcEnd(baseGoal, typical)
        if (end <= 0) return 0f
        return (typical.coerceAtLeast(0).toFloat() / end).coerceIn(0f, 1f)
    }

    /** Fraction of the shade arc covered by live active burn (extends past the
     *  typical zone toward the full ring when over-typical). */
    fun burnShadeLiveFraction(baseGoal: Int, live: Int, typical: Int): Float {
        val end = burnShadeArcEnd(baseGoal, typical)
        if (end <= 0) return 0f
        return (live.coerceAtLeast(0).toFloat() / end).coerceIn(0f, 1f)
    }

    /** Fraction of the shade arc covered by resting (basal) burn so far. */
    fun burnShadeRestingFraction(restingKcal: Int, baseGoal: Int, typical: Int): Float {
        val end = burnShadeArcEnd(baseGoal, typical)
        if (end <= 0) return 0f
        return (restingKcal.coerceAtLeast(0).toFloat() / end).coerceIn(0f, 1f)
    }

    /** How far live active burn is through a typical day's active burn (0..1). */
    fun activeBurnShadeProgress(live: Int, typical: Int): Float =
        if (typical > 0) (live.coerceAtLeast(0).toFloat() / typical).coerceIn(0f, 1f) else 0f

    /** True when live active burn exceeds a typical day's active burn. */
    fun isActiveBurnOverTypical(live: Int, typical: Int): Boolean = typical > 0 && live > typical
}
