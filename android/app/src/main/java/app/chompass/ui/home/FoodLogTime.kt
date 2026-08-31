package app.chompass.ui.home

import app.chompass.models.FoodEntry
import app.chompass.models.MealCatalog
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Codeberg #77 part 3: stamp new food logs with a chosen clock time on the
 * selected Home day.
 *
 * [timeOverride] null keeps today's `now` (seconds included) and past/future
 * days at wall-clock time-of-day. When set, every day uses that clock time
 * (seconds stripped) so a 13:00 lunch session is one time, not per-row now.
 */
internal fun timestampForLogging(
    day: LocalDate,
    now: Instant,
    zone: ZoneId,
    timeOverride: LocalTime?,
): Instant {
    val nowZoned = now.atZone(zone)
    if (timeOverride == null && day == nowZoned.toLocalDate()) return now
    val clock = timeOverride?.withSecond(0)?.withNano(0) ?: nowZoned.toLocalTime()
    return day.atTime(clock).atZone(zone).toInstant()
}

/** Meal slot for new logs: catalog at the stamped clock, else [nowTime]. */
internal fun mealIdForLogging(
    catalog: MealCatalog,
    timeOverride: LocalTime?,
    nowTime: LocalTime,
): String = catalog.mealIdAt(timeOverride ?: nowTime)

/**
 * Other rows in the same meal slot on [original]'s calendar day. Used by
 * Edit Food "apply this date and time to other items".
 */
internal fun siblingEntriesForTimeApply(
    dayEntries: List<FoodEntry>,
    original: FoodEntry,
    zone: ZoneId,
): List<FoodEntry> {
    val originalDay = original.timestamp.atZone(zone).toLocalDate()
    return dayEntries.filter { sibling ->
        sibling.id != original.id &&
            sibling.mealType == original.mealType &&
            sibling.timestamp.atZone(zone).toLocalDate() == originalDay
    }
}
