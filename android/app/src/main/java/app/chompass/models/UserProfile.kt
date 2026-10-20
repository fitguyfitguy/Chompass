package app.chompass.models

import kotlinx.serialization.Serializable
import app.chompass.services.KetoCarbRecommendationService
import app.chompass.services.ai.GoalCalculation
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import kotlin.math.abs
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
    /** When the body-fat reading was taken. Null = legacy reading, treated as fresh so existing
     *  users' numbers do not change on update. Dated readings older than
     *  [NutritionConstants.BODY_FAT_RECENCY_DAYS] fall back to Mifflin-St Jeor. */
    @Serializable(with = InstantSerializer::class)
    val bodyFatUpdatedAt: Instant? = null,
    /** Display-only goal — explicitly NOT used in BMR/TDEE/macro math. */
    val goalBodyFatPercentage: Double? = null,
    /** How body-fat readings are measured — drives the spike-guard width and how
     *  long a reading stays fresh ([BodyFatMethod.recencyDays]). Null = legacy
     *  behavior (±5 pp guard, 90-day freshness). */
    val bodyFatMethod: BodyFatMethod? = null,
    /** User override of the method's known accuracy, in percentage points. */
    val bodyFatAccuracyPoints: Double? = null,
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
     *  sum. Recalculate and weekly Adaptive leave a locked field untouched. Defaults false. */
    val caloriesLocked: Boolean = false,
    /** User locks over individual macros — at most two at once, so at least one stays free to
     *  balance. A locked macro is never auto-adjusted during a rebalance. Recalculate and weekly
     *  Adaptive leave locked macros untouched. Defaults empty. */
    val lockedMacros: Set<AutoBalanceMacro> = emptySet(),
    /** Macro day-type plan (Codeberg #60): named profiles with explicit targets assigned by
     *  manual toggle / weekday map / repeating cycle. Null = feature off (base targets
     *  everywhere). Rides this object through sync-1.2 + backup; see MacroPlanResolver. */
    val macroPlan: MacroPlan? = null,
    /** Goal phases (Progress v2): dated cut/maintain/bulk windows applied via
     *  macroPlan day-type profiles + dayAssignments — no new journal semantics.
     *  Default-empty so old payloads decode; rides the same sync/backup profile
     *  envelope as [macroPlan]. */
    val goalPhases: List<GoalPhase> = emptyList(),
    /** Provenance of the stored targets. AUTO sources (FORMULA/AI/ADAPTIVE) may be
     *  re-clamped by [reclampedAutoGoal] when weight/body-fat syncs move BMR/TDEE;
     *  MANUAL/UNKNOWN goals are never auto-adjusted. Default UNKNOWN so older
     *  serialized profiles decode unchanged. */
    val goalSource: GoalSource = GoalSource.UNKNOWN,
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

    /** True when BMR uses Katch-McArdle. Default on whenever a VALID body-fat fraction
     *  (3–65%) is set and the reading is fresh; [useBodyFatInBMR] false falls back to
     *  Mifflin-St Jeor without clearing BF%, and a stale reading
     *  (older than [NutritionConstants.BODY_FAT_RECENCY_DAYS]) does too. A out-of-range
     *  fraction (e.g. a 28.0 percent-vs-fraction typo) is ignored entirely. */
    val usesBodyFatForBMR: Boolean
        get() = validBodyFatFraction != null && useBodyFatInBMR != false && !isBodyFatStale()

    /** The body-fat reading only when finite and inside the valid 3–65% window. */
    private val validBodyFatFraction: Double?
        get() = bodyFatPercentage?.takeIf {
            it.isFinite() && it in NutritionConstants.BODY_FAT_MIN_FRACTION..NutritionConstants.BODY_FAT_MAX_FRACTION
        }

    /** Trust width for body-fat readings, in percentage points: the user's custom
     *  override, else the method's known accuracy, else the historical ±5 pp guard. */
    val effectiveBodyFatAccuracyPoints: Double
        get() = bodyFatAccuracyPoints ?: bodyFatMethod?.accuracyPoints ?: 5.0

    /** Day-to-day noise of the reading source: the same override (a wider stated
     *  band is also a wider noise band), else the method's repeatability, else
     *  the historical ±5 pp spike guard. */
    val effectiveBodyFatRepeatabilityPoints: Double
        get() = bodyFatAccuracyPoints ?: bodyFatMethod?.repeatabilityPoints ?: 5.0

    /** How long a body-fat reading stays fresh for Katch-McArdle: a clinical DEXA
     *  scan outlives a daily smart-scale estimate; null method = legacy 90 days. */
    val bodyFatRecencyDays: Long
        get() = bodyFatMethod?.recencyDays ?: NutritionConstants.BODY_FAT_RECENCY_DAYS

    /** Age of the body-fat reading in whole days, or null when undated (legacy). */
    fun bodyFatAgeDays(at: Instant = Instant.now()): Long? =
        bodyFatUpdatedAt?.let { java.time.Duration.between(it, at).toDays() }

    /** Dated body-fat reading older than [bodyFatRecencyDays]. */
    fun isBodyFatStale(at: Instant = Instant.now()): Boolean =
        (bodyFatAgeDays(at) ?: -1L) > bodyFatRecencyDays

    val bmr: Double get() = if (usesBodyFatForBMR) {
        // Katch-McArdle
        370.0 + 21.6 * (1.0 - validBodyFatFraction!!) * weightKg
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

    /** Unclamped TDEE ± pace. Display the raw deficit; persist [dailyCalories]. */
    val rawDailyCalories: Int get() = tdee.toInt() + calorieAdjustment

    val dailyCalories: Int get() = CalorieSafety.clampAuto(rawDailyCalories, bmr, tdee)

    private val standardProteinGoal: Int get() {
        // +0.2 g/kg during cutting phase to preserve lean mass (Helms et al 2014).
        val cut = if (goal == WeightGoal.LOSE) 0.2 else 0.0
        val total = (activityLevel.proteinPerKg + cut) * weightKg
        // A valid body-fat reading caps protein at lean mass: 2.2 g/kg LBM
        // (2.4 while losing) — high-BF profiles should not chase total-weight rates.
        val lbm = validBodyFatFraction?.let { weightKg * (1.0 - it) }
        val capped = if (lbm != null) {
            minOf(total, (if (goal == WeightGoal.LOSE) 2.4 else 2.2) * lbm)
        } else {
            total
        }
        return capped.toInt()
    }

    private val proteinBasisWeightKg: Double get() =
        validBodyFatFraction?.let { weightKg * (1.0 - it) } ?: weightKg

    /** Body-mass basis for [proteinGramsPerKg] when the user pins protein as g/kg. */
    val proteinTargetBasisKg: Double get() = when (proteinTargetMode) {
        ProteinTargetMode.GRAMS_PER_DAY -> weightKg
        ProteinTargetMode.G_PER_KG_TOTAL -> weightKg
        ProteinTargetMode.G_PER_KG_LBM -> proteinBasisWeightKg
    }.coerceAtLeast(0.1)

    private val standardFatGoal: Int get() = maxOf(
        (0.6 * weightKg).toInt(),
        (0.20 * dailyCalories / 9.0).roundToInt(),
    )

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
        weeklyChangeKg, goalWeightKg, bodyFatPercentage, bodyFatUpdatedAt?.epochSecond, useBodyFatInBMR,
        bodyFatMethod, bodyFatAccuracyPoints,
        macroPlan?.signature() ?: "-"
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

    /** Pin the current calorie number so Recalculate / Adaptive leave it. */
    fun pinCalories(): UserProfile = copy(
        customCalories = effectiveCalories,
        caloriesLocked = true,
    )

    /** Allow Recalculate / Adaptive to write calories. Keeps the current number. */
    fun unpinCalories(): UserProfile = copy(caloriesLocked = false)

    /** Pin the current grams for [macro]. No-op when already locked or a third lock is attempted. */
    fun pinMacro(macro: AutoBalanceMacro): UserProfile {
        if (macro in lockedMacros || lockedMacros.size >= 2) return this
        return withMacroGrams(mapOf(macro to effectiveGrams(macro)))
            .copy(lockedMacros = lockedMacros + macro)
    }

    /** Allow Recalculate / Adaptive to write [macro]. Keeps the current grams. */
    fun unpinMacro(macro: AutoBalanceMacro): UserProfile =
        copy(lockedMacros = lockedMacros - macro)

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
                    ProteinTargetMode.G_PER_KG_LBM -> proteinBasisWeightKg
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

    /** User edited the calorie target directly. Locked macros stay fixed; unlocked
     *  macros rebalance via [MacroBalance] (STANDARD keeps protein/fat and lets carbs
     *  absorb, KETO lets fat fill), so stored macros always add up to the stored total. */
    fun applyCaloriesEdit(newCalories: Int): UserProfile {
        val base = materialized()
        val target = maxOf(0, newCalories)
        val balanced = MacroBalance.rebalance(
            calories = target,
            protein = base.effectiveProtein,
            carbs = base.effectiveCarbs,
            fat = base.effectiveFat,
            mode = if (dietMode == DietMode.KETO) MacroBalance.Mode.KETO else MacroBalance.Mode.STANDARD,
            locked = base.lockedMacros,
            fatFloorG = macroBalanceFatFloor(target),
        )
        return base
            .withMacroGrams(unlockedMacroUpdates(base, balanced))
            .copy(customCalories = target)
    }

    /** Fat floor for [MacroBalance]: max(0.6 g/kg bodyweight, 20% of calories), and in
     *  keto max(0.6 g/kg, 45 g) — the 20% leg would push keto carbs up, not fat. */
    private fun macroBalanceFatFloor(targetCalories: Int): Int =
        if (dietMode == DietMode.KETO) {
            maxOf((0.6 * weightKg).toInt(), 45)
        } else {
            maxOf((0.6 * weightKg).toInt(), (0.20 * targetCalories / 9.0).roundToInt())
        }

    private fun unlockedMacroUpdates(base: UserProfile, balanced: MacroBalance.Grams): Map<AutoBalanceMacro, Int> =
        buildMap {
            if (!base.isMacroLocked(AutoBalanceMacro.PROTEIN)) put(AutoBalanceMacro.PROTEIN, balanced.protein)
            if (!base.isMacroLocked(AutoBalanceMacro.CARBS)) put(AutoBalanceMacro.CARBS, balanced.carbs)
            if (!base.isMacroLocked(AutoBalanceMacro.FAT)) put(AutoBalanceMacro.FAT, balanced.fat)
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
     * Switch [goal] the way Settings does: Maintain clears pace and target weight;
     * Lose/Gain seed a 0.5 kg/week pace and drop a target that now points the wrong way.
     * Calories and macros then follow the new goal from the formulas, so a lose-to-maintain
     * switch does not keep the old deficit on screen (Codeberg #3).
     */
    fun withGoal(newGoal: WeightGoal): UserProfile {
        val redirected = when (newGoal) {
            WeightGoal.MAINTAIN -> copy(
                goal = newGoal,
                weeklyChangeKg = null,
                goalWeightKg = null,
            )
            else -> {
                val gw = goalWeightKg
                val mismatched = gw != null && (
                    (newGoal == WeightGoal.LOSE && gw >= weightKg) ||
                    (newGoal == WeightGoal.GAIN && gw <= weightKg)
                )
                copy(
                    goal = newGoal,
                    weeklyChangeKg = weeklyChangeKg ?: 0.5,
                    goalWeightKg = if (mismatched) null else goalWeightKg,
                )
            }
        }
        return redirected.recalculatedFromFormulas()
    }

    /** Prompt fragment for Recalculate: locked fields are hard constraints. Empty if none. */
    fun goalLockPromptSection(): String {
        if (!caloriesLocked && lockedMacros.isEmpty()) return ""
        val lines = mutableListOf(
            "",
            "LOCKED TARGETS (the user pinned these; return these exact numbers):",
        )
        if (caloriesLocked) {
            lines.add(
                "- Calories locked at $effectiveCalories kcal. Return that exact calories value. " +
                    "Choose unlocked macros so 4*protein + 4*carbs + 9*fat is about $effectiveCalories. " +
                    "Do not lower calories to force a bigger deficit."
            )
        }
        if (isMacroLocked(AutoBalanceMacro.PROTEIN)) {
            lines.add("- Protein locked at $effectiveProtein g. Return that exact protein value.")
        }
        if (isMacroLocked(AutoBalanceMacro.CARBS)) {
            lines.add("- Carbs locked at $effectiveCarbs g. Return that exact carbs value.")
        }
        if (isMacroLocked(AutoBalanceMacro.FAT)) {
            lines.add("- Fat locked at $effectiveFat g. Return that exact fat value.")
        }
        lines.add("Unlocked macros may change. Locked fields must match the numbers above.")
        return lines.joinToString("\n")
    }

    /**
     * Apply an AI goal snapshot while keeping user locks. Unlocked fields take the new
     * values; locked fields stay. Unlocked macros are then rebalanced to the kept
     * calorie total via [MacroBalance] — even when calories was not clamped — so the
     * stored macros always add up to the stored total. An unlocked protein in a g/kg
     * rate mode keeps that mode (the balanced grams re-enter as the per-kg rate).
     */
    fun applyingAiGoals(calories: Int, protein: Int, carbs: Int, fat: Int): UserProfile {
        val clamped = CalorieSafety.clampAuto(calories, this)
        val keptCalories = if (caloriesLocked) effectiveCalories else clamped
        val balanced = MacroBalance.rebalance(
            calories = keptCalories,
            protein = if (isMacroLocked(AutoBalanceMacro.PROTEIN)) effectiveProtein else protein,
            carbs = if (isMacroLocked(AutoBalanceMacro.CARBS)) effectiveCarbs else carbs,
            fat = if (isMacroLocked(AutoBalanceMacro.FAT)) effectiveFat else fat,
            mode = if (dietMode == DietMode.KETO) MacroBalance.Mode.KETO else MacroBalance.Mode.STANDARD,
            locked = lockedMacros,
            fatFloorG = macroBalanceFatFloor(keptCalories),
        )
        return copy(customCalories = keptCalories, goalSource = GoalSource.AI)
            .withMacroGrams(unlockedMacroUpdates(this, balanced))
    }

    /**
     * Re-clamp an auto-sourced goal into the CAL-SAFE envelope. Called before saving a
     * profile whose BMR/TDEE just moved (weight/body-fat sync): a stored FORMULA/AI/
     * ADAPTIVE target that now sits below the new BMR floor (or above the ceiling) is
     * pulled back in and rebalanced; MANUAL/UNKNOWN targets are never touched, and a
     * locked calorie total is left as the user pinned it. Enabled day-type profiles are
     * re-clamped via [MacroDayProfile.shiftedBy] regardless of the base goal's source,
     * so a lifted floor lifts every training/rest day too. A no-op when the target is
     * already inside the envelope and its macros add up (±30 kcal).
     */
    fun reclampedAutoGoal(): UserProfile {
        if (caloriesLocked) return this
        val plan = macroPlan?.takeIf { it.enabled }
        val planClamped = if (plan == null) this else {
            val profiles = plan.profiles.map { it.shiftedBy(0, bmr, tdee) }
            if (profiles == plan.profiles) this else copy(macroPlan = plan.copy(profiles = profiles))
        }
        if (goalSource !in AUTO_GOAL_SOURCES) return planClamped
        val floor = CalorieSafety.floorKcal(bmr)
        val target = effectiveCalories.coerceIn(floor, CalorieSafety.ceilingKcal(tdee, floor))
        val macroKcal = effectiveProtein * 4 + effectiveCarbs * 4 + effectiveFat * 9
        if (target == effectiveCalories && abs(macroKcal - target) <= 30) return planClamped
        return planClamped.applyCaloriesEdit(target)
    }

    /**
     * Recalculate application with day types (#60 phase 4): the base target set
     * is written by [applyingAiGoals]; when a macro plan is enabled (and base
     * calories are not locked — locks stay base-scoped), every day-type profile
     * moves too. Explicit model results ([GoalCalculation.profiles], matched by
     * id, per-profile MACRO-CYCLE-C clamps) win; a missing or snapped array
     * falls back to the same kcal delta the base change implies, so the
     * training/rest spread survives and older-model responses still work.
     */
    fun applyingAiGoalsToPlan(result: GoalCalculation): UserProfile {
        val base = applyingAiGoals(result.calories, result.protein, result.carbs, result.fat)
        val plan = macroPlan?.takeIf { it.enabled } ?: return base
        if (caloriesLocked) return base
        val deltaKcal = base.effectiveCalories - effectiveCalories
        val aiById = result.profiles.associateBy { it.id }
        val updated = plan.profiles.map { profile ->
            val ai = aiById[profile.id]
            if (ai != null) {
                val target = CalorieSafety.clampAuto(ai.calories, bmr, tdee)
                profile.copy(
                    calories = target,
                    proteinG = ai.proteinG.coerceIn(0, 500),
                    carbsG = ai.carbsG.coerceIn(0, 1200),
                    fatG = ai.fatG.coerceIn(0, 400),
                ).rebalancedTo(target)
            } else {
                profile.shiftedBy(deltaKcal, bmr, tdee)
            }
        }
        return base.copy(macroPlan = plan.copy(profiles = updated))
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
        lockedMacros = emptySet(),
        goalSource = GoalSource.FORMULA,
    )

    companion object {
        /** Goal sources an automatic re-clamp may adjust ([reclampedAutoGoal]). */
        private val AUTO_GOAL_SOURCES = setOf(GoalSource.FORMULA, GoalSource.AI, GoalSource.ADAPTIVE)

        private fun defaultBirthday(): Instant =
            LocalDate.now().minusYears(25).atStartOfDay(ZoneId.systemDefault()).toInstant()

        val Default = UserProfile()
    }
}
