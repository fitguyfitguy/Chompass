package app.chompass.services

import app.chompass.models.BodyFatEntry
import app.chompass.models.BodyCompositionEntry
import app.chompass.models.BodyMeasurement
import app.chompass.models.ChatMessage
import app.chompass.models.DailyNote
import app.chompass.models.FoodConstituent
import app.chompass.models.FoodEntry
import app.chompass.models.FoodProductMetadata
import app.chompass.models.FoodSource
import app.chompass.models.HabitPreset
import app.chompass.models.HabitPresetCatalog
import app.chompass.models.HabitPresetDomain
import app.chompass.models.MealCatalog
import app.chompass.models.MealDef
import app.chompass.models.MealType
import app.chompass.models.MicronutrientValues
import app.chompass.models.MilkKind
import app.chompass.models.ServingUnitOption
import app.chompass.models.microsCompositionSignature
import app.chompass.models.CaffeineEntry
import app.chompass.models.CaffeineKind
import app.chompass.models.NicotineEntry
import app.chompass.models.NicotineKind
import app.chompass.models.Recipe
import app.chompass.models.RecipeIngredient
import app.chompass.models.WaterEntry
import app.chompass.models.WeightEntry
import app.chompass.services.health.DebugActivityDay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.random.Random

/** Shared synthetic food / weight / body-fat generators for dev seeding and screenshot fixtures. */
internal object SampleDataGenerators {
    fun foodEntries(
        totalDays: Int = 365,
        today: LocalDate = LocalDate.now(),
    ): List<FoodEntry> {
        val zone = ZoneId.systemDefault()
        val rng = Random(seed = 0xF0D)

        val breakfast = listOf(
            MealTemplate("Greek yogurt with berries", 280, 22, 32, 6, "🥣"),
            MealTemplate("Oatmeal with banana", 340, 12, 60, 8, "🥣"),
            MealTemplate("Avocado toast", 380, 14, 38, 18, "🥑"),
            MealTemplate("Protein smoothie", 310, 30, 28, 7, "🥤"),
            MealTemplate("Eggs and toast", 420, 22, 30, 22, "🍳"),
        )
        val lunch = listOf(
            MealTemplate("Chicken caesar salad", 540, 38, 22, 32, "🥗"),
            MealTemplate("Turkey sandwich", 480, 32, 48, 16, "🥪"),
            MealTemplate("Sushi rolls", 620, 28, 84, 14, "🍣"),
            MealTemplate("Burrito bowl", 720, 36, 78, 24, "🌯"),
            MealTemplate("Pasta primavera", 560, 18, 82, 18, "🍝"),
        )
        val dinner = listOf(
            MealTemplate("Grilled salmon and rice", 640, 42, 58, 22, "🐟"),
            MealTemplate("Steak and broccoli", 720, 50, 18, 46, "🥩"),
            MealTemplate("Chicken stir-fry", 580, 40, 52, 20, "🍛"),
            MealTemplate("Veggie curry", 510, 18, 72, 18, "🍛"),
            MealTemplate("Margherita pizza", 780, 28, 92, 28, "🍕"),
        )
        val snacks = listOf(
            MealTemplate("Apple", 95, 0, 25, 0, "🍎"),
            MealTemplate("Almonds", 170, 6, 6, 14, "🥜"),
            MealTemplate("Protein bar", 210, 20, 22, 6, "🍫"),
            MealTemplate("Banana", 105, 1, 27, 0, "🍌"),
            MealTemplate("Greek yogurt", 130, 18, 8, 4, "🥛"),
        )

        val out = mutableListOf<FoodEntry>()
        for (daysAgo in totalDays downTo 0) {
            val day = today.minusDays(daysAgo.toLong())
            val skipDay = rng.nextInt(20) == 0
            if (skipDay) continue

            fun add(template: MealTemplate, hour: Int, meal: String) {
                val jitter = rng.nextDouble(0.85, 1.15)
                val ts = day.atTime(LocalTime.of(hour, rng.nextInt(0, 50)))
                    .atZone(zone).toInstant()
                out.add(
                    FoodEntry(
                        name = template.name,
                        calories = (template.cal * jitter).toInt(),
                        protein = template.p * jitter,
                        carbs = template.c * jitter,
                        fat = template.f * jitter,
                        timestamp = ts,
                        emoji = template.emoji,
                        source = FoodSource.TEXT_INPUT,
                        mealType = meal,
                    ),
                )
            }

            add(breakfast.random(rng), hour = 8, meal = MealType.BREAKFAST.id)
            add(lunch.random(rng), hour = 13, meal = MealType.LUNCH.id)
            add(dinner.random(rng), hour = 19, meal = MealType.DINNER.id)
            if (rng.nextBoolean()) add(snacks.random(rng), hour = 16, meal = MealType.SNACK.id)
        }
        return out
    }

    /** Weight readings over [totalDays], linear [startKg]→[endKg] trend with day-to-day noise. */
    fun weightSeries(
        totalDays: Int,
        startKg: Double,
        endKg: Double,
        seed: Long,
        today: LocalDate = LocalDate.now(),
    ): List<WeightEntry> {
        val zone = ZoneId.systemDefault()
        val rng = Random(seed)
        val out = mutableListOf<WeightEntry>()
        for (daysAgo in (totalDays - 1) downTo 0) {
            if (daysAgo > 1 && rng.nextInt(10) < 2) continue
            val day = today.minusDays(daysAgo.toLong())
            val progress = (totalDays - 1 - daysAgo).toDouble() / (totalDays - 1)
            val baseline = startKg - (startKg - endKg) * progress
            val noise = rng.nextDouble(-0.5, 0.5)
            val ts = day.atTime(8, rng.nextInt(0, 30)).atZone(zone).toInstant()
            out.add(WeightEntry(date = ts, weightKg = baseline + noise))
        }
        return out
    }

