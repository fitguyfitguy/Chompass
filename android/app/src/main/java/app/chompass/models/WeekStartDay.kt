package app.chompass.models

import app.chompass.R
import java.time.DayOfWeek

/** First day of the home week strip. Default Monday (legacy `weekStartsOnMonday = true`). */
enum class WeekStartDay(val storageValue: String, val displayNameRes: Int) {
    SUNDAY("sunday", R.string.settings_week_sunday),
    MONDAY("monday", R.string.settings_week_monday),
    SATURDAY("saturday", R.string.settings_week_saturday);

    val javaDay: DayOfWeek
        get() = when (this) {
            SUNDAY -> DayOfWeek.SUNDAY
            MONDAY -> DayOfWeek.MONDAY
            SATURDAY -> DayOfWeek.SATURDAY
        }

    companion object {
        fun fromStorage(value: String?, weekStartsOnMonday: Boolean? = null): WeekStartDay {
            return when (value?.lowercase()) {
                SUNDAY.storageValue -> SUNDAY
                SATURDAY.storageValue -> SATURDAY
                MONDAY.storageValue -> MONDAY
                else -> if (weekStartsOnMonday == false) SUNDAY else MONDAY
            }
        }
    }
}
