package app.chompass.models

import app.chompass.R
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Per-drink milk sidecar on caffeine presets (Codeberg #55). Storage key is
 * what [HabitPreset.milkKind] stores; [labelRes] is the diary food name.
 */
enum class MilkKind(val storageKey: String, val labelRes: Int) {
    WHOLE("whole", R.string.milk_kind_whole),
    SEMI("semi", R.string.milk_kind_semi),
    SKIM("skim", R.string.milk_kind_skim),
    OAT("oat", R.string.milk_kind_oat),
    ALMOND("almond", R.string.milk_kind_almond);

    companion object {
        /** Existing drinks heuristic in [ServingUnitHeuristics]. */
        const val DENSITY_G_PER_ML = 1.03
        const val MAX_ML = 500

        fun fromStorage(raw: String?): MilkKind? =
            entries.firstOrNull { it.storageKey == raw }
    }
}

data class MilkMacros(
    val kcal: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val calcium: Double,
    val grams: Double,
)

private data class MilkPer100(
    val kcal: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val calcium: Double,
)

private val MILK_PER_100: Map<MilkKind, MilkPer100> = mapOf(
    MilkKind.WHOLE to MilkPer100(62, 3.3, 4.8, 3.3, 120.0),
    MilkKind.SEMI to MilkPer100(47, 3.4, 4.8, 1.7, 120.0),
    MilkKind.SKIM to MilkPer100(35, 3.4, 5.0, 0.1, 125.0),
    MilkKind.OAT to MilkPer100(46, 1.0, 6.7, 1.5, 120.0),
    MilkKind.ALMOND to MilkPer100(15, 0.6, 0.6, 1.1, 180.0),
)

private fun Double.round1(): Double = (this * 10.0).roundToInt() / 10.0

fun MilkKind.scaled(ml: Int): MilkMacros {
    val per100 = MILK_PER_100.getValue(this)
    val f = ml / 100.0
    return MilkMacros(
        kcal = (per100.kcal * f).roundToInt(),
        protein = (per100.protein * f).round1(),
        carbs = (per100.carbs * f).round1(),
        fat = (per100.fat * f).round1(),
        calcium = (per100.calcium * f).round1(),
        grams = ml * MilkKind.DENSITY_G_PER_ML,
    )
}

fun MilkKind.toFoodEntry(ml: Int, at: Instant, mealId: String, name: String): FoodEntry {
    val macros = scaled(ml)
    return FoodEntry(
        name = name,
        calories = macros.kcal,
        protein = macros.protein,
        carbs = macros.carbs,
        fat = macros.fat,
        timestamp = at,
        emoji = "🥛",
        source = FoodSource.MANUAL,
        mealType = mealId,
        calcium = macros.calcium,
        caffeine = null,
        servingSizeGrams = macros.grams,
        servingUnitOptions = listOf(
            ServingUnitOption(
                unit = "ml",
                gramsPerUnit = MilkKind.DENSITY_G_PER_ML,
                quantity = ml.toDouble(),
            ),
        ),
        selectedServingUnit = "ml",
        selectedServingQuantity = ml.toDouble(),
    )
}
