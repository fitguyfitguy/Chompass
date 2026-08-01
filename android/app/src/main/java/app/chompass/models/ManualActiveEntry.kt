package app.chompass.models

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID

/** User-logged active burn (walk/ride/etc.) without Health Connect. */
@Serializable
data class ManualActiveEntry(
    val id: String = UUID.randomUUID().toString(),
    /** ISO local date YYYY-MM-DD */
    val date: String,
    val name: String,
    val calories: Int,
) {
    fun localDate(): LocalDate = LocalDate.parse(date)

    companion object {
        fun forDay(date: LocalDate, name: String, calories: Int): ManualActiveEntry =
            ManualActiveEntry(
                date = date.toString(),
                name = name.trim().ifEmpty { "Activity" },
                calories = calories.coerceAtLeast(0),
            )
    }
}
