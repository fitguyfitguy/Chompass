package app.chompass.models

import java.time.LocalTime

data class MealSchedule(
    val breakfastStartMinutes: Int = DEFAULT_BREAKFAST_START,
    val lunchStartMinutes: Int = DEFAULT_LUNCH_START,
    val dinnerStartMinutes: Int = DEFAULT_DINNER_START,
    val snackStartMinutes: Int = DEFAULT_SNACK_START,
) {
    val isValid: Boolean
        get() {
            val starts = listOf(breakfastStartMinutes, lunchStartMinutes, dinnerStartMinutes, snackStartMinutes)
            if (starts.any { it !in 0 until MINUTES_PER_DAY }) return false
            // Mirrors MealCatalog.validate(): gaps on clock-sorted starts.
            val ordered = starts.sorted()
            var span = 0
            for (i in 1 until ordered.size) {
                val delta = (ordered[i] - ordered[i - 1] + MINUTES_PER_DAY) % MINUTES_PER_DAY
                if (delta < MealCatalog.MIN_GAP_MINUTES) return false
                span += delta
            }
            return span < MINUTES_PER_DAY
        }

    fun mealTypeAt(time: LocalTime): MealType {
        val minutes = time.hour * 60 + time.minute
        // The slot that started most recently around the clock owns the moment;
        // slot order is a rotation, so this covers schedules wrapping midnight.
        var best = MealType.BREAKFAST
        var bestOffset = (minutes - breakfastStartMinutes + MINUTES_PER_DAY) % MINUTES_PER_DAY
        val lunchOffset = (minutes - lunchStartMinutes + MINUTES_PER_DAY) % MINUTES_PER_DAY
        if (lunchOffset < bestOffset) {
            best = MealType.LUNCH
            bestOffset = lunchOffset
        }
        val dinnerOffset = (minutes - dinnerStartMinutes + MINUTES_PER_DAY) % MINUTES_PER_DAY
        if (dinnerOffset < bestOffset) {
            best = MealType.DINNER
            bestOffset = dinnerOffset
        }
        if ((minutes - snackStartMinutes + MINUTES_PER_DAY) % MINUTES_PER_DAY < bestOffset) {
            best = MealType.SNACK
        }
        return best
    }

    fun validatedOrDefault(): MealSchedule = if (isValid) this else Default

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
        /** Matches legacy Chompass hardcoded meal windows (5–10 / 11–14 / 15–20 / else snack). */
        const val DEFAULT_BREAKFAST_START = 5 * 60
        const val DEFAULT_LUNCH_START = 11 * 60
        const val DEFAULT_DINNER_START = 15 * 60
        const val DEFAULT_SNACK_START = 21 * 60

        val Default = MealSchedule()
    }
}

object CurrentMealSchedule {
    @Volatile
    var value: MealSchedule = MealSchedule.Default
}
