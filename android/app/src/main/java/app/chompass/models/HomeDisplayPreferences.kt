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

/** Presentation options for the home calorie hero. Null preserves the legacy tall arc. */
enum class HomeCalorieHeroLayout(val storageKey: String) {
    COMPACT_HORIZONTAL("compactHorizontal"),
    SIDE_GAUGE("sideGauge"),
    COMPACT_ARC("compactArc"),
    HIDDEN("hidden");

    companion object {
        fun fromStorage(raw: String?): HomeCalorieHeroLayout? =
            entries.firstOrNull { it.storageKey == raw }
    }
}

/** Presentation options for home nutrient cards. Null = compact bars band (default). */
enum class HomeMacroCardLayout(val storageKey: String) {
    SLIM_LINE("slimLine"),
    CLASSIC("classic");

    companion object {
        fun fromStorage(raw: String?): HomeMacroCardLayout? =
            entries.firstOrNull { it.storageKey == raw }
    }
}

/** Presentation options for food-log rows. Null = compact rows (default). */
enum class HomeFoodLogLayout(val storageKey: String) {
    CLASSIC("classic");

    companion object {
        fun fromStorage(raw: String?): HomeFoodLogLayout? =
            entries.firstOrNull { it.storageKey == raw }
    }
}

/** Macros shown on food-log rows (subset of core nutrients). */
enum class FoodLogMacroChip(val storageKey: String) {
    PROTEIN("protein"),
    CARBS("carbs"),
    FAT("fat"),
    FIBER("fiber"),
    SUGAR("sugar");

    /** Chip letter before the gram value — per-locale (#111): pt-BR fat = "G" (Gordura). */
    fun glyphRes(): Int = when (this) {
        PROTEIN -> R.string.macro_glyph_protein
        CARBS -> R.string.macro_glyph_carbs
        FAT -> R.string.macro_glyph_fat
        FIBER -> R.string.macro_glyph_fiber
        SUGAR -> R.string.macro_glyph_sugar
    }

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

