package app.chompass.models

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Single source of truth for imperial/metric conversion and the display strings
 * built from them. Before this existed the ratios were re-typed at ~28 call
 * sites, so a correction had to be applied 28 times to stay consistent.
 *
 * Formatters here cover the spellings that are genuinely shared. Where a call
 * site needs different wording or precision — the AI prompt builders in
 * `services/ai` say "lb" and "%+.2f lb/week", the UI says "lbs" — it converts
 * with [kgToLbs] and formats locally rather than bending a shared formatter.
 */
object UnitFormat {
    const val LBS_PER_KG = 2.20462
    const val CM_PER_INCH = 2.54

    fun kgToLbs(kg: Double): Double = kg * LBS_PER_KG

    fun lbsToKg(lbs: Double): Double = lbs / LBS_PER_KG

    fun cmToInches(cm: Double): Double = cm / CM_PER_INCH

    fun inchesToCm(inches: Double): Double = inches * CM_PER_INCH

    /**
     * Whole-unit height conversions. These round rather than truncate in both
     * directions, which is what keeps a 5'7" pick from snapping back to 5'6"
     * after the 170 cm round trip.
     */
    fun cmToInchesRounded(cm: Int): Int = cmToInches(cm.toDouble()).roundToInt()

    fun inchesToCmRounded(inches: Int): Int = inchesToCm(inches.toDouble()).roundToInt()

    /** "72.4 kg" or "159.6 lbs". */
    fun weight(kg: Double, useMetric: Boolean): String =
        if (useMetric) String.format(Locale.US, "%.1f kg", kg)
        else String.format(Locale.US, "%.1f lbs", kgToLbs(kg))

    /** "18.3%" from an already-scaled percentage value. */
    fun percent(value: Double): String = String.format(Locale.US, "%.1f%%", value)

    /**
     * Signed one-decimal delta with no unit: "+1.2", "-0.8", "0.0".
     *
     * Values under half a display step are clamped to zero so a rounded "0.0"
     * is never shown with a misleading "+" or "-" sign. Callers append their own
     * unit, which is what keeps "+1.2 kg" and "+1.2%" from needing two helpers.
     */
    fun signedDelta(value: Double): String {
        val rounded = if (abs(value) < 0.05) 0.0 else value
        val sign = if (rounded > 0) "+" else ""
        return String.format(Locale.US, "%s%.1f", sign, rounded)
    }
}
