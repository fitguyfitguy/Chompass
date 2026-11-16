package app.chompass.models

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A day with a qualified day type shows that average from the start. Same-hour
 * history does not move that number; it only reports a [DayTypeDivergence]
 * so the UI can ask for a switch.
 *
 * A day with no type starts at the blended norm and slides toward the nearer
 * of two clusters, only as far as same-hour history supports.
 */
object ActiveDayClassifier {
    const val MIN_COMPLETED_DAYS = 8
    const val MIN_CLUSTER_DAYS = 3
    const val MIN_DEST_HOURLY = 5
    const val MIN_CONTRAST_HOURLY = 3
    const val MAX_ITERATIONS = 20
    private const val STEP_1 = 0.65
    private const val STEP_2 = 0.85

    /** Closeness to another day type required before a switch prompt. Not a lean. */
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
        if (dayTypeAverage != null && dayTypeAverage > 0) {
            val projected = projectedActive(
                blendedActive = blendedActive,
                dayTypeAverage = dayTypeAverage,
                dayTypeDates = dayTypeDates,
                mergedActiveByDay = mergedActiveByDay,
                today = today,
            )
            if (projected <= 0) return none
            return ActiveDayClassification(
                step = 0,
                projectedActive = projected,
                kind = Kind.DAY_TYPE,
                dayTypeName = dayTypeName,
                hourlyReady = false,
            )
        }
        val blend = blendedActive.coerceAtLeast(0)
        if (blend <= 0) return none
        if (minuteOfDay <= 0) {
            return ActiveDayClassification(0, blend, Kind.NONE, null, false)
        }
        val completed = completedDays(today, mergedActiveByDay)
        val groups = clusters(completed, mergedActiveByDay, hourlyByDay, minuteOfDay, liveActive)
            ?: return stay(blend)
        return stepToward(groups, hourlyByDay, minuteOfDay, liveActive, blend)
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

    private fun stepToward(
        groups: Groups,
        hourly: Map<String, List<Int>>,
        minuteOfDay: Int,
        liveActive: Int,
        blend: Int,
    ): ActiveDayClassification {
        val destCumul = usable(groups.destDates, hourly, minuteOfDay)
        val contrastCumul = usable(groups.contrastDates, hourly, minuteOfDay)
        val hourlyReady = destCumul.size >= MIN_DEST_HOURLY &&
            contrastCumul.size >= MIN_CONTRAST_HOURLY
        if (!hourlyReady || destCumul.isEmpty() || contrastCumul.isEmpty()) {
            return stay(blend, hourlyReady = false)
        }
        val destMedian = median(destCumul)
        val contrastMedian = median(contrastCumul)
        val higher = maxOf(destMedian, contrastMedian)
        if (higher == 0 || abs(destMedian - contrastMedian) * 5 < higher) {
            return stay(blend, hourlyReady = true)
        }
        val distDest = abs(liveActive - destMedian)
        val distContrast = abs(liveActive - contrastMedian)
        if (distDest == 0 && distContrast == 0) return stay(blend, hourlyReady = true)
        val closeness = 1.0 - distDest.toDouble() / (distDest + distContrast).toDouble()
        val step = when {
            closeness < STEP_1 -> 0
            closeness < STEP_2 -> 1
            else -> 2
        }
        if (step == 0) return stay(blend, hourlyReady = true)
        val projected = if (step == 1) {
            blend + ((groups.destActive - blend) / 2.0).roundToInt()
        } else {
            groups.destActive
        }
        return ActiveDayClassification(
            step = step,
            projectedActive = projected.coerceAtLeast(0),
            kind = groups.destKind,
            dayTypeName = null,
            hourlyReady = true,
        )
    }

    private fun stay(blend: Int, hourlyReady: Boolean = false) = ActiveDayClassification(
        step = 0,
        projectedActive = blend,
        kind = Kind.NONE,
        dayTypeName = null,
        hourlyReady = hourlyReady,
    )

    private data class Groups(
        val destKind: Kind,
        val destActive: Int,
        val destName: String?,
        val destDates: List<String>,
        val contrastDates: List<String>,
    )

    /**
     * 1D k-means, k = 2. Seeds are min and max. An empty cluster, a cluster
     * under 3 days, or a split under 25% of the high mean aborts. The
     * destination is the cluster whose same-hour median is closer to [liveActive].
     */
    private fun clusters(
        dates: List<String>,
        merged: Map<String, Int>,
        hourly: Map<String, List<Int>>,
        minuteOfDay: Int,
        liveActive: Int,
    ): Groups? {
        if (dates.size < MIN_COMPLETED_DAYS) return null
        val values = dates.map { merged.getValue(it) }
        var low = values.min().toDouble()
        var high = values.max().toDouble()
        if (low == high) return null
        var assignment = IntArray(dates.size) { -1 }
        for (iter in 0 until MAX_ITERATIONS) {
            val next = IntArray(dates.size)
            var lowSum = 0.0
            var highSum = 0.0
            var lowN = 0
            var highN = 0
            for (i in dates.indices) {
                val v = values[i].toDouble()
                if (abs(v - low) <= abs(v - high)) {
                    next[i] = 0
                    lowSum += v
                    lowN++
                } else {
                    next[i] = 1
                    highSum += v
                    highN++
                }
            }
            if (lowN == 0 || highN == 0) return null
            val stable = next.contentEquals(assignment) && iter > 0
            assignment = next
            low = lowSum / lowN
            high = highSum / highN
            if (stable) break
        }
        val lowMean = low.roundToInt()
        val highMean = high.roundToInt()
        val lowDates = dates.filterIndexed { i, _ -> assignment[i] == 0 }
        val highDates = dates.filterIndexed { i, _ -> assignment[i] == 1 }
        if (lowDates.size < MIN_CLUSTER_DAYS || highDates.size < MIN_CLUSTER_DAYS) return null
        if ((highMean - lowMean) * 4 < highMean) return null
        val lowCumul = usable(lowDates, hourly, minuteOfDay)
        val highCumul = usable(highDates, hourly, minuteOfDay)
        val destIsHigh = when {
            lowCumul.isEmpty() && highCumul.isEmpty() -> false
            lowCumul.isEmpty() -> true
            highCumul.isEmpty() -> false
            else -> abs(liveActive - median(highCumul)) < abs(liveActive - median(lowCumul))
        }
        return if (destIsHigh) {
            Groups(Kind.HARDER, highMean, null, highDates, lowDates)
        } else {
            Groups(Kind.LIGHTER, lowMean, null, lowDates, highDates)
        }
    }
}
