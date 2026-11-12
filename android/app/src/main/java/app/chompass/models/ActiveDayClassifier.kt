package app.chompass.models

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Today's active layer is the day-type average from the start of the day, or
 * the blended norm when today has no qualified day type. Same-hour history
 * never moves that number. It only reports a [DayTypeDivergence] when this
 * hour looks significantly unlike the current day type, so the UI can ask
 * the user to switch.
 */
object ActiveDayClassifier {
    const val MIN_DEST_HOURLY = 5
    const val MIN_CONTRAST_HOURLY = 3

    /** Closeness to another group required before a switch prompt. Not a lean. */
    private const val SIGNIFICANT = 0.85

    enum class Kind { NONE, LIGHTER, HARDER, DAY_TYPE }

    data class ActiveDayClassification(
        val step: Int,
        val projectedActive: Int,
        val kind: Kind,
        val dayTypeName: String?,
        val hourlyReady: Boolean,
    )

    data class DayTypeGroup(
        val profileId: String,
        val name: String,
        val dates: Set<String>,
    )

    /** Non-null when the user should be asked to change today's day type. */
    data class DayTypeDivergence(
        val currentName: String,
        val suggestedProfileId: String?,
        val suggestedName: String?,
    )

    private val none = ActiveDayClassification(0, 0, Kind.NONE, null, false)

    /**
     * @param dayTypeAverage Qualified average for today's profile, or null.
     * The projected number is the mean of that profile's completed past days
     * when those dates are supplied, so today's partial burn cannot pull it.
     */
    fun classify(
        today: LocalDate,
        minuteOfDay: Int,
        isToday: Boolean,
        blendedActive: Int,
        dayTypeAverage: Int?,
        dayTypeName: String?,
        mergedActiveByDay: Map<String, Int>,
        hourlyByDay: Map<String, List<Int>>,
        liveActive: Int,
        dayTypeDates: Set<String> = emptySet(),
    ): ActiveDayClassification {
        if (!isToday) return none
        val projected = projectedActive(
            blendedActive = blendedActive,
            dayTypeAverage = dayTypeAverage,
            dayTypeDates = dayTypeDates,
            mergedActiveByDay = mergedActiveByDay,
            today = today,
        )
        if (projected <= 0) return none
        val named = dayTypeAverage != null && dayTypeAverage > 0
        return ActiveDayClassification(
            step = 0,
            projectedActive = projected,
            kind = if (named) Kind.DAY_TYPE else Kind.NONE,
            dayTypeName = if (named) dayTypeName else null,
            hourlyReady = false,
        )
    }

    fun projectedActive(
        blendedActive: Int,
        dayTypeAverage: Int?,
        dayTypeDates: Set<String>,
        mergedActiveByDay: Map<String, Int>,
        today: LocalDate,
    ): Int {
        val typed = dayTypeAverage?.takeIf { it > 0 } ?: return blendedActive.coerceAtLeast(0)
        val past = completedDays(today, mergedActiveByDay).filter { it in dayTypeDates }
        if (past.isEmpty()) return typed
        return past.map { mergedActiveByDay.getValue(it) }.average().roundToInt().coerceAtLeast(0)
    }

    /**
     * Null unless this hour is much closer to another day type (or to days
     * that are not this type) than to the current type's same-hour median.
     * A lean is not enough.
     */
    fun divergence(
        today: LocalDate,
        minuteOfDay: Int,
        liveActive: Int,
        currentName: String,
        currentDates: Set<String>,
        others: List<DayTypeGroup>,
        hourlyByDay: Map<String, List<Int>>,
        mergedActiveByDay: Map<String, Int>,
    ): DayTypeDivergence? {
        if (minuteOfDay <= 0 || currentName.isBlank()) return null
        val todayIso = today.toString()
        val pastCurrent = currentDates.filter { it != todayIso }
        val currentCumul = usable(pastCurrent, hourlyByDay, minuteOfDay)
        if (currentCumul.size < MIN_DEST_HOURLY) return null
        val currentMedian = median(currentCumul)
        val completed = completedDays(today, mergedActiveByDay)
        val otherIds = others.flatMap { it.dates }.toSet()
        val candidates = buildList {
            for (other in others) {
                if (other.name.isBlank()) continue
                val cumul = usable(other.dates.filter { it != todayIso }, hourlyByDay, minuteOfDay)
                if (cumul.size < MIN_CONTRAST_HOURLY) continue
                add(Candidate(other.profileId, other.name, median(cumul)))
            }
            val contrast = completed.filter { it !in currentDates && it !in otherIds }
            val contrastCumul = usable(contrast, hourlyByDay, minuteOfDay)
            if (contrastCumul.size >= MIN_CONTRAST_HOURLY) {
                add(Candidate(null, null, median(contrastCumul)))
            }
        }
        val live = liveActive.coerceAtLeast(0)
        val winner = candidates.mapNotNull { candidate ->
            if (!significantGap(currentMedian, candidate.median)) return@mapNotNull null
            val closeness = closenessTo(live, candidate.median, currentMedian)
            if (closeness < SIGNIFICANT) return@mapNotNull null
            candidate to closeness
        }.maxByOrNull { it.second }?.first ?: return null
        return DayTypeDivergence(
            currentName = currentName,
            suggestedProfileId = winner.profileId,
            suggestedName = winner.name,
        )
    }

    /**
     * Sum of hours before [minuteOfDay], plus the current hour prorated.
     * Any -1 in that span makes the day unusable.
     */
    fun cumulativeAt(slots: List<Int>?, minuteOfDay: Int): Int? {
        if (slots == null || slots.size != HourlyActiveHistory.HOURS || minuteOfDay <= 0) return null
        val hour = minuteOfDay / 60
        val minute = minuteOfDay % 60
        if (hour !in 0..23) return null
        var sum = 0
        for (h in 0 until hour) {
            val v = slots[h]
            if (v < 0) return null
            sum += v
        }
        val current = slots[hour]
        if (current < 0) return null
        sum += (current * minute / 60.0).roundToInt()
        return sum
    }

    private fun significantGap(a: Int, b: Int): Boolean {
        val higher = maxOf(a, b)
        if (higher == 0) return false
        return abs(a - b) * 5 >= higher
    }

    /** 1 when [live] is on [dest], 0 when it is on [other]. */
    private fun closenessTo(live: Int, dest: Int, other: Int): Double {
        val distDest = abs(live - dest)
        val distOther = abs(live - other)
        val sum = distDest + distOther
        if (sum == 0) return 0.0
        return 1.0 - distDest.toDouble() / sum.toDouble()
    }

    /** Past days inside the day-type window. Today is excluded; so is the day just outside it. */
    private fun completedDays(today: LocalDate, merged: Map<String, Int>): List<String> =
        (1 until DayTypeActiveStats.WINDOW_DAYS).map { today.minusDays(it.toLong()).toString() }
            .filter { (merged[it] ?: 0) > 0 }

    private fun usable(
        dates: List<String>,
        hourly: Map<String, List<Int>>,
        minuteOfDay: Int,
    ): List<Int> = dates.mapNotNull { cumulativeAt(hourly[it], minuteOfDay) }

    private fun median(values: List<Int>): Int {
        val sorted = values.sorted()
        val n = sorted.size
        return if (n % 2 == 1) {
            sorted[n / 2]
        } else {
            ((sorted[n / 2 - 1] + sorted[n / 2]) / 2.0).roundToInt()
        }
    }

    private data class Candidate(
        val profileId: String?,
        val name: String?,
        val median: Int,
    )
}