    /** Year-scale weight trend with ~30% skipped days (seed_test_data style). */
    fun yearWeights(today: LocalDate = LocalDate.now()): List<WeightEntry> {
        val zone = ZoneId.systemDefault()
        val rng = Random(seed = 0xC0FFEE)
        val startKg = 78.0
        val endKg = 73.5
        val totalDays = 365

        val out = mutableListOf<WeightEntry>()
        for (daysAgo in (totalDays - 1) downTo 0) {
            if (daysAgo > 1 && rng.nextInt(10) < 3) continue
            val day = today.minusDays(daysAgo.toLong())
            val progress = (totalDays - 1 - daysAgo).toDouble() / (totalDays - 1)
            val baseline = startKg - (startKg - endKg) * progress
            val noise = rng.nextDouble(-0.6, 0.6)
            val ts = day.atTime(8, rng.nextInt(0, 30)).atZone(zone).toInstant()
            out.add(WeightEntry(date = ts, weightKg = baseline + noise))
        }
        return out
    }

    fun bodyFatSeries(
        totalDays: Int,
        startFraction: Double,
        endFraction: Double,
        seed: Long,
        today: LocalDate = LocalDate.now(),
    ): List<BodyFatEntry> {
        val zone = ZoneId.systemDefault()
        val rng = Random(seed)
        val out = mutableListOf<BodyFatEntry>()
        for (daysAgo in (totalDays - 1) downTo 0) {
            if (daysAgo > 1 && rng.nextInt(10) < 4) continue
            val day = today.minusDays(daysAgo.toLong())
            val progress = (totalDays - 1 - daysAgo).toDouble() / (totalDays - 1)
            val baseline = startFraction - (startFraction - endFraction) * progress
            val noise = rng.nextDouble(-0.003, 0.003)
            val ts = day.atTime(8, rng.nextInt(0, 30)).atZone(zone).toInstant()
            out.add(BodyFatEntry(date = ts, bodyFatFraction = baseline + noise))
        }
        return out
    }

    /**
     * Bioimpedance-scale rows derived from an existing weight + body-fat history, so the
     * seeded numbers stay physiologically plausible (bone ≈ 4% of mass, water ≈ 57.5%,
     * muscle ≈ 38%, lean = mass × (1 − body-fat)). One row every third weigh-in, i.e.
     * "scale day is not every day". [bodyFats] supplies the nearest reading; a gap
     * falls back to 18% so lean mass still exists.
     */
    fun compositionSeries(
        weights: List<WeightEntry>,
        bodyFats: List<BodyFatEntry>,
        seed: Long,
    ): List<BodyCompositionEntry> {
        val rng = Random(seed)
        val fats = bodyFats.sortedBy { it.date }
        fun fractionAt(at: Instant): Double {
            val nearest = fats.minByOrNull { kotlin.math.abs(it.date.toEpochMilli() - at.toEpochMilli()) }
            return nearest?.bodyFatFraction ?: 0.18
        }
        return weights.sortedBy { it.date }
            .filterIndexed { index, _ -> index % 3 == 0 }
            .map { weight ->
                val bf = fractionAt(weight.date)
                val mass = weight.weightKg
                BodyCompositionEntry(
                    date = weight.date,
                    boneMassKg = (0.040 * mass + rng.nextDouble(-0.05, 0.05)).roundToSingle(),
                    totalBodyWaterKg = (0.575 * mass + rng.nextDouble(-0.4, 0.4)).roundToSingle(),
                    muscleMassKg = (0.380 * mass + rng.nextDouble(-0.3, 0.3)).roundToSingle(),
                    leanMassKg = (mass * (1.0 - bf)).roundToSingle(),
                    visceralFatRating = 8.0,
                )
            }
    }

    private fun Double.roundToSingle(): Double = (this * 10).roundToInt() / 10.0

    /** Weekly body-measurement snapshots over [totalDays]: waist/hips/chest/neck
     *  with linear cm trends + day-to-day noise — 4 of the 8 sites, like a real
     *  user who logs the tape once a week. */
    fun measurementSeries(
        totalDays: Int = 90,
        seed: Long,
        today: LocalDate = LocalDate.now(),
    ): List<BodyMeasurement> {
        val zone = ZoneId.systemDefault()
        val rng = Random(seed)
        val out = mutableListOf<BodyMeasurement>()
        for (daysAgo in (totalDays - 1) downTo 0 step 7) {
            val day = today.minusDays(daysAgo.toLong())
            val progress = (totalDays - 1 - daysAgo).toDouble() / (totalDays - 1)
            fun trend(start: Double, end: Double) = start - (start - end) * progress
            val ts = day.atTime(7, rng.nextInt(0, 60)).atZone(zone).toInstant()
            out.add(
                BodyMeasurement(
                    date = ts,
                    neckCm = trend(39.0, 38.0) + rng.nextDouble(-0.3, 0.3),
                    waistCm = trend(88.0, 83.0) + rng.nextDouble(-0.6, 0.6),
                    hipsCm = trend(104.0, 101.0) + rng.nextDouble(-0.5, 0.5),
                    chestCm = trend(100.0, 97.5) + rng.nextDouble(-0.5, 0.5),
                )
            )
        }
        return out
    }

    fun debugActivityDays(
        totalDays: Int,
        today: LocalDate = LocalDate.now(),
    ): List<DebugActivityDay> {
        val rng = Random(seed = 0xF0D)
        val out = mutableListOf<DebugActivityDay>()
        for (daysAgo in totalDays downTo 0) {
            val day = today.minusDays(daysAgo.toLong())
            val skipDay = daysAgo != 0 && rng.nextInt(20) == 0
            if (skipDay) continue
            val steps = rng.nextLong(4_500, 14_000)
            val active = rng.nextInt(280, 650)
            val basal = rng.nextInt(1_520, 1_780)
            out.add(
                DebugActivityDay(
                    date = day.toString(),
                    steps = steps,
                    activeCalories = active,
                    totalCalories = active + basal,
                ),
            )
        }
        return out
    }

