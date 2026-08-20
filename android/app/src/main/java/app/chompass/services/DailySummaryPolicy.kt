package app.chompass.services

import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Pure nightly energy-balance math for the Daily summary notification.
 *
 * Burned = Health Connect total energy when present, else BMR + today's
 * active (measured-wins: never substitute the PAL estimate onto a live
 * measured 0). Display only — does not change stored goals.
 *
 * On target when `|burned − eaten| ≤ [ON_TARGET_BAND_KCAL]`.
 */
object DailySummaryPolicy {
    const val ON_TARGET_BAND_KCAL = 100
    const val YESTERDAY_BEFORE_HOUR = 4

    fun summaryDate(now: ZonedDateTime): LocalDate =
        if (now.hour < YESTERDAY_BEFORE_HOUR) now.toLocalDate().minusDays(1) else now.toLocalDate()

    fun evaluate(input: DailySummaryInput): DailySummaryResult {
        if (!input.hasFoodLogged) return DailySummaryResult(DailySummaryVerdict.SKIP)
        val burned = resolveBurned(input) ?: return DailySummaryResult(DailySummaryVerdict.STATIC)
        val delta = burned - input.eatenKcal
        val verdict = when {
            kotlin.math.abs(delta) <= ON_TARGET_BAND_KCAL -> DailySummaryVerdict.ON_TARGET
            delta > 0 -> DailySummaryVerdict.DEFICIT
            else -> DailySummaryVerdict.SURPLUS
        }
        return DailySummaryResult(
            verdict = verdict,
            eaten = input.eatenKcal,
            burned = burned,
            delta = delta,
            proteinG = input.proteinG,
            carbsG = input.carbsG,
            fatG = input.fatG,
        )
    }

    /**
     * 1. HC / debug total calories for the day, if `> 0`
     * 2. Else BMR + active (measured if [DailySummaryInput.energyLive], else PAL
     *    estimate only when no live source exists)
     * 3. Else null → static copy
     */
    fun resolveBurned(input: DailySummaryInput): Int? {
        val total = input.totalKcal
        if (total != null && total > 0) return total
        val active = if (input.energyLive) {
            input.activeKcal.coerceAtLeast(0)
        } else {
            input.estimatedActiveKcal.coerceAtLeast(0)
        }
        val burned = input.bmrKcal.coerceAtLeast(0) + active
        return burned.takeIf { it > 0 }
    }
}

data class DailySummaryInput(
    val eatenKcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val bmrKcal: Int,
    val activeKcal: Int,
    val energyLive: Boolean,
    val totalKcal: Int?,
    val estimatedActiveKcal: Int,
    val hasFoodLogged: Boolean,
)

enum class DailySummaryVerdict { DEFICIT, SURPLUS, ON_TARGET, SKIP, STATIC }

data class DailySummaryResult(
    val verdict: DailySummaryVerdict,
    val eaten: Int = 0,
    val burned: Int = 0,
    /** `burned − eaten`. Positive = deficit. */
    val delta: Int = 0,
    val proteinG: Int = 0,
    val carbsG: Int = 0,
    val fatG: Int = 0,
)