    /** Value of this chip against a meal group's combined totals. */
    fun valueFromMeal(
        totalProtein: Double,
        totalCarbs: Double,
        totalFat: Double,
        totalFiber: Double,
        totalSugar: Double,
    ): Double = when (this) {
        PROTEIN -> totalProtein
        CARBS -> totalCarbs
        FAT -> totalFat
        FIBER -> totalFiber
        SUGAR -> totalSugar
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
    /** Add Active only: credit today's measured burn on top of the full goal instead of the sedentary split. */
    val fullCreditActiveBurn: Boolean = false,
    val calorieHeroLayout: HomeCalorieHeroLayout? = null,
    val macroCardLayout: HomeMacroCardLayout? = null,
    val foodLogLayout: HomeFoodLogLayout? = null,
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
    val typicalIsDayType: Boolean = false,
    val typicalDayTypeName: String? = null,
    val blendedTypical: Int = 0,
)

/** The gauge's single budget decision — see [HomeCalorieDisplay.resolveGaugeBudget]. */
data class GaugeBudget(
    /** Goal the ring is anchored on (full goal, or the sedentary split). */
    val base: Int,
    /** Active burn credited on top of [base]. */
    val creditedBurn: Int,
    /** Number the hero's goal / ring end shows. */
    val displayTarget: Int,
    /** True when the ring draws the expected-day arc (ADD_ACTIVE hold-back with a live source). */
    val useExpectedArc: Boolean,
)

object HomeCalorieDisplay {
    /**
     * Resolve today's active burn for the gauge. Measured Health Connect (or
     * debug) active wins even at 0: once a live measured-energy source exists,
     * a morning with no wearable data yet shows the sedentary budget plus 0
     * instead of substituting the whole estimated day. The PAL estimate is
     * used only when no live measured source exists at all. STATIC mode counts
     * only manual burns (returned as [ActiveCalorieSource.MANUAL]); automatic
     * sources never leak into it.
     */
    fun resolveActiveBurn(
        mode: HomeCalorieDisplayMode,
        snapshot: HomeActivitySnapshot,
        estimatedDailyActive: Int,
        manualActiveCalories: Int = 0,
    ): ResolvedActiveBurn? {
        val measured = if (snapshot.energyLive) {
            ResolvedActiveBurn(snapshot.activeCalories.coerceAtLeast(0), ActiveCalorieSource.MEASURED)
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
        if (mode == HomeCalorieDisplayMode.STATIC) {
            // Manual burns are deliberate "eat back" logs: they raise that
            // day's goal even in STATIC mode, which only excludes automatic
            // (Health Connect / PAL estimate) calories.
            if (manual <= 0) return null
            return ResolvedActiveBurn(manual, ActiveCalorieSource.MANUAL)
        }
        val total = (core?.calories ?: 0) + manual
        // A measured 0 is a valid "no activity yet" burn: keep ADD_ACTIVE so
        // the gauge stays on the sedentary budget instead of jumping to the
        // full goal at the first measurement.
        if (total <= 0 && core?.source != ActiveCalorieSource.MEASURED) return null
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
                if (burn != null) requested else HomeCalorieDisplayMode.STATIC
        }

    fun gaugeBaseGoal(
        mode: HomeCalorieDisplayMode,
        effectiveCalories: Int,
        sedentaryBudget: Int,
    ): Int = when (mode) {
        HomeCalorieDisplayMode.ADD_ACTIVE -> sedentaryBudget
        HomeCalorieDisplayMode.STATIC -> effectiveCalories
    }

    /** Base goal plus active calories. In STATIC mode callers pass manual-only
     *  burn ([resolveActiveBurn] returns manual-only there), so a logged burn
     *  raises that day's goal without turning on automatic estimates. */
    fun effectiveGoal(mode: HomeCalorieDisplayMode, baseGoal: Int, activeCalories: Int): Int =
        baseGoal + activeCalories.coerceAtLeast(0)

    /**
     * One budget decision for the home gauge and the widget snapshot, so the
     * hero and the widget can never disagree. [fullCredit] only means anything
     * in ADD_ACTIVE; in STATIC it is ignored (manual burns stay the only
     * credited burn).
     *
     * Hold-back (default, fullCredit == false): the ADD_ACTIVE split — the
     * stored goal minus a typical day's active burn becomes the sedentary
     * base, today's burn is layered on top, and with a live measured source
     * the ring projects the expected day ([expectedTarget]).
     *
     * Full credit: the base is the full stored goal (no split, no 14-day
     * average, no PAL estimate) and only today's measured burn plus manual
     * burns are added. A morning with measured 0 keeps the full goal; a big
     * workout is not eaten back twice through the average.
     */
    fun resolveGaugeBudget(
        mode: HomeCalorieDisplayMode,
        fullCredit: Boolean,
        fullGoal: Int,
        sedentaryBudget: Int,
        energyLive: Boolean,
        measuredToday: Int,
        estimatedDailyActive: Int,
        manual: Int,
        typical: Int,
    ): GaugeBudget {
        val goal = fullGoal.coerceAtLeast(0)
        val sedentary = sedentaryBudget.coerceAtLeast(0)
        val manualKcal = manual.coerceAtLeast(0)
        if (mode != HomeCalorieDisplayMode.ADD_ACTIVE) {
            // STATIC: full goal + deliberate manual eat-backs only.
            return GaugeBudget(
                base = goal,
                creditedBurn = manualKcal,
                displayTarget = goal + manualKcal,
                useExpectedArc = false,
            )
        }
        val measured = measuredToday.coerceAtLeast(0)
        if (fullCredit) {
            val credited = (if (energyLive) measured else 0) + manualKcal
            return GaugeBudget(
                base = goal,
                creditedBurn = credited,
                displayTarget = goal + credited,
                useExpectedArc = false,
            )
        }
        val credited = (if (energyLive) {
            measured
        } else {
            estimatedDailyActive.takeIf { it > 0 } ?: 0
        }) + manualKcal
        val useExpectedArc = energyLive && typical > 0
        return GaugeBudget(
            base = sedentary,
            creditedBurn = credited,
            displayTarget = if (useExpectedArc) {
                expectedTarget(sedentary, typical, measured + manualKcal)
            } else {
                effectiveGoal(HomeCalorieDisplayMode.ADD_ACTIVE, sedentary, credited)
            },
            useExpectedArc = useExpectedArc,
        )
    }

    fun progressRatio(
        mode: HomeCalorieDisplayMode,
        eaten: Int,
        baseGoal: Int,
        activeCalories: Int,
    ): Float {
        val denominator = effectiveGoal(mode, baseGoal, activeCalories)
        if (denominator <= 0) return 0f
        return (eaten.toFloat() / denominator).coerceIn(0f, 1f)
    }

    fun remaining(
        mode: HomeCalorieDisplayMode,
        eaten: Int,
        baseGoal: Int,
        activeCalories: Int,
    ): Int = (effectiveGoal(mode, baseGoal, activeCalories) - eaten).coerceAtLeast(0)

    // ── Burn shade geometry (drawing only; never budget math) ─────────────

    /**
     * Display-only expected-day target for ADD_ACTIVE: the sedentary base plus
     * the larger of the day's active norm (typical) and the live burn. The hero
     * arc end, goal line, and remaining all read against this, so a morning
     * with no measured burn yet shows the projected day (base + typical) and
     * the target grows only when live burn exceeds typical. The budget
     * (base + live) is unchanged; in STATIC the target is the base goal.
     */
    fun expectedTarget(baseGoal: Int, typical: Int, live: Int): Int =
        (baseGoal + maxOf(typical, live).coerceAtLeast(0)).coerceAtLeast(0)

    /**
     * Visual arc end for the hero's burn shades: the expected-day target
     * [expectedTarget]. With measured data the typical is the 14-day active
     * average (basal + average = measured TDEE); with the PAL estimate it is
     * the profile TDEE. The arc end grows with live burn once it exceeds the
     * norm, so the live tip always lands at the ring end on high-activity days.
     */
    fun burnShadeArcEnd(baseGoal: Int, typical: Int, live: Int = 0): Int =
        expectedTarget(baseGoal, typical, live)

    /** Eaten fill fraction on the shade arc (single energy scale, 0..1). */
    fun burnShadeEatenFraction(eaten: Int, baseGoal: Int, typical: Int, live: Int = 0): Float {
        val end = burnShadeArcEnd(baseGoal, typical, live)
        if (end <= 0) return 0f
        return (eaten.coerceAtLeast(0).toFloat() / end).coerceIn(0f, 1f)
    }

    /** Fraction of the shade arc at which the sedentary base ends (active zone starts). */
    fun burnShadeBaseFraction(baseGoal: Int, typical: Int, live: Int = 0): Float {
        val end = burnShadeArcEnd(baseGoal, typical, live)
        if (end <= 0) return 0f
        return (baseGoal.toFloat() / end).coerceIn(0f, 1f)
    }

    /** Fraction of the shade arc covered by the typical active zone. */
    fun burnShadeTypicalFraction(baseGoal: Int, typical: Int, live: Int = 0): Float {
        val end = burnShadeArcEnd(baseGoal, typical, live)
        if (end <= 0) return 0f
        return (typical.coerceAtLeast(0).toFloat() / end).coerceIn(0f, 1f)
    }

    /** Fraction of the shade arc covered by live active burn: the tip lands at
     *  the ring end exactly when live burn reaches the (grown) arc end. */
    fun burnShadeLiveFraction(baseGoal: Int, live: Int, typical: Int): Float {
        val end = burnShadeArcEnd(baseGoal, typical, live)
        if (end <= 0) return 0f
        return (live.coerceAtLeast(0).toFloat() / end).coerceIn(0f, 1f)
    }

    /** How far live active burn is through a typical day's active burn (0..1). */
    fun activeBurnShadeProgress(live: Int, typical: Int): Float =
        if (typical > 0) (live.coerceAtLeast(0).toFloat() / typical).coerceIn(0f, 1f) else 0f

    /** True when live active burn exceeds a typical day's active burn. */
    fun isActiveBurnOverTypical(live: Int, typical: Int): Boolean = typical > 0 && live > typical
}