    /**
     * Power-user diary: mixed sources, micros on a subset, a few recipe-log
     * groups. Photo filenames are stamped later by the seeder so this stays
     * unit-testable without an image store.
     */
    fun fullFoodEntries(
        totalDays: Int = 365,
        today: LocalDate = LocalDate.now(),
    ): List<FoodEntry> {
        val zone = ZoneId.systemDefault()
        val rng = Random(seed = 0xF011)
        val breakfast = listOf(
            MealTemplate("Greek yogurt with berries", 280, 22, 32, 6, "\uD83E\uDD63", fiber = 4.0, sodium = 85.0, sugar = 18.0),
            MealTemplate("Oatmeal with banana", 340, 12, 60, 8, "\uD83E\uDD63", fiber = 7.0, sodium = 40.0, sugar = 14.0),
            MealTemplate("Avocado toast", 380, 14, 38, 18, "\uD83E\uDD51", fiber = 8.0, sodium = 420.0, sugar = 2.0),
            MealTemplate("Protein smoothie", 310, 30, 28, 7, "\uD83E\uDD64", fiber = 3.0, sodium = 110.0, sugar = 16.0),
            MealTemplate("Eggs and toast", 420, 22, 30, 22, "\uD83E\uDD5A", fiber = 2.0, sodium = 480.0, sugar = 3.0),
        )
        val lunch = listOf(
            MealTemplate("Chicken caesar salad", 540, 38, 22, 32, "\uD83E\uDD57", fiber = 4.0, sodium = 890.0, sugar = 4.0),
            MealTemplate("Turkey sandwich", 480, 32, 48, 16, "\uD83E\uDD6A", fiber = 5.0, sodium = 980.0, sugar = 6.0),
            MealTemplate("Sushi rolls", 620, 28, 84, 14, "\uD83C\uDF63", fiber = 3.0, sodium = 720.0, sugar = 8.0),
            MealTemplate("Burrito bowl", 720, 36, 78, 24, "\uD83C\uDF2F", fiber = 11.0, sodium = 1100.0, sugar = 7.0),
            MealTemplate("Pasta primavera", 560, 18, 82, 18, "\uD83C\uDF5D", fiber = 6.0, sodium = 640.0, sugar = 9.0),
        )
        val dinner = listOf(
            MealTemplate("Grilled salmon and rice", 640, 42, 58, 22, "\uD83D\uDC1F", fiber = 2.0, sodium = 510.0, sugar = 1.0),
            MealTemplate("Steak and broccoli", 720, 50, 18, 46, "\uD83E\uDD69", fiber = 5.0, sodium = 380.0, sugar = 3.0),
            MealTemplate("Chicken stir-fry", 580, 40, 52, 20, "\uD83C\uDF5B", fiber = 4.0, sodium = 870.0, sugar = 8.0),
            MealTemplate("Veggie curry", 510, 18, 72, 18, "\uD83C\uDF5B", fiber = 9.0, sodium = 760.0, sugar = 10.0),
            MealTemplate("Margherita pizza", 780, 28, 92, 28, "\uD83C\uDF55", fiber = 4.0, sodium = 1320.0, sugar = 7.0),
        )
        val snacks = listOf(
            MealTemplate("Apple", 95, 0, 25, 0, "\uD83C\uDF4E", fiber = 4.0, sodium = 2.0, sugar = 19.0),
            MealTemplate("Almonds", 170, 6, 6, 14, "\uD83E\uDD5C", fiber = 3.0, sodium = 1.0, sugar = 1.0),
            MealTemplate("Protein bar", 210, 20, 22, 6, "\uD83C\uDF6B", fiber = 5.0, sodium = 180.0, sugar = 9.0),
            MealTemplate("Banana", 105, 1, 27, 0, "\uD83C\uDF4C", fiber = 3.0, sodium = 1.0, sugar = 14.0),
            MealTemplate("Greek yogurt", 130, 18, 8, 4, "\uD83E\uDD5B", fiber = 0.0, sodium = 55.0, sugar = 6.0),
        )
        val recipeMains = RecipeMains

        val out = mutableListOf<FoodEntry>()
        for (daysAgo in totalDays downTo 0) {
            val day = today.minusDays(daysAgo.toLong())
            if (rng.nextInt(20) == 0) continue

            fun add(
                template: MealTemplate,
                hour: Int,
                meal: String,
                source: FoodSource,
                recipeLogId: UUID? = null,
                withMicros: Boolean,
            ) {
                val jitter = rng.nextDouble(0.85, 1.15)
                val ts = day.atTime(LocalTime.of(hour, rng.nextInt(0, 50)))
                    .atZone(zone).toInstant()
                out.add(
                    FoodEntry(
                        name = template.name,
                        calories = (template.cal * jitter).toInt(),
                        protein = template.p * jitter,
                        carbs = template.c * jitter,
                        fat = template.f * jitter,
                        timestamp = ts,
                        emoji = template.emoji,
                        source = source,
                        mealType = meal,
                        fiber = if (withMicros) template.fiber?.times(jitter) else null,
                        sodium = if (withMicros) template.sodium?.times(jitter) else null,
                        sugar = if (withMicros) template.sugar?.times(jitter) else null,
                        saturatedFat = if (withMicros) template.f * 0.35 * jitter else null,
                        recipeLogId = recipeLogId,
                    ),
                )
            }

            fun sourceFor(daysAgo: Int): FoodSource = when {
                daysAgo <= 40 && rng.nextInt(8) == 0 -> FoodSource.SNAP_FOOD
                rng.nextInt(12) == 0 -> FoodSource.BARCODE
                rng.nextInt(10) == 0 -> FoodSource.MANUAL
                else -> FoodSource.TEXT_INPUT
            }

            add(breakfast.random(rng), hour = 8, meal = MealType.BREAKFAST.id, source = sourceFor(daysAgo), withMicros = rng.nextInt(3) != 0)
            add(lunch.random(rng), hour = 13, meal = MealType.LUNCH.id, source = sourceFor(daysAgo), withMicros = rng.nextInt(3) != 0)
            if (daysAgo % 11 == 0) {
                val recipeLogId = UUID.randomUUID()
                val parts = recipeMains.random(rng)
                add(parts.first, hour = 18, meal = MealType.DINNER.id, source = FoodSource.MANUAL, recipeLogId = recipeLogId, withMicros = true)
                add(parts.second, hour = 18, meal = MealType.DINNER.id, source = FoodSource.MANUAL, recipeLogId = recipeLogId, withMicros = true)
                add(parts.third, hour = 18, meal = MealType.DINNER.id, source = FoodSource.MANUAL, recipeLogId = recipeLogId, withMicros = true)
            } else {
                add(dinner.random(rng), hour = 19, meal = MealType.DINNER.id, source = sourceFor(daysAgo), withMicros = rng.nextInt(3) != 0)
            }
            if (rng.nextBoolean()) {
                add(snacks.random(rng), hour = 16, meal = MealType.SNACK.id, source = sourceFor(daysAgo), withMicros = rng.nextBoolean())
            }
        }
        return out
    }

