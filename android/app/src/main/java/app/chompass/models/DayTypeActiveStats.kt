package app.chompass.models

import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Per-day-type typical active burn (day-type × active-burn integration).
 * Pure: journal-first grouping over a lookback window. Derived only — never
 * stored on [MacroDayProfile] / sync-1.2.
 */
object DayTypeActiveStats {
    const val WINDOW_DAYS = 28
    const val MIN_SAMPLES = 3
    const val HISTORY_KEEP_DAYS = 60

    data class TypeAverage(
        val profileId: String,
        val averageKcal: Int,
        val sampleCount: Int,
    )

    data class DailyTagged(
        val date: String,
        val profileId: String,
        val profileName: String?,
        val activeKcal: Int,
    )

    data class Result(
        val byProfileId: Map<String, TypeAverage> = emptyMap(),
        val daily: List<DailyTagged> = emptyList(),
    ) {
        fun typicalFor(profileId: String?, minSamples: Int = MIN_SAMPLES): Int? {
            if (profileId == null) return null
            val row = byProfileId[profileId] ?: return null
            return row.averageKcal.takeIf { row.sampleCount >= minSamples }
        }
    }

    data class TypicalResolution(
        val kcal: Int,
        val typicalIsDayType: Boolean,
    )

    fun compute(
        journal: List<GoalJournalEntry>,
        activeByDay: Map<String, Int>,
        today: LocalDate,
        windowDays: Int = WINDOW_DAYS,
        minSamples: Int = MIN_SAMPLES,
    ): Result {
        val start = today.minusDays((windowDays - 1).toLong())
        val journalByDate = journal.associateBy { it.date }
        val daily = mutableListOf<DailyTagged>()
        var d = start
        while (!d.isAfter(today)) {
            val iso = d.toString()
            val entry = journalByDate[iso]
            val profileId = entry?.profileId
            val active = activeByDay[iso]
            if (profileId != null && active != null && active > 0) {
                daily += DailyTagged(
                    date = iso,
                    profileId = profileId,
                    profileName = entry.profileName,
                    activeKcal = active,
                )
            }
            d = d.plusDays(1)
        }
        val byProfile = daily.groupBy { it.profileId }.mapValues { (id, rows) ->
            val avg = (rows.sumOf { it.activeKcal }.toDouble() / rows.size).roundToInt()
            TypeAverage(profileId = id, averageKcal = avg, sampleCount = rows.size)
        }
        // Keep only qualified averages in the public map? Callers need sample
        // counts for captions ("n of last 28"). Keep all; typicalFor applies floor.
        return Result(byProfileId = byProfile, daily = daily)
    }

    fun resolveTypical(
        viewedProfileId: String?,
        stats: Result,
        blendedMeasured: Int,
        palEstimate: Int,
        minSamples: Int = MIN_SAMPLES,
    ): TypicalResolution {
        val perType = stats.typicalFor(viewedProfileId, minSamples)
        if (perType != null && perType > 0) {
            return TypicalResolution(kcal = perType, typicalIsDayType = true)
        }
        if (blendedMeasured > 0) {
            return TypicalResolution(kcal = blendedMeasured, typicalIsDayType = false)
        }
        return TypicalResolution(kcal = palEstimate.coerceAtLeast(0), typicalIsDayType = false)
    }

    fun mergeDayTotals(
        healthConnectByDay: Map<String, Int>,
        manualByDay: Map<String, Int>,
    ): Map<String, Int> {
        val keys = healthConnectByDay.keys + manualByDay.keys
        return keys.associateWith { date ->
            healthConnectByDay.getOrDefault(date, 0).coerceAtLeast(0) +
                manualByDay.getOrDefault(date, 0).coerceAtLeast(0)
        }.filterValues { it > 0 }
    }

    fun sumManualByDay(entries: List<ManualActiveEntry>): Map<String, Int> =
        entries.groupBy { it.date }.mapValues { (_, rows) -> rows.sumOf { it.calories.coerceAtLeast(0) } }
            .filterValues { it > 0 }

    fun pruneHistory(
        map: Map<String, Int>,
        today: LocalDate,
        keepDays: Int = HISTORY_KEEP_DAYS,
    ): Map<String, Int> {
        val cutoff = today.minusDays((keepDays - 1).toLong())
        return map.filter { (iso, kcal) ->
            kcal > 0 && GoalJournal.parseDateOrNull(iso)?.let { !it.isBefore(cutoff) } == true
        }
    }
}
