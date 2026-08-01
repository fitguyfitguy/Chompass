package app.chompass.models

import kotlinx.serialization.Serializable
import app.chompass.services.KetoCarbRecommendationService
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import kotlin.math.roundToInt

@Serializable
data class UserProfile(
    val name: String? = null,
    val gender: Gender = Gender.MALE,
    @Serializable(with = InstantSerializer::class)
    val birthday: Instant = defaultBirthday(),
    val heightCm: Double = 175.0,
    val weightKg: Double = 70.0,
    val activityLevel: ActivityLevel = ActivityLevel.MODERATE,
    val goal: WeightGoal = WeightGoal.MAINTAIN,
    val dietMode: DietMode = DietMode.STANDARD,
    val ketoCarbMode: KetoCarbMode = KetoCarbMode.ADAPTIVE,
    val ketoCarbManualTarget: Int? = null,
    val bodyFatPercentage: Double? = null,
    /** Display-only goal — explicitly NOT used in BMR/TDEE/macro math. */
    val goalBodyFatPercentage: Double? = null,
    /** Nullable so older serialized profiles decode as null = treat as true. When false,
     *  BMR falls back to Mifflin-St Jeor even if bodyFatPercentage is set. */
    val useBodyFatInBMR: Boolean? = null,
    val weeklyChangeKg: Double? = null,
    val goalWeightKg: Double? = null,
    val customCalories: Int? = null,
    val customProtein: Int? = null,
    val customFat: Int? = null,
    val customCarbs: Int? = null,
    /** When [proteinTargetMode] uses g/kg, this rate is the source of truth for protein grams. */
    val proteinGramsPerKg: Double? = null,
    val proteinTargetMode: ProteinTargetMode = ProteinTargetMode.Default,
    val autoBalanceMacro: AutoBalanceMacro? = null,
    /** User lock over the calorie target. When locked, editing one macro holds this total fixed
     *  (the other unlocked macros absorb the change) instead of letting calories float to the new
     *  sum. Defaults false for back-compat. Cleared by Recalculate and the Adaptive auto-run. */
    val caloriesLocked: Boolean = false,
    /** User locks over individual macros — at most two at once, so at least one stays free to
     *  balance. A locked macro is never auto-adjusted during a rebalance. Defaults empty for
     *  back-compat. Cleared by Recalculate and the Adaptive auto-run. */
    val lockedMacros: Set<AutoBalanceMacro> = emptySet()
) {
    val displayName: String get() = name?.takeIf { it.isNotEmpty() } ?: "User"

    val initials: String get() {
        val parts = displayName.split(" ")
        return if (parts.size >= 2) {
            (parts[0].take(1) + parts[1].take(1)).uppercase()
        } else {
            displayName.take(1).uppercase()
        }
    }

    val age: Int get() {
        val birthDate = birthday.atZone(ZoneId.systemDefault()).toLocalDate()
        return Period.between(birthDate, LocalDate.now()).years.coerceAtLeast(0)
    }

    /** True when BMR uses Katch-McArdle: whenever a body fat % is set it is used directly.
     *  No manual override — if body fat is unset, BMR falls back to Mifflin-St Jeor. */
    val usesBodyFatForBMR: Boolean get() = bodyFatPercentage != null

    val bmr: Double get() = if (usesBodyFatForBMR) {
        // Katch-McArdle
        370.0 + 21.6 * (1.0 - bodyFatPercentage!!) * weightKg
    } else {
        // Mifflin-St Jeor
        val base = 10.0 * weightKg + 6.25 * heightCm - 5.0 * age - 161.0
        if (gender == Gender.MALE) base + 166.0 else base
    }

    val tdee: Double get() = bmr * activityLevel.multiplier

    val calorieAdjustment: Int get() = when (goal) {
        WeightGoal.MAINTAIN -> 0
        WeightGoal.LOSE -> {
            val rate = weeklyChangeKg ?: 0.5
            -NutritionConstants.dailyCalorieAdjustmentForWeeklyRateKg(rate)
        }
        WeightGoal.GAIN -> {
            val rate = weeklyChangeKg ?: 0.5
            NutritionConstants.dailyCalorieAdjustmentForWeeklyRateKg(rate)
        }
    }

    val dailyCalories: Int get() = tdee.toInt() + calorieAdjustment

    private val standardProteinGoal: Int get() {
        // +0.2 g/kg during cutting phase to preserve lean mass (Helms et al 2014).
        val cuttingBoost = if (goal == WeightGoal.LOSE) 0.2 else 0.0
        val multiplier = activityLevel.proteinRequirementPerKg(bodyFatPercentage, cuttingBoost)
        return (multiplier * proteinBasisWeightKg).toInt()
    }

    private val proteinBasisWeightKg: Double get() =
        bodyFatPercentage?.let { weightKg * (1.0 - it).coerceIn(0.05, 1.0) } ?: weightKg

    /** Body-mass basis for [proteinGramsPerKg] when the user pins protein as g/kg. */
    val proteinTargetBasisKg: Double get() = when (proteinTargetMode) {
        ProteinTargetMode.GRAMS_PER_DAY -> weightKg
        ProteinTargetMode.G_PER_KG_TOTAL -> weightKg
        ProteinTargetMode.G_PER_KG_LBM -> proteinBasisWeightKg
    }.coerceAtLeast(0.1)

    private val standardFatGoal: Int get() = (0.6 * weightKg).toInt()

    private val ketoAdaptiveCarbTarget: Int
        get() = KetoCarbRecommendationService.recommendNetCarbs(this)

    val ketoActiveCarbTarget: Int get() = when (ketoCarbMode) {
        KetoCarbMode.ADAPTIVE -> ketoAdaptiveCarbTarget
        KetoCarbMode.MANUAL -> KetoCarbRecommendationService.clampManualNetCarbs(ketoCarbManualTarget ?: ketoAdaptiveCarbTarget)
    }

    val proteinGoal: Int get() = when (dietMode) {
        DietMode.STANDARD -> standardProteinGoal
        DietMode.KETO -> {
            val ketoProteinFloor = (1.6 * proteinBasisWeightKg).toInt().coerceAtLeast(60)
            maxOf(standardProteinGoal, ketoProteinFloor)
        }
    }

    val carbsGoal: Int get() = when (dietMode) {
        DietMode.STANDARD -> maxOf(0, (dailyCalories - proteinGoal * 4 - fatGoal * 9) / 4)
        DietMode.KETO -> ketoActiveCarbTarget
    }

    val fatGoal: Int get() = when (dietMode) {
        DietMode.STANDARD -> standardFatGoal
        DietMode.KETO -> {
            val minFat = maxOf(standardFatGoal, 45)
            val carbs = carbsGoal
            val caloriesAfterCarbs = maxOf(0, dailyCalories - carbs * 4)
            val maxProteinForFloor = maxOf(0, (caloriesAfterCarbs - minFat * 9) / 4)
            val adjustedProtein = minOf(proteinGoal, maxProteinForFloor.takeIf { it > 0 } ?: proteinGoal)
            val remainingAfterProtein = maxOf(0, caloriesAfterCarbs - adjustedProtein * 4)
            maxOf(minFat, remainingAfterProtein / 9)
        }
    }

    val effectiveCalories: Int get() = customCalories ?: dailyCalories

    /** PAL-implied average active burn: TDEE minus BMR. Used when Health Connect is unavailable. */
    val estimatedDailyActiveCalories: Int
        get() = (tdee - bmr).roundToInt().coerceAtLeast(0)

    /** BMR + goal adjustment only — activity stripped from the stored effective goal. */
    fun sedentaryCalorieBudget(effectiveCalories: Int = this.effectiveCalories): Int =
        (effectiveCalories - estimatedDailyActiveCalories).coerceAtLeast(0)

    fun isPinned(macro: AutoBalanceMacro): Boolean = customValue(macro) != null

    val pinnedCount: Int get() = AutoBalanceMacro.values().count { isPinned(it) }

    val effectiveProtein: Int get() {
        if (proteinTargetMode.usesRate) {
            proteinGramsPerKg?.let { rate ->
                return (rate * proteinTargetBasisKg).roundToInt().coerceAtLeast(0)
            }
        }
        return customProtein ?: autoMacroValue(AutoBalanceMacro.PROTEIN)
    }
    val effectiveCarbs: Int get() = customCarbs ?: autoMacroValue(AutoBalanceMacro.CARBS)
    val effectiveFat: Int get() = customFat ?: autoMacroValue(AutoBalanceMacro.FAT)

    private fun customValue(macro: AutoBalanceMacro): Int? = when (macro) {
        AutoBalanceMacro.PROTEIN -> when {
            proteinTargetMode.usesRate && proteinGramsPerKg != null ->
                (proteinGramsPerKg!! * proteinTargetBasisKg).roundToInt().coerceAtLeast(0)
            else -> customProtein
        }
        AutoBalanceMacro.CARBS -> customCarbs
        AutoBalanceMacro.FAT -> customFat
    }

    private fun formulaValue(macro: AutoBalanceMacro): Int = when (macro) {
        AutoBalanceMacro.PROTEIN -> proteinGoal
        AutoBalanceMacro.CARBS -> carbsGoal
        AutoBalanceMacro.FAT -> fatGoal
    }

    private fun autoMacroValue(macro: AutoBalanceMacro): Int {
        val pinnedKcal = AutoBalanceMacro.values().sumOf { m ->
            customValue(m)?.let { it * m.kcalPerGram } ?: 0
        }
        val remaining = maxOf(0, effectiveCalories - pinnedKcal)
        val autoMacros = AutoBalanceMacro.values().filter { !isPinned(it) }
        if (macro !in autoMacros) return 0

        if (autoMacros.size == 1) {
            return remaining / macro.kcalPerGram
        }

        val totalFormulaKcal = autoMacros.sumOf { formulaValue(it) * it.kcalPerGram }
        if (totalFormulaKcal <= 0) return formulaValue(macro)

        val mySharedKcal = remaining * formulaValue(macro) * macro.kcalPerGram / totalFormulaKcal
        return mySharedKcal / macro.kcalPerGram
    }

    /**
     * Stable fingerprint of the inputs that feed goal calculation. When this differs from the
     * value captured at the last Recalculate, the UI nudges the user to recalculate. Editing a
     * profile input no longer recomputes goals automatically, so this is how we surface "your
     * profile changed — your goals may be stale." Must stay in sync with the fields the AI/formula
     * actually consume (see [dailyCalories], [bmr], [proteinGoal]).
     */
    val goalInputSignature: String get() = listOf(
        gender, birthday.epochSecond, heightCm, weightKg, activityLevel, goal, dietMode,
        ketoCarbMode, ketoCarbManualTarget,
        weeklyChangeKg, goalWeightKg, bodyFatPercentage, useBodyFatInBMR
    ).joinToString("|")

    // -- User locks (a control layer on top of the stored custom* snapshot) -----------------

    fun isMacroLocked(macro: AutoBalanceMacro): Boolean = macro in lockedMacros

    /** Toggle a macro lock. At most two macros may be locked — at least one stays free to balance.
     *  Returns the original (unchanged) profile when trying to lock a third macro. */
    fun toggledMacroLock(macro: AutoBalanceMacro): UserProfile = when {
        macro in lockedMacros -> copy(lockedMacros = lockedMacros - macro)
        lockedMacros.size >= 2 -> this
        else -> copy(lockedMacros = lockedMacros + macro)
    }

    fun toggledCaloriesLock(): UserProfile = copy(caloriesLocked = !caloriesLocked)

    fun withLocksCleared(): UserProfile = copy(caloriesLocked = false, lockedMacros = emptySet())

    private fun effectiveGrams(macro: AutoBalanceMacro): Int = when (macro) {
        AutoBalanceMacro.PROTEIN -> effectiveProtein
        AutoBalanceMacro.CARBS -> effectiveCarbs
        AutoBalanceMacro.FAT -> effectiveFat
    }

    /** Freeze current effective values into the stored custom* fields so edits are explicit
     *  (no hidden auto-balance). Snapshots all three macros before writing, so writing one
     *  doesn't shift another's auto value mid-materialization. */
    private fun materialized(): UserProfile {
        val base = copy(
            customCalories = effectiveCalories,
            customCarbs = effectiveCarbs,
            customFat = effectiveFat,
        )
        return if (proteinTargetMode.usesRate && proteinGramsPerKg != null) {
            // Keep g/kg as the source of truth so weight/BF% changes still scale protein.
            base.copy(customProtein = null)
        } else {
            base.copy(customProtein = effectiveProtein)
        }
    }

    /** Distribute [targetKcal] over [macros], split proportional to each macro's current kcal
     *  (falling back to formula weights when current is zero). The last macro absorbs the rounding
     *  remainder. Returns gram values keyed by macro. */
    private fun distribute(targetKcal: Int, macros: List<AutoBalanceMacro>): Map<AutoBalanceMacro, Int> {
        if (macros.isEmpty()) return emptyMap()
        val target = maxOf(0, targetKcal)
        val weights = macros.map { m ->
            val current = (effectiveGrams(m) * m.kcalPerGram).toDouble()
            if (current > 0) current else maxOf(1, formulaValue(m) * m.kcalPerGram).toDouble()
        }
        val totalWeight = weights.sum()
        val result = mutableMapOf<AutoBalanceMacro, Int>()
        var assignedKcal = 0
        macros.forEachIndexed { index, m ->
            if (index == macros.lastIndex) {
                val kcal = maxOf(0, target - assignedKcal)
                result[m] = Math.round(kcal.toDouble() / m.kcalPerGram).toInt()
            } else {
                val share = if (totalWeight > 0) target * weights[index] / totalWeight
                            else target.toDouble() / macros.size
                val grams = Math.round(share / m.kcalPerGram).toInt()
                result[m] = grams
                assignedKcal += grams * m.kcalPerGram
            }
        }
        return result
    }

    private fun withMacroGrams(updates: Map<AutoBalanceMacro, Int>): UserProfile {
        val next = copy(
            customProtein = updates[AutoBalanceMacro.PROTEIN]?.let { maxOf(0, it) } ?: customProtein,
            customCarbs = updates[AutoBalanceMacro.CARBS]?.let { maxOf(0, it) } ?: customCarbs,
            customFat = updates[AutoBalanceMacro.FAT]?.let { maxOf(0, it) } ?: customFat
        )
        val proteinGrams = updates[AutoBalanceMacro.PROTEIN] ?: return next
        return if (next.proteinTargetMode.usesRate) {
            next.copy(
                customProtein = null,
                proteinGramsPerKg = proteinGrams / next.proteinTargetBasisKg,
            )
        } else {
            next.copy(proteinGramsPerKg = null)
        }
    }

    /** Switch protein pin mode, converting the current effective grams into the new representation. */
    fun withProteinTargetMode(mode: ProteinTargetMode): UserProfile {
        val grams = effectiveProtein
        return when (mode) {
            ProteinTargetMode.GRAMS_PER_DAY -> copy(
                proteinTargetMode = mode,
                proteinGramsPerKg = null,
                customProtein = grams,
            )
            ProteinTargetMode.G_PER_KG_TOTAL,
            ProteinTargetMode.G_PER_KG_LBM -> {
                val basis = when (mode) {
                    ProteinTargetMode.G_PER_KG_LBM ->
                        bodyFatPercentage?.let { weightKg * (1.0 - it).coerceIn(0.05, 1.0) } ?: weightKg
                    else -> weightKg
                }.coerceAtLeast(0.1)
                copy(
                    proteinTargetMode = mode,
                    proteinGramsPerKg = grams / basis,
                    customProtein = null,
                )
            }
        }
    }

    /** Set an explicit g/kg protein rate (pins protein in rate mode). */
    fun withProteinGramsPerKg(rate: Double): UserProfile = copy(
        proteinTargetMode = if (proteinTargetMode.usesRate) proteinTargetMode else ProteinTargetMode.G_PER_KG_TOTAL,
        proteinGramsPerKg = rate.coerceAtLeast(0.0),
        customProtein = null,
    )

    /** User edited the calorie target directly. Hold any locked macros fixed and rescale the
     *  unlocked macros to fill the new total. (Max two macros lock, so one always absorbs.) */
    fun applyCaloriesEdit(newCalories: Int): UserProfile {
        val base = materialized()
        val target = maxOf(0, newCalories)
        val lockedKcal = AutoBalanceMacro.values()
            .filter { base.isMacroLocked(it) }
            .sumOf { base.effectiveGrams(it) * it.kcalPerGram }
        val unlocked = AutoBalanceMacro.values().filter { !base.isMacroLocked(it) }
        return base
            .withMacroGrams(base.distribute(target - lockedKcal, unlocked))
            .copy(customCalories = target)
    }

    /** User edited one macro. When calories is locked, hold the calorie total fixed and let the
     *  other unlocked macros absorb the change — returns null (no change) if neither other macro
     *  can absorb (both locked). When calories is unlocked, the macro takes the new value and
     *  calories floats to the new sum. */
    fun applyMacroEdit(macro: AutoBalanceMacro, newGrams: Int): UserProfile? {
        val base = materialized()
        val requested = maxOf(0, newGrams)
        if (caloriesLocked) {
            val absorbers = AutoBalanceMacro.values().filter { it != macro && !base.isMacroLocked(it) }
            if (absorbers.isEmpty()) return null
            val otherLockedKcal = AutoBalanceMacro.values()
                .filter { it != macro && base.isMacroLocked(it) }
                .sumOf { base.effectiveGrams(it) * it.kcalPerGram }
            val available = maxOf(0, base.effectiveCalories - otherLockedKcal)
            val macroKcal = minOf(requested * macro.kcalPerGram, available)
            return base
                .withMacroGrams(mapOf(macro to macroKcal / macro.kcalPerGram))
                .withMacroGrams(base.distribute(available - macroKcal, absorbers))
            // customCalories stays put — calories is locked.
        } else {
            val edited = base.withMacroGrams(mapOf(macro to requested))
            val newTotal = AutoBalanceMacro.values().sumOf { edited.effectiveGrams(it) * it.kcalPerGram }
            return edited.copy(customCalories = newTotal)
        }
    }

    /** Release the calories lock and reset the total to the sum of the current macros — the honest
     *  "auto" value when calories isn't pinned. The calories-row "Reset to Auto-balance" action,
     *  mirroring the per-macro reset. */
    fun resetCaloriesToBalance(): UserProfile {
        val base = materialized()
        val total = AutoBalanceMacro.values().sumOf { base.effectiveGrams(it) * it.kcalPerGram }
        return base.copy(caloriesLocked = false, customCalories = total)
    }

    /** Release a macro's lock and reset it to the balancing remainder: it absorbs whatever calories
     *  the other two macros leave, so the macros sum back to the calorie total. This is the "Reset
     *  to Auto-balance" action — turns the lock off and re-derives the value. */
    fun resetMacroToBalance(macro: AutoBalanceMacro): UserProfile {
        val base = materialized()
        val othersKcal = AutoBalanceMacro.values()
            .filter { it != macro }
            .sumOf { base.effectiveGrams(it) * it.kcalPerGram }
        val grams = maxOf(0, base.effectiveCalories - othersKcal) / macro.kcalPerGram
        val unlocked = base
            .copy(lockedMacros = lockedMacros - macro)
            .withMacroGrams(mapOf(macro to grams))
        return if (macro == AutoBalanceMacro.PROTEIN) {
            unlocked.copy(
                proteinTargetMode = ProteinTargetMode.Default,
                proteinGramsPerKg = null,
            )
        } else {
            unlocked
        }
    }

    /**
     * Returns a copy with calories recomputed from formulas, all three macros reset to auto, and
     * every user lock cleared — a fresh calculation starts unlocked.
     */
    fun recalculatedFromFormulas(): UserProfile = copy(
        customCalories = dailyCalories,
        customProtein = null,
        customFat = null,
        customCarbs = null,
        proteinGramsPerKg = null,
        proteinTargetMode = ProteinTargetMode.Default,
        autoBalanceMacro = null,
        caloriesLocked = false,
        lockedMacros = emptySet()
    )

    companion object {
        private fun defaultBirthday(): Instant =
            LocalDate.now().minusYears(25).atStartOfDay(ZoneId.systemDefault()).toInstant()

        val Default = UserProfile()
    }
}