    fun waterEntries(
        totalDays: Int = 365,
        today: LocalDate = LocalDate.now(),
    ): List<WaterEntry> {
        val zone = ZoneId.systemDefault()
        val rng = Random(seed = 0xA11E)
        val cups = intArrayOf(250, 300, 350, 500)
        val hours = intArrayOf(8, 10, 12, 15, 17, 20)
        val out = mutableListOf<WaterEntry>()
        for (daysAgo in (totalDays - 1) downTo 0) {
            if (daysAgo > 1 && rng.nextInt(12) == 0) continue
            val day = today.minusDays(daysAgo.toLong())
            val sips = rng.nextInt(4, 8)
            repeat(sips) { i ->
                val hour = hours[i % hours.size]
                val ts = day.atTime(hour, rng.nextInt(0, 50)).atZone(zone).toInstant()
                out.add(WaterEntry(date = ts, milliliters = cups.random(rng)))
            }
        }
        return out
    }

    /** A few weeks of nicotine logs (cigarettes + occasional vape) for seeding. */
    fun nicotineEntries(
        totalDays: Int = 30,
        today: LocalDate = LocalDate.now(),
    ): List<NicotineEntry> {
        val zone = ZoneId.systemDefault()
        val rng = Random(seed = 0x51C0)
        val kinds = NicotineKind.entries.filter { it != NicotineKind.OTHER }.map { it.storageKey }
        val out = mutableListOf<NicotineEntry>()
        for (daysAgo in (totalDays - 1) downTo 0) {
            val day = today.minusDays(daysAgo.toLong())
            val logs = rng.nextInt(3, 10)
            repeat(logs) { i ->
                val hour = 7 + rng.nextInt(0, 12)
                val ts = day.atTime(hour, rng.nextInt(0, 60)).atZone(zone).toInstant()
                out.add(
                    NicotineEntry(
                        date = ts,
                        kind = kinds.random(rng),
                        count = 1,
                        mg = if (rng.nextInt(5) == 0) rng.nextDouble(3.0, 12.0) else null,
                    )
                )
            }
        }
        return out
    }

    /** A few weeks of caffeine logs (coffee morning, tea afternoon) for seeding. */
    fun caffeineEntries(
        totalDays: Int = 30,
        today: LocalDate = LocalDate.now(),
    ): List<CaffeineEntry> {
        val zone = ZoneId.systemDefault()
        val rng = Random(seed = 0xC0FFE)
        val out = mutableListOf<CaffeineEntry>()
        for (daysAgo in (totalDays - 1) downTo 0) {
            val day = today.minusDays(daysAgo.toLong())
            // Morning coffee + afternoon tea/energy, like a normal day.
            val morning = day.atTime(7 + rng.nextInt(0, 3), rng.nextInt(0, 60)).atZone(zone).toInstant()
            out.add(
                CaffeineEntry(
                    date = morning,
                    kind = CaffeineKind.COFFEE.storageKey,
                    mg = CaffeineKind.COFFEE.defaultMg ?: 95.0,
                )
            )
            if (rng.nextInt(10) < 7) {
                val kind = if (rng.nextInt(10) < 7) CaffeineKind.TEA else CaffeineKind.ENERGY
                val afternoon = day.atTime(13 + rng.nextInt(0, 4), rng.nextInt(0, 60)).atZone(zone).toInstant()
                out.add(
                    CaffeineEntry(
                        date = afternoon,
                        kind = kind.storageKey,
                        mg = kind.defaultMg ?: 28.0,
                    )
                )
            }
        }
        return out
    }

    fun sampleRecipes(now: Instant = Instant.now()): List<Recipe> {
        fun ing(name: String, emoji: String, cal: Int, p: Int, c: Int, f: Int, fiber: Double, sodium: Double) =
            RecipeIngredient(
                name = name,
                emoji = emoji,
                baseCalories = cal,
                baseProtein = p.toDouble(),
                baseCarbs = c.toDouble(),
                baseFat = f.toDouble(),
                baseFiber = fiber,
                baseSodium = sodium,
            )
        return listOf(
            Recipe(
                name = "Overnight oats",
                emoji = "\uD83E\uDD63",
                mealType = MealType.BREAKFAST.id,
                createdAt = now.minusSeconds(86_400L * 40),
                ingredients = listOf(
                    ing("Rolled oats", "\uD83C\uDF3E", 150, 5, 27, 3, 4.0, 2.0),
                    ing("Greek yogurt", "\uD83E\uDD5B", 100, 14, 6, 2, 0.0, 40.0),
                    ing("Blueberries", "\uD83C\uDF53", 40, 0, 10, 0, 2.0, 1.0),
                ),
            ),
            Recipe(
                name = "Chicken rice bowl",
                emoji = "\uD83C\uDF57",
                mealType = MealType.LUNCH.id,
                createdAt = now.minusSeconds(86_400L * 20),
                ingredients = listOf(
                    ing("Chicken thigh", "\uD83C\uDF57", 240, 28, 0, 14, 0.0, 90.0),
                    ing("Brown rice", "\uD83C\uDF5A", 220, 5, 46, 2, 3.0, 8.0),
                    ing("Roasted broccoli", "\uD83E\uDD66", 80, 4, 12, 3, 4.0, 30.0),
                ),
            ),
            Recipe(
                name = "Tofu soba",
                emoji = "\uD83C\uDF5C",
                mealType = MealType.DINNER.id,
                createdAt = now.minusSeconds(86_400L * 8),
                ingredients = listOf(
                    ing("Tofu", "\uD83E\uDDC6", 180, 16, 6, 10, 1.0, 20.0),
                    ing("Soba noodles", "\uD83C\uDF5C", 250, 8, 48, 2, 2.0, 240.0),
                    ing("Peanut sauce", "\uD83E\uDD5C", 140, 4, 8, 11, 1.0, 310.0),
                ),
            ),
            Recipe(
                name = "Salmon plate",
                emoji = "\uD83D\uDC1F",
                mealType = MealType.DINNER.id,
                createdAt = now.minusSeconds(86_400L * 3),
                ingredients = listOf(
                    ing("Grilled salmon", "\uD83D\uDC1F", 360, 34, 0, 24, 0.0, 80.0),
                    ing("Quinoa", "\uD83C\uDF3E", 180, 6, 32, 3, 3.0, 10.0),
                    ing("Asparagus", "\uD83E\uDD66", 40, 3, 6, 0, 2.0, 4.0),
                ),
            ),
        )
    }

