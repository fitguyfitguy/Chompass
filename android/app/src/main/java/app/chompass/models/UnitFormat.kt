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

    /**
     * Storage grid for weight entries. Must stay fine enough that a 0.1 lbs
     * wheel pick round-trips through kg storage and the one-decimal lbs display
     * (Codeberg #82/#73): a tenth of a lb is 0.0454 kg, so on this grid the
     * kg→lbs display error stays under 0.011 lbs — below the 0.05 lbs half-step
     * of "%.1f". The old 0.1 kg grid snapped 275.0 lbs to 124.7 kg → 274.9.
     * Metric picks sit on the 0.1 kg wheel, so they pass through unchanged.
     */
    fun roundKgToHundredths(kg: Double): Double = (kg * 100.0).roundToInt() / 100.0

    fun cmToInches(cm: Double): Double = cm / CM_PER_INCH

    fun inchesToCm(inches: Double): Double = inches * CM_PER_INCH

    /**
     * Whole-unit height conversions. These round rather than truncate in both
     * directions, which is what keeps a 5'7" pick from snapping back to 5'6"
     * after the 170 cm round trip.
     */
    fun cmToInchesRounded(cm: Int): Int = cmToInches(cm.toDouble()).roundToInt()

    fun inchesToCmRounded(inches: Int): Int = inchesToCm(inches.toDouble()).roundToInt()

    /** "72.4 kg" or "159.6 lbs" — uses the app display locale. */
    fun weight(kg: Double, useMetric: Boolean, locale: Locale = Locale.getDefault()): String =
        if (useMetric) String.format(locale, "%.1f kg", kg)
        else String.format(locale, "%.1f lbs", kgToLbs(kg))

    /** "18.3%" from an already-scaled percentage value. */
    fun percent(value: Double, locale: Locale = Locale.getDefault()): String =
        String.format(locale, "%.1f%%", value)

    /**
     * Signed one-decimal delta with no unit: "+1.2", "-0.8", "0.0".
     *
     * Values under half a display step are clamped to zero so a rounded "0.0"
     * is never shown with a misleading "+" or "-" sign. Callers append their own
     * unit, which is what keeps "+1.2 kg" and "+1.2%" from needing two helpers.
     */
    fun signedDelta(value: Double, locale: Locale = Locale.getDefault()): String {
        val rounded = if (abs(value) < 0.05) 0.0 else value
        val sign = if (rounded > 0) "+" else ""
        return String.format(locale, "%s%.1f", sign, rounded)
    }
}
