package app.chompass.models

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID

/**
 * One row per calendar day with food entries: the day's summed calories,
 * macros, and the extra nutrients Progress averages (fiber, sugar, sodium).
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
) {
    /** Deterministic per-day id for [app.chompass.data.JsonBucketStore] upserts. */
    val id: UUID get() = UUID.nameUUIDFromBytes(date.toString().toByteArray())
}