    fun sampleFavorites(now: Instant = Instant.now()): List<FoodEntry> {
        val templates = listOf(
            MealTemplate("Greek yogurt with berries", 280, 22, 32, 6, "\uD83E\uDD63", fiber = 4.0, sodium = 85.0, sugar = 18.0),
            MealTemplate("Oatmeal with banana", 340, 12, 60, 8, "\uD83E\uDD63", fiber = 7.0, sodium = 40.0, sugar = 14.0),
            MealTemplate("Avocado toast", 380, 14, 38, 18, "\uD83E\uDD51", fiber = 8.0, sodium = 420.0, sugar = 2.0),
            MealTemplate("Chicken caesar salad", 540, 38, 22, 32, "\uD83E\uDD57", fiber = 4.0, sodium = 890.0, sugar = 4.0),
            MealTemplate("Turkey sandwich", 480, 32, 48, 16, "\uD83E\uDD6A", fiber = 5.0, sodium = 980.0, sugar = 6.0),
            MealTemplate("Burrito bowl", 720, 36, 78, 24, "\uD83C\uDF2F", fiber = 11.0, sodium = 1100.0, sugar = 7.0),
            MealTemplate("Grilled salmon and rice", 640, 42, 58, 22, "\uD83D\uDC1F", fiber = 2.0, sodium = 510.0, sugar = 1.0),
            MealTemplate("Steak and broccoli", 720, 50, 18, 46, "\uD83E\uDD69", fiber = 5.0, sodium = 380.0, sugar = 3.0),
            MealTemplate("Protein bar", 210, 20, 22, 6, "\uD83C\uDF6B", fiber = 5.0, sodium = 180.0, sugar = 9.0),
            MealTemplate("Almonds", 170, 6, 6, 14, "\uD83E\uDD5C", fiber = 3.0, sodium = 1.0, sugar = 1.0),
        )
        return templates.mapIndexed { i, t ->
            FoodEntry(
                name = t.name,
                calories = t.cal,
                protein = t.p.toDouble(),
                carbs = t.c.toDouble(),
                fat = t.f.toDouble(),
                timestamp = now.minusSeconds(86_400L * (i + 1).toLong()),
                emoji = t.emoji,
                source = FoodSource.TEXT_INPUT,
                mealType = when {
                    i < 3 -> MealType.BREAKFAST.id
                    i < 6 -> MealType.LUNCH.id
                    i < 8 -> MealType.DINNER.id
                    else -> MealType.SNACK.id
                },
                fiber = t.fiber,
                sodium = t.sodium,
                sugar = t.sugar,
            )
        }
    }

    fun sampleChat(now: Instant = Instant.now(), withAttachment: Boolean = false): List<ChatMessage> {
        val turns = listOf(
            ChatMessage.Role.USER to "How am I doing on protein this week?",
            ChatMessage.Role.ASSISTANT to "You are averaging about 140 g of protein a day, a bit under your 150 g target. A Greek yogurt or an extra chicken serving at lunch would close the gap.",
            ChatMessage.Role.USER to "Any ideas for a 500 calorie lunch?",
            ChatMessage.Role.ASSISTANT to "A turkey sandwich with a side salad lands around 480 calories and keeps carbs moderate. Swap the chips for fruit if you want more fiber.",
            ChatMessage.Role.USER to "I walked 12k steps yesterday. Should I eat more?",
            ChatMessage.Role.ASSISTANT to "That is a solid active day. If the home gauge is in add-active mode, the extra burn is already in the ring. A 200 calorie snack is optional, not required.",
            ChatMessage.Role.USER to "Remind me of my water goal.",
            ChatMessage.Role.ASSISTANT to "Your dynamic goal is based on weight, activity, and food water. Aim for the cup reminders through the afternoon and you will hit it.",
            ChatMessage.Role.USER to "Is pizza okay tonight?",
            ChatMessage.Role.ASSISTANT to "Yes. Log two slices, add a side salad, and keep the rest of the day closer to target. One meal does not break the week.",
            ChatMessage.Role.USER to "What should I hit for fiber?",
            ChatMessage.Role.ASSISTANT to "Your optional fiber goal is 30 g. Oats at breakfast and a burrito bowl at lunch usually get you most of the way there.",
            ChatMessage.Role.USER to "How is my weight trend?",
            ChatMessage.Role.ASSISTANT to "Down about 4 kg over the last year with normal day-to-day noise. Stay the course.",
            ChatMessage.Role.USER to "Thanks.",
            ChatMessage.Role.ASSISTANT to "Anytime. Log dinner when you can and I will keep an eye on the macros.",
        )
        val messages = turns.mapIndexed { i, (role, content) ->
            ChatMessage(
                role = role,
                content = content,
                timestamp = now.minusSeconds(120L * (turns.size - i).toLong()),
            )
        }
        if (!withAttachment) return messages
        // One image bubble (Coach attachment rendering); default off so the
        // 16-message pin in SampleDataGeneratorsTest stays true.
        return messages + ChatMessage(
            role = ChatMessage.Role.USER,
            content = "Here is the plate, does the portion look right?",
            attachmentImageBase64 = ONE_PIXEL_PNG_BASE64,
            timestamp = now,
        )
    }

