package app.chompass.models

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID

/**
 * One row per calendar day with food entries: the day's summed calories,
 * macros, and every goal-bearing optional nutrient (#75) so Progress can
 * average any nutrient the user selects, not just the original trio.
 * This is the on-disk daily-aggregate cache (flippidity C.1) that
 * Progress reads instead of decoding the year of [FoodEntry] rows, so
 * All-range compute never holds the full diary in memory.
 *
 * Stored one month file at a time in `chompass-buckets/food-aggregates/`
 * ([app.chompass.data.JsonBucketStore]), derived from the `food/` month files
 * on every food write — it is a cache, never a source of truth, and rebuilds
 * from the food files if it is ever emptied.
 *
 * A day is present iff it has at least one entry, even when the sums are all
 * zero: a 0-kcal log still counts toward Progress' macro-average day count,
 * so a zero row must survive rather than being dropped as "empty".
 */
@Serializable
data class DailyFoodTotals(
    @Serializable(with = LocalDateSerializer::class)
    val date: LocalDate,
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double = 0.0,
    val sugar: Double = 0.0,
    val sodium: Double = 0.0,
    val addedSugar: Double = 0.0,
    val saturatedFat: Double = 0.0,
    val cholesterol: Double = 0.0,
    val potassium: Double = 0.0,
    val transFat: Double = 0.0,
    val calcium: Double = 0.0,
    val iron: Double = 0.0,
    val magnesium: Double = 0.0,
    val zinc: Double = 0.0,
    val vitaminA: Double = 0.0,
    val vitaminC: Double = 0.0,
    val vitaminD: Double = 0.0,
    val vitaminB12: Double = 0.0,
    val vitaminE: Double = 0.0,
    val vitaminK: Double = 0.0,
    val folate: Double = 0.0,
    val omega3: Double = 0.0,
    val caffeine: Double = 0.0,
) {
    /**
     * The day's summed amount of [nutrient] (HomeTopNutrient.current
     * equivalent on the aggregate row, so range averages never decode the
     * per-entry diary). Macros included for reuse; Progress' nutrient
     * averages are non-macro only.
     */
    fun amountOf(nutrient: HomeTopNutrient): Double = when (nutrient) {
        HomeTopNutrient.PROTEIN -> protein
        HomeTopNutrient.CARBS -> carbs
        HomeTopNutrient.FAT -> fat
        HomeTopNutrient.FIBER -> fiber
        HomeTopNutrient.SUGAR -> sugar
        HomeTopNutrient.ADDED_SUGAR -> addedSugar
        HomeTopNutrient.SATURATED_FAT -> saturatedFat
        HomeTopNutrient.CHOLESTEROL -> cholesterol
        HomeTopNutrient.SODIUM -> sodium
        HomeTopNutrient.POTASSIUM -> potassium
        HomeTopNutrient.TRANS_FAT -> transFat
        HomeTopNutrient.CALCIUM -> calcium
        HomeTopNutrient.IRON -> iron
        HomeTopNutrient.MAGNESIUM -> magnesium
        HomeTopNutrient.ZINC -> zinc
        HomeTopNutrient.VITAMIN_A -> vitaminA
        HomeTopNutrient.VITAMIN_C -> vitaminC
        HomeTopNutrient.VITAMIN_D -> vitaminD
        HomeTopNutrient.VITAMIN_B12 -> vitaminB12
        HomeTopNutrient.VITAMIN_E -> vitaminE
        HomeTopNutrient.VITAMIN_K -> vitaminK
        HomeTopNutrient.FOLATE -> folate
        HomeTopNutrient.OMEGA3 -> omega3
        HomeTopNutrient.CAFFEINE -> caffeine
    }

    /** Deterministic per-day id for [app.chompass.data.JsonBucketStore] upserts. */
    val id: UUID get() = UUID.nameUUIDFromBytes(date.toString().toByteArray())
}
