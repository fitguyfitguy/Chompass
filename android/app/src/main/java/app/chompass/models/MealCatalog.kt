package app.chompass.models

import java.time.LocalTime
import kotlin.random.Random
import kotlinx.serialization.Serializable

/** User-facing meal slot. [id] is stable; blank [label] means locale default for builtins. */
@Serializable
data class MealDef(
    val id: String,
    val label: String = "",
    val startMinutes: Int? = null,
    val enabled: Boolean = true,
) {
    val isBuiltin: Boolean get() = MealType.fromId(id) != null
    val isCustom: Boolean get() = id.startsWith(MealCatalog.CUSTOM_PREFIX)
}

@Serializable
data class MealCatalog(
    val meals: List<MealDef> = defaultMeals(),
    val version: Int = 1,
) {
    val isValid: Boolean get() = validate() == null

    fun validate(): String? {
        if (meals.isEmpty()) return "empty"
        if (meals.size > MAX_MEALS) return "too_many"
        val ids = meals.map { it.id }
        if (ids.toSet().size != ids.size) return "duplicate_id"
        for (def in meals) {
            if (def.id.isBlank()) return "blank_id"
            if (def.isCustom && !CUSTOM_ID_REGEX.matches(def.id)) return "bad_custom_id"
            if (!def.isBuiltin && !def.isCustom) return "bad_id"
            if (def.label.length > MAX_LABEL) return "label"
            val start = def.startMinutes
            if (start != null && start !in 0 until MealSchedule.MINUTES_PER_DAY) return "start"
        }
        val timed = meals.filter { it.enabled && it.startMinutes != null }
        if (timed.isEmpty()) return "no_windows"
        val starts = timed.map { it.startMinutes!! }
        for (i in 1 until starts.size) {
            if (starts[i] < starts[i - 1] + MIN_GAP_MINUTES) return "gap"
        }
        return null
    }

    fun validatedOrDefault(): MealCatalog = if (isValid) this else Default

    /** Enabled defs with a start time, in catalog order. */
    fun scheduleWindows(): List<MealDef> =
        meals.filter { it.enabled && it.startMinutes != null }

    fun mealIdAt(time: LocalTime): String {
        val windows = scheduleWindows()
        if (windows.isEmpty()) return Default.mealIdAt(time)
        val minutes = time.hour * 60 + time.minute
        val firstStart = windows.first().startMinutes!!
        // Overnight / pre-first window belongs to the last window (legacy snack).
        if (minutes < firstStart) return windows.last().id
        var current = windows.first().id
        for (w in windows) {
            val start = w.startMinutes!!
            if (minutes >= start) current = w.id else break
        }
        return current
    }

    fun mealTypeAt(time: LocalTime): MealType =
        MealType.fromId(mealIdAt(time)) ?: MealType.SNACK

    fun enabledForPicker(): List<MealDef> = meals.filter { it.enabled }

    fun def(id: String): MealDef? = meals.firstOrNull { it.id == id }

    fun displayOrderIds(): List<String> = meals.map { it.id }

    fun toLegacySchedule(): MealSchedule {
        fun start(id: String, fallback: Int): Int =
            def(id)?.startMinutes ?: fallback
        return MealSchedule(
            breakfastStartMinutes = start(MealType.BREAKFAST.id, MealSchedule.DEFAULT_BREAKFAST_START),
            lunchStartMinutes = start(MealType.LUNCH.id, MealSchedule.DEFAULT_LUNCH_START),
            dinnerStartMinutes = start(MealType.DINNER.id, MealSchedule.DEFAULT_DINNER_START),
            snackStartMinutes = start(MealType.SNACK.id, MealSchedule.DEFAULT_SNACK_START),
        ).validatedOrDefault()
    }

    fun withLabel(id: String, label: String): MealCatalog =
        copy(meals = meals.map { if (it.id == id) it.copy(label = label.trim().take(MAX_LABEL)) else it })

    fun withStart(id: String, startMinutes: Int): MealCatalog =
        copy(meals = meals.map { if (it.id == id) it.copy(startMinutes = startMinutes) else it })

    fun withEnabled(id: String, enabled: Boolean): MealCatalog =
        copy(meals = meals.map { if (it.id == id) it.copy(enabled = enabled) else it })

    fun without(id: String): MealCatalog = copy(meals = meals.filter { it.id != id })

    fun reordered(ids: List<String>): MealCatalog {
        val byId = meals.associateBy { it.id }
        val next = ids.mapNotNull { byId[it] } + meals.filter { it.id !in ids.toSet() }
        return copy(meals = next)
    }

    fun addCustom(label: String, startMinutes: Int): MealCatalog {
        if (meals.size >= MAX_MEALS) return this
        val def = MealDef(
            id = newCustomId(meals.map { it.id }.toSet()),
            label = label.trim().take(MAX_LABEL),
            startMinutes = startMinutes,
            enabled = true,
        )
        val timed = meals.mapIndexed { i, m -> i to m }
            .filter { it.second.startMinutes != null }
        val insertAt = timed.indexOfLast { (it.second.startMinutes ?: 0) < startMinutes }
            .let { if (it < 0) timed.firstOrNull()?.first ?: meals.size else timed[it].first + 1 }
        val next = meals.toMutableList()
        next.add(insertAt.coerceIn(0, next.size), def)
        return copy(meals = next)
    }

    fun suggestedStartForNew(): Int {
        val timed = scheduleWindows().mapNotNull { it.startMinutes }.sorted()
        if (timed.isEmpty()) return MealSchedule.DEFAULT_LUNCH_START
        if (timed.size == 1) return (timed[0] + 60).coerceAtMost(MealSchedule.MINUTES_PER_DAY - 1)
        var bestGap = 0
        var mid = (timed.last() + 60).coerceAtMost(MealSchedule.MINUTES_PER_DAY - 1)
        for (i in 0 until timed.lastIndex) {
            val gap = timed[i + 1] - timed[i]
            if (gap > bestGap) {
                bestGap = gap
                mid = timed[i] + gap / 2
            }
        }
        return mid
    }

    companion object {
        const val MAX_MEALS = 8
        const val MAX_LABEL = 24
        const val MIN_GAP_MINUTES = 15
        const val CUSTOM_PREFIX = "c_"
        val CUSTOM_ID_REGEX = Regex("^c_[0-9a-f]{4,16}$")

        val Default = MealCatalog(defaultMeals())

        fun fromLegacySchedule(schedule: MealSchedule): MealCatalog {
            val s = schedule.validatedOrDefault()
            return MealCatalog(
                meals = listOf(
                    MealDef(MealType.BREAKFAST.id, startMinutes = s.breakfastStartMinutes),
                    MealDef(MealType.LUNCH.id, startMinutes = s.lunchStartMinutes),
                    MealDef(MealType.DINNER.id, startMinutes = s.dinnerStartMinutes),
                    MealDef(MealType.SNACK.id, startMinutes = s.snackStartMinutes),
                    MealDef(MealType.OTHER.id, startMinutes = null, enabled = false),
                ),
            )
        }

        fun newCustomId(existing: Set<String> = emptySet()): String {
            repeat(16) {
                val hex = Random.nextBytes(4).joinToString("") { b ->
                    val v = b.toInt() and 0xff
                    "%02x".format(v)
                }
                val id = CUSTOM_PREFIX + hex
                if (id !in existing) return id
            }
            return CUSTOM_PREFIX + System.nanoTime().toString(16).takeLast(8)
        }

        private fun defaultMeals(): List<MealDef> = fromLegacySchedule(MealSchedule.Default).meals
    }
}

object CurrentMealCatalog {
    @Volatile
    var value: MealCatalog = MealCatalog.Default
}