    // ---- Coverage layer: full micros, serving units, composites, imports ----

    /**
     * Deterministic plausible per-serving micros: all 23 fields set, none null,
     * so every micro row in the edit sheet has a value to render. Values are
     * rounded to 1 decimal like the sheet preview.
     */
    fun microsForSeed(seed: Long): MicronutrientValues {
        val rng = Random(seed)
        fun v(lo: Double, hi: Double): Double = round1(rng.nextDouble(lo, hi))
        return MicronutrientValues(
            sugar = v(8.0, 30.0),
            addedSugar = v(0.0, 25.0),
            fiber = v(1.0, 10.0),
            saturatedFat = v(1.0, 12.0),
            monounsaturatedFat = v(1.0, 15.0),
            polyunsaturatedFat = v(0.5, 8.0),
            cholesterol = v(0.0, 180.0),
            sodium = v(100.0, 1200.0),
            potassium = v(200.0, 1500.0),
            transFat = v(0.0, 1.0),
            calcium = v(50.0, 400.0),
            iron = v(1.0, 8.0),
            magnesium = v(20.0, 120.0),
            zinc = v(1.0, 6.0),
            vitaminA = v(50.0, 800.0),
            vitaminC = v(2.0, 60.0),
            vitaminD = v(0.5, 6.0),
            vitaminB12 = v(0.2, 3.0),
            vitaminE = v(0.5, 6.0),
            vitaminK = v(5.0, 120.0),
            folate = v(20.0, 200.0),
            omega3 = v(0.1, 2.0),
            caffeine = v(0.0, 120.0),
        )
    }

    /** [entry] with every one of the 23 micro fields overwritten from [seed]. */
    fun withFullMicros(entry: FoodEntry, seed: Long): FoodEntry = microsForSeed(seed).applyTo(entry)

    /** [entry] carrying a g + cup serving option, cup selected at 1.5 (320 g). */
    fun withServingUnits(entry: FoodEntry): FoodEntry = entry.copy(
        servingSizeGrams = 320.0,
        servingUnitOptions = listOf(
            ServingUnitOption(unit = "g", gramsPerUnit = 1.0),
            ServingUnitOption(unit = "cup", gramsPerUnit = 240.0, quantity = 1.5),
        ),
        selectedServingUnit = "cup",
        selectedServingQuantity = 1.5,
    )

    /** [entry] plus an Open Food Facts style product card (renders ungated in the edit sheet). */
    fun withProductMetadata(entry: FoodEntry): FoodEntry = entry.copy(
        productMetadata = FoodProductMetadata(
            barcode = "4001234567890",
            packageQuantity = "250 g",
            ingredientsText = "Zucker, Haferflocken, Milch",
            allergens = listOf("milk"),
            traces = listOf("nuts"),
            nutriScore = "b",
            novaGroup = 4,
            ecoScore = "c",
            labels = listOf("organic"),
            categories = listOf("snacks"),
            imageUrl = null,
        )
    )

    /**
     * Composite-meal coverage: every 11th day logs each triple main as ONE
     * [FoodEntry] with 3 [FoodConstituent] rows (grams and macros sum to the
     * totals, per-row micros on 2 of 3 rows, composition signature stamped) —
     * distinct from [fullFoodEntries]' 3-row recipe-log groups. Deterministic
     * ids so re-seeding upserts instead of stacking.
     */
    fun compositeEntries(totalDays: Int = 365, today: LocalDate = LocalDate.now()): List<FoodEntry> {
        val zone = ZoneId.systemDefault()
        val grams = listOf(150.0, 200.0, 120.0)
        val notes = listOf(
            "Meal-prepped, split into three rows",
            "Leftovers from Sunday",
            "Cooked with the wok, oil not counted",
        )
        val out = mutableListOf<FoodEntry>()
        for (daysAgo in totalDays downTo 0) {
            if (daysAgo % 11 != 0) continue
            val parts = RecipeMains[(daysAgo / 11) % RecipeMains.size].toList()
            val constituents = parts.mapIndexed { i, template ->
                val row = FoodConstituent(
                    name = template.name,
                    calories = template.cal,
                    protein = template.p.toDouble(),
                    carbs = template.c.toDouble(),
                    fat = template.f.toDouble(),
                    servingSizeGrams = grams[i],
                    emoji = template.emoji,
                )
                // Micros on 2 of 3 rows: the real AI constituent shape is partial.
                if (i < 2) row.withMicros(microsForSeed(0xC0110L + daysAgo + i)) else row
            }
            val day = today.minusDays(daysAgo.toLong())
            out.add(
                FoodEntry(
                    id = UUID.nameUUIDFromBytes("chompass-seed-composite:$daysAgo".toByteArray()),
                    name = parts.joinToString(" + ") { it.name },
                    calories = constituents.sumOf { it.calories },
                    protein = round1(constituents.sumOf { it.protein }),
                    carbs = round1(constituents.sumOf { it.carbs }),
                    fat = round1(constituents.sumOf { it.fat }),
                    timestamp = day.atTime(18, 15).atZone(zone).toInstant(),
                    emoji = parts.first().emoji,
                    source = FoodSource.MANUAL,
                    mealType = MealType.DINNER.id,
                    servingSizeGrams = constituents.sumOf { it.servingSizeGrams },
                    customNote = if (daysAgo / 11 % 3 == 0) notes.random(Random(daysAgo)) else null,
                    constituents = constituents,
                    microsCompositionSignature = microsCompositionSignature(constituents),
                ),
            )
        }
        return out
    }

