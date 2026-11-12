package app.chompass.models

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Slides today's active layer from the blended full-day norm toward a
 * destination group, and only as far as same-hour history supports.
 *
 * The destination is a day-type average when that profile has enough samples,
 * otherwise the nearer of two clusters of tracked days. It is never a guessed
 * training day at 9am: without a usable same-hour quote the step stays 0 and
 * the projection is the blend.
 */
object ActiveDayClassifier {
    const val MIN_COMPLETED_DAYS = 8
    const val MIN_CLUSTER_DAYS = 3
    const val MIN_DEST_HOURLY = 5
    const val MIN_CONTRAST_HOURLY = 3
    const val MAX_ITERATIONS = 20
    private const val STEP_1 = 0.65
    private const val STEP_2 = 0.85

    enum class Kind { NONE, LIGHTER, HARDER, DAY_TYPE }

    data class ActiveDayClassification(
        val step: Int,
        val projectedActive: Int,
        val kind: Kind,
        val dayTypeName: String?,
        val hourlyReady: Boolean,
    )

    private val none = ActiveDayClassification(0, 0, Kind.NONE, null, false)

    /**
     * @param dayTypeDates ISO dates tagged with today's profile. Ignored unless
     * [dayTypeAverage] is non-null. The contrast group is every completed day
     * not in this set.
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
        val blend = blendedActive.coerceAtLeast(0)
        if (!isToday || blend <= 0) return none
        if (minuteOfDay <= 0) {
            return ActiveDayClassification(0, blend, Kind.NONE, null, false)
        }
        val completed = completedDays(today, mergedActiveByDay)
        val groups = if (dayTypeAverage != null) {
            Groups(
                destKind = Kind.DAY_TYPE,
                destActive = dayTypeAverage.coerceAtLeast(0),
                destName = dayTypeName,
                destDates = completed.filter { it in dayTypeDates },
                contrastDates = completed.filter { it !in dayTypeDates },
            )
        } else {
            clusters(completed, mergedActiveByDay, hourlyByDay, minuteOfDay, liveActive)
                ?: return stay(blend)
        }
        return stepToward(groups, hourlyByDay, minuteOfDay, liveActive, blend)
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
        // Unsupervised destination is the nearer cluster, so distDest is distNear.
        // Day type keeps its own group as the destination: a morning that looks
        // like the contrast stays on the blend.
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
            dayTypeName = if (groups.destKind == Kind.DAY_TYPE) groups.destName else null,
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

    private fun completedDays(today: LocalDate, merged: Map<String, Int>): List<String> =
        (1..DayTypeActiveStats.WINDOW_DAYS).map { today.minusDays(it.toLong()).toString() }
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
