package app.chompass.models

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * One user-selected Home nutrient carried in the widget snapshot. Mirrors the
 * iOS WidgetNutrientValue payload shape.
 */
@Serializable
data class WidgetNutrient(
    val id: String,
    val label: String,
    val unit: String,
    val value: Double,
    val goal: Double
) {
    val progress: Double get() = if (goal > 0) minOf(1.0, value / goal) else 0.0
}

/**
 * Small Codable snapshot of today's totals + goals that the widget reads out of DataStore.
 * The main app writes it on every FoodStore / profile change; the widget re-reads on every
 * timeline refresh and on explicit updateAll() calls.
 */
@Serializable
data class WidgetSnapshot(
    @Serializable(with = InstantSerializer::class) val date: Instant,
    @Serializable(with = InstantSerializer::class) val dayStart: Instant,
    val calories: Int,
    val calorieGoal: Int,
    val protein: Double,
    val proteinGoal: Int,
    val carbs: Double,
    val carbsGoal: Int,
    val fat: Double,
    val fatGoal: Int,
    /** The user's 4 selected Home nutrients. Null in snapshots persisted by older builds. */
    val homeNutrients: List<WidgetNutrient>? = null,
    /** User's theme gradient as raw RGB hex (e.g. 0x0D9488). Teal when absent. */
    val themeStartHex: Int? = null,
    val themeEndHex: Int? = null,
    val proteinHex: Int? = null,
    val carbsHex: Int? = null,
    val fatHex: Int? = null,
    val fiberHex: Int? = null,
    val nutrientCardCount: Int? = null,
    val calorieDisplayMode: String? = null,
    val effectiveCalorieGoal: Int? = null,
    val activeCaloriesToday: Int? = null,
    /** Decomposed gauge base for ADD_ACTIVE (sedentary budget); null in older snapshots. */
    val gaugeBaseCalorieGoal: Int? = null,
    /** [ActiveCalorieSource.storageKey] for today's active burn layer. */
    val activeCalorieSource: String? = null,
    /**
     * Display-only expected-day target in ADD_ACTIVE (base + the larger of the
     * active norm and live burn), mirroring the hero's goal line. Null when the
     * presentation is inactive: STATIC mode, estimate/manual-only days without
     * a measured source, and snapshots written by older builds (fall back to
     * [resolvedEffectiveCalorieGoal]).
     */
    val displayGoalTarget: Int? = null,
    /** The day's active norm backing [displayGoalTarget] (14-day measured average, else PAL estimate). */
    val activeBurnTypical: Int? = null,
    val stepsToday: Long? = null,
    val stepGoal: Int? = null,
    /** Defaults preserve decoding of snapshots written before the Water widget. */
    val waterTrackingEnabled: Boolean = false,
    val waterCurrentMl: Int = 0,
    val waterGoalMl: Int = 2_000,
    /** When false, water widget labels use fl oz (matches weight unit preference). */
    val waterUseMetric: Boolean = true,
    /**
     * Next planned drink from the adaptive reminder chain; null when reminders
     * are off, the goal is met, or the window is degenerate. Null in snapshots
     * written by older builds.
     */
    val waterNextFireAtMillis: Long? = null,
    /** Amount to drink at [waterNextFireAtMillis] (one cup, capped by the goal remainder); 0 when no plan. */
    val waterNextDrinkMl: Int = 0,
) {
    val resolvedCalorieMode: HomeCalorieDisplayMode
        get() = HomeCalorieDisplayMode.fromStorage(calorieDisplayMode)

    private val resolvedBurn: ResolvedActiveBurn?
        get() {
            val calories = activeCaloriesToday ?: return null
            if (calories <= 0) return null
            val source = ActiveCalorieSource.fromStorage(activeCalorieSource)
            return if (source == ActiveCalorieSource.UNAVAILABLE) {
                null
            } else {
                ResolvedActiveBurn(calories, source)
            }
        }

    val resolvedEffectiveCalorieMode: HomeCalorieDisplayMode
        get() = HomeCalorieDisplay.effectiveMode(resolvedCalorieMode, resolvedBurn)

    val resolvedGaugeBaseGoal: Int
        get() = gaugeBaseCalorieGoal ?: calorieGoal

    val resolvedEffectiveCalorieGoal: Int
        get() = effectiveCalorieGoal ?: calorieGoal

    val resolvedDisplayGoalTarget: Int
        get() = displayGoalTarget ?: resolvedEffectiveCalorieGoal

    val caloriesRemaining: Int
        get() = (resolvedDisplayGoalTarget - calories).coerceAtLeast(0)

    val proteinRemaining: Double get() = maxOf(0.0, proteinGoal.toDouble() - protein)
    val carbsRemaining: Double get() = maxOf(0.0, carbsGoal.toDouble() - carbs)
    val fatRemaining: Double get() = maxOf(0.0, fatGoal.toDouble() - fat)

    val calorieProgress: Double
        get() {
            val goal = resolvedDisplayGoalTarget
            if (goal <= 0) return 0.0
            return (calories.toDouble() / goal).coerceIn(0.0, 1.0)
        }
    val proteinProgress: Double get() = if (proteinGoal > 0) minOf(1.0, protein / proteinGoal) else 0.0
    val carbsProgress: Double get() = if (carbsGoal > 0) minOf(1.0, carbs / carbsGoal) else 0.0
    val fatProgress: Double get() = if (fatGoal > 0) minOf(1.0, fat / fatGoal) else 0.0
    val waterRemaining: Int get() = maxOf(0, waterGoalMl - waterCurrentMl)
    val waterProgress: Double get() = if (waterGoalMl > 0) minOf(1.0, waterCurrentMl.toDouble() / waterGoalMl) else 0.0

    val isStale: Boolean get() {
        val snapshotDay = dayStart.atZone(ZoneId.systemDefault()).toLocalDate()
        return snapshotDay != LocalDate.now()
    }

    /**
     * The 4 nutrient bars to render, matching the user's Home selection.
     * Legacy snapshots (no homeNutrients) yield the classic protein/carbs/fat.
     */
    val displayedHomeNutrients: List<WidgetNutrient> get() {
        val count = nutrientCardCount?.coerceIn(1, 4) ?: 4
        return homeNutrients?.takeIf { it.isNotEmpty() }?.take(count) ?: listOf(
            WidgetNutrient("protein", "Protein", "g", protein, proteinGoal.toDouble()),
            WidgetNutrient("carbs", "Carbs", "g", carbs, carbsGoal.toDouble()),
            WidgetNutrient("fat", "Fat", "g", fat, fatGoal.toDouble())
        )
    }

    /** First selected nutrient — what the "Protein" widget actually tracks. */
    val primaryHomeNutrient: WidgetNutrient get() = displayedHomeNutrients.first()

    fun emptyForToday(): WidgetSnapshot = copy(
        date = Instant.now(),
        dayStart = todayStart(),
        waterCurrentMl = 0,
        // The day rolled over and the app has not re-published yet — drop the
        // stale plan so the widget never shows yesterday's cadence.
        waterNextFireAtMillis = null,
        waterNextDrinkMl = 0,
    )

    fun nutrientColorHex(id: String): Int {
        val paletteFallback = themeStartHex ?: 0x006B5E
        return when (id) {
            "protein" -> proteinHex ?: paletteFallback
            "carbs" -> carbsHex ?: paletteFallback
            "fat" -> fatHex ?: paletteFallback
            "fiber" -> fiberHex ?: paletteFallback
            else -> paletteFallback
        }
    }

    companion object {
        fun placeholder(): WidgetSnapshot {
            val now = Instant.now()
            return WidgetSnapshot(
                date = now,
                dayStart = todayStart(),
                calories = 1247, calorieGoal = 2000,
                protein = 84.0, proteinGoal = 150,
                carbs = 132.0, carbsGoal = 220,
                fat = 42.0, fatGoal = 70
            )
        }

        fun empty(): WidgetSnapshot {
            val now = Instant.now()
            return WidgetSnapshot(
                date = now,
                dayStart = todayStart(),
                calories = 0, calorieGoal = 2000,
                protein = 0.0, proteinGoal = 150,
                carbs = 0.0, carbsGoal = 220,
                fat = 0.0, fatGoal = 70
            )
        }

        fun todayStart(): Instant =
            LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
    }
}