    /**
     * Two recipes in the Mealie import shape: `mealie:<slug>` source, declared
     * servings, recipe-level nutrition, and ingredients carrying only the
     * culinary amount/unit (zero base macros) — the exact signature the
     * recipe-builder fill-missing pill targets.
     */
    fun sampleMealieRecipes(now: Instant = Instant.now()): List<Recipe> {
        fun ing(name: String, amount: String, unit: String, emoji: String?) = RecipeIngredient(
            name = name,
            emoji = emoji,
            amountText = amount,
            unitText = unit,
            baseCalories = 0,
            baseProtein = 0.0,
            baseCarbs = 0.0,
            baseFat = 0.0,
        )
        return listOf(
            Recipe(
                name = "Mealie French toast",
                emoji = "\uD83C\uDF5E",
                mealType = MealType.BREAKFAST.id,
                createdAt = now.minusSeconds(86_400L * 12),
                source = "mealie:french-toast",
                servingsPerRecipe = 4.0,
                nutritionCalories = 1_480,
                nutritionProtein = 52.0,
                nutritionCarbs = 196.0,
                nutritionFat = 48.0,
                nutritionFiber = 8.0,
                nutritionSugar = 44.0,
                nutritionSodium = 940.0,
                ingredients = listOf(
                    ing("Bread", "8", "slices", "\uD83C\uDF5E"),
                    ing("Eggs", "3", "pcs", "\uD83E\uDD5A"),
                    ing("Milk", "200", "ml", "\uD83E\uDD5B"),
                    ing("Cinnamon", "2", "g", null),
                ),
            ),
            Recipe(
                name = "Mealie lentil soup",
                emoji = "\uD83C\uDF72",
                mealType = MealType.DINNER.id,
                createdAt = now.minusSeconds(86_400L * 5),
                source = "mealie:lentil-soup",
                servingsPerRecipe = 4.0,
                nutritionCalories = 1_120,
                nutritionProtein = 62.0,
                nutritionCarbs = 148.0,
                nutritionFat = 22.0,
                nutritionFiber = 34.0,
                nutritionSugar = 14.0,
                nutritionSodium = 2_100.0,
                ingredients = listOf(
                    ing("Red lentils", "300", "g", "\uD83E\uDD56"),
                    ing("Carrots", "2", "pcs", "\uD83E\uDD55"),
                    ing("Vegetable stock", "800", "ml", null),
                ),
            ),
        )
    }

    /**
     * Three favorites the plain [sampleFavorites] set does not cover: full
     * micros, a cup serving option + note, and a composite meal with a stamped
     * composition signature. Names stay distinct from [sampleFavorites]
     * (favorite identity is the lowercased name).
     */
    fun richFavorites(now: Instant = Instant.now()): List<FoodEntry> {
        val micros = withFullMicros(
            FoodEntry(
                name = "Seed micros bowl",
                calories = 520,
                protein = 34.0,
                carbs = 52.0,
                fat = 18.0,
                timestamp = now.minusSeconds(86_400L * 11),
                emoji = "\uD83E\uDD63",
                source = FoodSource.TEXT_INPUT,
                mealType = MealType.LUNCH.id,
            ),
            seed = 0xF4B0,
        )
        val cup = withServingUnits(
            FoodEntry(
                name = "Seed cup granola",
                calories = 430,
                protein = 12.0,
                carbs = 66.0,
                fat = 14.0,
                timestamp = now.minusSeconds(86_400L * 12),
                emoji = "\uD83E\uDD63",
                source = FoodSource.BARCODE,
                mealType = MealType.BREAKFAST.id,
                customNote = "One and a half cups, weighed once",
            )
        )
        val constituents = RecipeMains.first().toList().mapIndexed { i, template ->
            FoodConstituent(
                name = template.name,
                calories = template.cal,
                protein = template.p.toDouble(),
                carbs = template.c.toDouble(),
                fat = template.f.toDouble(),
                servingSizeGrams = listOf(150.0, 200.0, 120.0)[i],
                emoji = template.emoji,
            )
        }
        val composite = FoodEntry(
            name = "Seed composite plate",
            calories = constituents.sumOf { it.calories },
            protein = round1(constituents.sumOf { it.protein }),
            carbs = round1(constituents.sumOf { it.carbs }),
            fat = round1(constituents.sumOf { it.fat }),
            timestamp = now.minusSeconds(86_400L * 13),
            emoji = constituents.first().emoji,
            source = FoodSource.MANUAL,
            mealType = MealType.DINNER.id,
            servingSizeGrams = constituents.sumOf { it.servingSizeGrams },
            constituents = constituents,
            microsCompositionSignature = microsCompositionSignature(constituents),
        )
        return listOf(micros, cup, composite)
    }

    /** Three daily notes (today, −10, −25) built with the stable per-day id. */
    fun sampleNotes(today: LocalDate = LocalDate.now()): List<DailyNote> = listOf(
        today to "Felt strong today. Hit the protein target without a shake and walked after lunch.",
        today.minusDays(10) to "Late dinner, so the day came in over target. Logged everything anyway, no guilt.",
        today.minusDays(25) to "Rest day: ate close to maintenance and the tape says the waist is holding.",
    ).map { (date, text) -> DailyNote(id = DailyNote.idFor(date), date = date, text = text) }

    /**
     * Default meal catalog plus one custom "Seed Brunch" slot (id matches
     * [MealCatalog.CUSTOM_ID_REGEX]; validated: the start keeps the ≥15 min gap
     * to the builtin lunch window).
     */
    fun sampleMealCatalog(): MealCatalog = MealCatalog().let { catalog ->
        catalog.copy(
            meals = catalog.meals + MealDef(
                id = SEED_CUSTOM_MEAL_ID,
                label = "Seed Brunch",
                startMinutes = 690,
                enabled = true,
            ),
        )
    }

