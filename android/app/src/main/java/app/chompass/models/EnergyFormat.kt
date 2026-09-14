package app.chompass.models

import kotlin.math.roundToInt

enum class EnergyUnit {
    KCAL, KJ;

    companion object {
        fun fromStorage(raw: String?): EnergyUnit =
            if (raw.equals("kj", ignoreCase = true)) KJ else KCAL

        fun toStorage(unit: EnergyUnit): String = if (unit == KJ) "kj" else "kcal"
    }
}

/**
 * Display-only kcal↔kJ conversion. Storage and formulas stay in kcal.
 * Factor matches [GroundingValidator]'s inbound kJ-as-kcal detection.
 */
object EnergyFormat {
    const val KJ_PER_KCAL = 4.184 // thermochemical

    fun kcalToKj(kcal: Int): Int = (kcal * KJ_PER_KCAL).roundToInt()

    fun kjToKcal(kj: Int): Int = (kj / KJ_PER_KCAL).roundToInt()

    fun quantity(kcal: Int, unit: EnergyUnit): Int =
        if (unit == EnergyUnit.KJ) kcalToKj(kcal) else kcal

    fun toKcal(display: Int, unit: EnergyUnit): Int =
        if (unit == EnergyUnit.KJ) kjToKcal(display) else display

    /** Wheel step in display units. kcal steps stay; kJ uses round tens so the wheel is usable. */
    fun wheelStep(kcalStep: Int, unit: EnergyUnit): Int = when {
        unit == EnergyUnit.KCAL -> kcalStep
        kcalStep <= 2 -> 10
        kcalStep <= 10 -> 50
        else -> 200
    }
}
