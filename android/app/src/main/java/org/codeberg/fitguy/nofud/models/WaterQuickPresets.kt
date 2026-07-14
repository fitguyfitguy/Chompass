package org.codeberg.fitguy.nofud.models

/**
 * User-configurable snap points for the Add food water slider. Always stored in ml;
 * imperial locales display fluid ounces in the UI.
 */
data class WaterQuickPresets(
    val amountsMl: List<Int> = DEFAULT_AMOUNTS_ML,
) {
    val isValid: Boolean
        get() = amountsMl.size in MIN_COUNT..MAX_COUNT &&
            amountsMl.all { it in MIN_ML..MAX_ML } &&
            amountsMl.distinct().size == amountsMl.size &&
            amountsMl == amountsMl.sorted()

    fun validatedOrDefault(): WaterQuickPresets = if (isValid) this else Default

    companion object {
        val DEFAULT_AMOUNTS_ML = listOf(250, 500, 750)
        const val MIN_COUNT = 2
        const val MAX_COUNT = 5
        const val MIN_ML = 50
        const val MAX_ML = 2_000

        val Default = WaterQuickPresets()

        fun fromStorage(raw: String?): WaterQuickPresets {
            if (raw.isNullOrBlank()) return Default
            val parsed = raw.split(',').mapNotNull { it.trim().toIntOrNull() }
            return WaterQuickPresets(parsed).validatedOrDefault()
        }

        fun toStorage(presets: WaterQuickPresets): String =
            presets.validatedOrDefault().amountsMl.joinToString(",")
    }
}

object WaterAmountFormat {
    private const val ML_PER_FL_OZ = 29.5735

    fun mlFromFlOz(flOz: Int): Int =
        (flOz * ML_PER_FL_OZ).toInt().coerceIn(WaterQuickPresets.MIN_ML, 10_000)

    fun flOzFromMl(ml: Int): Int =
        (ml / ML_PER_FL_OZ).toInt().coerceAtLeast(1)
}