    /**
     * Caffeine + nicotine preset catalogs: builtins in enum order, one renamed
     * builtin, and one custom preset each (`t_` hex id, within MAX_CUSTOM).
     */
    fun trackerPresets(): Pair<HabitPresetCatalog, HabitPresetCatalog> {
        val caffeine = HabitPresetDomain.CAFFEINE.defaultCatalog.copy(
            presets = HabitPresetDomain.CAFFEINE.defaultCatalog.presets.map {
                if (it.id == CaffeineKind.COFFEE.storageKey) it.copy(label = "Flat White") else it
            } + HabitPreset(
                id = SEED_CAFFEINE_PRESET_ID,
                label = "Seed Latte",
                defaultMg = 42.0,
                milkKind = MilkKind.OAT.storageKey,
                milkMl = 30,
            ),
        )
        val nicotine = HabitPresetDomain.NICOTINE.defaultCatalog.copy(
            presets = HabitPresetDomain.NICOTINE.defaultCatalog.presets + HabitPreset(
                id = SEED_NICOTINE_PRESET_ID,
                label = "Seed Pouch",
                defaultCount = 2,
                defaultDoseMg = 6.0,
            ),
        )
        return caffeine to nicotine
    }

    /**
     * Weekly measurement snapshots covering all eight [BodyMeasurement.Site]s
     * with per-site linear trends + noise (the coverage twin of
     * [measurementSeries], which only logs the four tape sites).
     */
    fun allSitesMeasurementSeries(
        totalDays: Int = 180,
        seed: Long,
        today: LocalDate = LocalDate.now(),
    ): List<BodyMeasurement> {
        val zone = ZoneId.systemDefault()
        val rng = Random(seed)
        val trends = mapOf(
            BodyMeasurement.Site.NECK to (39.0 to 38.0),
            BodyMeasurement.Site.WAIST to (88.0 to 82.5),
            BodyMeasurement.Site.HIPS to (104.0 to 100.5),
            BodyMeasurement.Site.CHEST to (100.0 to 97.0),
            BodyMeasurement.Site.UPPER_ARM to (34.0 to 32.8),
            BodyMeasurement.Site.THIGH to (58.0 to 56.0),
            BodyMeasurement.Site.CALF to (38.0 to 37.2),
            BodyMeasurement.Site.WRIST to (17.6 to 17.6),
        )
        val out = mutableListOf<BodyMeasurement>()
        for (daysAgo in (totalDays - 1) downTo 0 step 7) {
            val day = today.minusDays(daysAgo.toLong())
            val progress = (totalDays - 1 - daysAgo).toDouble() / (totalDays - 1)
            val ts = day.atTime(7, rng.nextInt(0, 60)).atZone(zone).toInstant()
            var row = BodyMeasurement(date = ts)
            BodyMeasurement.Site.entries.forEach { site ->
                val (start, end) = trends.getValue(site)
                val trend = start - (start - end) * progress
                row = row.setting(site, round1(trend + rng.nextDouble(-0.3, 0.3)))
            }
            out.add(row)
        }
        return out
    }

    /** [values] mapped onto a constituent row (per-row micros, #86). */
    private fun FoodConstituent.withMicros(values: MicronutrientValues): FoodConstituent = copy(
        sugar = values.sugar,
        addedSugar = values.addedSugar,
        fiber = values.fiber,
        saturatedFat = values.saturatedFat,
        monounsaturatedFat = values.monounsaturatedFat,
        polyunsaturatedFat = values.polyunsaturatedFat,
        cholesterol = values.cholesterol,
        sodium = values.sodium,
        potassium = values.potassium,
        transFat = values.transFat,
        calcium = values.calcium,
        iron = values.iron,
        magnesium = values.magnesium,
        zinc = values.zinc,
        vitaminA = values.vitaminA,
        vitaminC = values.vitaminC,
        vitaminD = values.vitaminD,
        vitaminB12 = values.vitaminB12,
        vitaminE = values.vitaminE,
        vitaminK = values.vitaminK,
        folate = values.folate,
        omega3 = values.omega3,
        caffeine = values.caffeine,
    )

    /** The two triple main courses [fullFoodEntries] logs as 3-row recipe groups
     *  and [compositeEntries] folds into one composite row. */
    private val RecipeMains = listOf(
        Triple(
            MealTemplate("Chicken thigh", 240, 28, 0, 14, "\uD83C\uDF57", fiber = 0.0, sodium = 90.0),
            MealTemplate("Brown rice", 220, 5, 46, 2, "\uD83C\uDF5A", fiber = 3.0, sodium = 8.0),
            MealTemplate("Roasted broccoli", 80, 4, 12, 3, "\uD83E\uDD66", fiber = 4.0, sodium = 30.0),
        ),
        Triple(
            MealTemplate("Tofu", 180, 16, 6, 10, "\uD83E\uDDC6", fiber = 1.0, sodium = 20.0),
            MealTemplate("Soba noodles", 250, 8, 48, 2, "\uD83C\uDF5C", fiber = 2.0, sodium = 240.0),
            MealTemplate("Peanut sauce", 140, 4, 8, 11, "\uD83E\uDD5C", fiber = 1.0, sodium = 310.0),
        ),
    )

    /** Custom meal-slot id used by [sampleMealCatalog] and the planned-row seeder. */
    const val SEED_CUSTOM_MEAL_ID = "c_5eed0001"

    /** Custom tracker presets seeded by [trackerPresets] (valid `t_` hex ids). */
    const val SEED_CAFFEINE_PRESET_ID = "t_0f1e2d3c"
    const val SEED_NICOTINE_PRESET_ID = "t_0f1e2d3d"

    /** 1×1 PNG for the seeded Coach image bubble (valid PNG, 70 bytes). */
    internal const val ONE_PIXEL_PNG_BASE64 =
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFAAH/q842iQAAAABJRU5ErkJggg=="
}

/** 1-decimal rounding shared by the coverage generators (sheet preview convention). */
private fun round1(value: Double): Double = (value * 10.0).roundToInt() / 10.0

private data class MealTemplate(
    val name: String,
    val cal: Int,
    val p: Int,
    val c: Int,
    val f: Int,
    val emoji: String,
    val fiber: Double? = null,
    val sodium: Double? = null,
    val sugar: Double? = null,
)
