package app.chompass.ui.home

import app.chompass.models.FoodEntry
import app.chompass.models.MealCatalog
import app.chompass.models.MealType
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

/** Home / queue / week-strip forward window (Codeberg #96). Copy To wheels stay year-bounded only. */
internal const val DIARY_FUTURE_WEEKS = 8

internal fun maxDiaryNavDate(today: LocalDate): LocalDate =
    today.plusWeeks(DIARY_FUTURE_WEEKS.toLong())

internal fun canAdvanceDiaryDay(from: LocalDate, today: LocalDate): Boolean =
    !from.plusDays(1).isAfter(maxDiaryNavDate(today))

/** Meal slot for new logs: catalog at the stamped clock, else [nowTime]. */
internal fun mealIdForLogging(
    catalog: MealCatalog,
    timeOverride: LocalTime?,
    nowTime: LocalTime,
): String = catalog.mealIdAt(timeOverride ?: nowTime)

/** Relog/copy keep [templateMealType] when meal-time suggestions are off. */
internal fun loggingSlotFor(
    templateMealType: String,
    timesEnabled: Boolean,
    catalog: MealCatalog,
    timeOverride: LocalTime?,
    nowTime: LocalTime,
): String =
    if (!timesEnabled) templateMealType else mealIdForLogging(catalog, timeOverride, nowTime)

/** Planned iff mode is on and this is a plan action or the target day is in the future. */
internal fun plannedFor(
    mode: Boolean,
    planAction: Boolean,
    targetDate: LocalDate,
    today: LocalDate,
): Boolean = mode && (planAction || targetDate.isAfter(today))

/**
 * Log now: future plans move to now (slot per the #102 rule); today/past
 * plans just clear the flag.
 */
internal fun confirmPlannedEntry(
    entry: FoodEntry,
    today: LocalDate,
    now: Instant,
    zone: ZoneId,
    timesEnabled: Boolean,
    catalog: MealCatalog,
): FoodEntry {
    // Log-now is only wired for planned rows; anything else passes through.
    if (!entry.planned) return entry
    val entryDay = entry.timestamp.atZone(zone).toLocalDate()
    if (!entryDay.isAfter(today)) return entry.copy(planned = false)
    val nowTime = now.atZone(zone).toLocalTime().withSecond(0).withNano(0)
    return entry.copy(
        timestamp = today.atTime(nowTime).atZone(zone).toInstant(),
        mealType = loggingSlotFor(entry.mealType, timesEnabled, catalog, null, nowTime),
        planned = false,
    )
}

/** UI prefill: Other when suggestions are off, else catalog slot at [time]. */
internal fun suggestedSlotFor(
    timesEnabled: Boolean,
    catalog: MealCatalog,
    time: LocalTime,
): String =
    if (!timesEnabled) MealType.OTHER.id else catalog.mealIdAt(time)

/**
 * Review-sheet default (Codeberg #102). Saved-meal templates follow
 * [loggingSlotFor]: clock slot when suggestions are on, stored slot when
 * off. Fresh analyses use [suggestedSlotFor].
 */
internal fun reviewSlotFor(
    templateMealType: String?,
    timesEnabled: Boolean,
    catalog: MealCatalog,
    timeOverride: LocalTime?,
    nowTime: LocalTime,
): String =
    if (templateMealType != null) {
        loggingSlotFor(templateMealType, timesEnabled, catalog, timeOverride, nowTime)
    } else {
        suggestedSlotFor(timesEnabled, catalog, timeOverride ?: nowTime)
    }

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

internal fun reviewLogTimeAfterOverride(
    current: LocalTime,
    override: LocalTime?,
    touched: Boolean,
): LocalTime = if (!touched && override != null) override.withSecond(0).withNano(0) else current
