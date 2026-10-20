package app.chompass.models

/**
 * Pure macro rebalance shared by every write path that persists a calorie
 * target (Settings calorie edit, AI goal snapshots, day-type/phase rescales;
 * PWA mirror in `web/app/src/lib/chompass-core/macro-balance.js`).
 *
 * Invariants:
 *  - locked macros are never changed (at most two can lock, so one stays free);
 *  - STANDARD keeps unlocked protein and fat at their input grams and lets
 *    carbs absorb the remainder; when the target is too low, fat yields first
 *    but never below [fatFloorG] while protein can still give way, and protein
 *    only shrinks when the floor would otherwise break (or is already locked);
 *  - KETO keeps unlocked protein and carbs and lets fat fill the target,
 *    floored at [fatFloorG]; when the floor leaves the target over, protein
 *    yields, then carbs;
 *  - when everything relevant is locked and the target cannot fit, the locked
 *    values are returned unchanged — the caller stores the requested calories
 *    and the UI shows the overshoot.
 *
 * All arithmetic is integer division on non-negative values, so the PWA
 * `Math.trunc` mirror matches bit-for-bit. 4/4/9 kcal per gram.
 */
object MacroBalance {
    enum class Mode { STANDARD, KETO }

    data class Grams(val protein: Int, val carbs: Int, val fat: Int) {
        val kcal: Int get() = protein * 4 + carbs * 4 + fat * 9
    }

    fun rebalance(
        calories: Int,
        protein: Int,
        carbs: Int,
        fat: Int,
        mode: Mode,
        locked: Set<AutoBalanceMacro>,
        fatFloorG: Int,
    ): Grams = when (mode) {
        Mode.STANDARD -> rebalanceStandard(calories, protein, carbs, fat, locked, fatFloorG)
        Mode.KETO -> rebalanceKeto(calories, protein, carbs, fat, locked, fatFloorG)
    }

    private fun rebalanceStandard(
        calories: Int,
        protein: Int,
        carbs: Int,
        fat: Int,
        locked: Set<AutoBalanceMacro>,
        floor: Int,
    ): Grams {
        val t = maxOf(0, calories)
        var p = maxOf(0, protein)
        val c = maxOf(0, carbs)
        var f = maxOf(0, fat)
        val proteinLocked = AutoBalanceMacro.PROTEIN in locked
        val carbsLocked = AutoBalanceMacro.CARBS in locked
        val fatLocked = AutoBalanceMacro.FAT in locked
        if (!fatLocked) f = maxOf(f, floor)
        if (t >= 4 * p + 9 * f) {
            return Grams(p, if (carbsLocked) c else maxOf(0, (t - 4 * p - 9 * f) / 4), f)
        }

        if (proteinLocked) {
            if (!fatLocked) f = maxOf(0, (t - 4 * p) / 9) // floor may break
        } else if (t >= 4 * p + 9 * floor) {
            // Protein can stay; reduce fat to fit, never below the floor.
            if (!fatLocked) f = minOf(f, (t - 4 * p) / 9)
        } else {
            // The floor cannot fit with this protein; reduce protein to preserve it.
            p = maxOf(0, (t - 9 * floor) / 4)
            if (t < 4 * p + 9 * floor) {
                if (!fatLocked) f = maxOf(0, (t - 4 * p) / 9) // last resort: break the floor
            } else if (!fatLocked) {
                f = floor
            }
        }
        // Last resort if a locked fat (or rounding) left the target over: protein yields.
        if (t < 4 * p + 9 * f && !proteinLocked) p = maxOf(0, (t - 9 * f) / 4)
        if (t < 4 * p + 9 * f && !fatLocked) f = maxOf(0, (t - 4 * p) / 9)
        val finalCarbs = if (carbsLocked) c else maxOf(0, (t - 4 * p - 9 * f) / 4)
        return Grams(p, finalCarbs, f)
    }

    private fun rebalanceKeto(
        calories: Int,
        protein: Int,
        carbs: Int,
        fat: Int,
        locked: Set<AutoBalanceMacro>,
        floor: Int,
    ): Grams {
        val t = maxOf(0, calories)
        var p = maxOf(0, protein)
        val c = maxOf(0, carbs)
        var f = maxOf(0, fat)
        val proteinLocked = AutoBalanceMacro.PROTEIN in locked
        val carbsLocked = AutoBalanceMacro.CARBS in locked
        val fatLocked = AutoBalanceMacro.FAT in locked
        if (!fatLocked) f = maxOf(maxOf(0, (t - 4 * p - 4 * c) / 9), floor)
        if (t - 4 * p - 4 * c - 9 * f >= 0) return Grams(p, c, f)
        if (!proteinLocked) p = minOf(p, maxOf(0, (t - 4 * c - 9 * floor) / 4))
        if (!fatLocked) f = maxOf(0, (t - 4 * p - 4 * c) / 9)
        if (t - 4 * p - 4 * c - 9 * f < 0 && !proteinLocked) p = maxOf(0, (t - 4 * c - 9 * f) / 4)
        if (t - 4 * p - 4 * c - 9 * f < 0 && !carbsLocked) {
            val finalCarbs = maxOf(0, (t - 4 * p - 9 * f) / 4)
            return Grams(p, finalCarbs, f)
        }
        return Grams(p, c, f)
    }
}
