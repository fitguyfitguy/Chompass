package app.chompass.services

import app.chompass.models.BodyFatEntry
import app.chompass.models.BodyMeasurement
import app.chompass.models.ChatMessage
import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.MealType
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

            fun add(template: MealTemplate, hour: Int, meal: MealType) {
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

            add(breakfast.random(rng), hour = 8, meal = MealType.BREAKFAST)
            add(lunch.random(rng), hour = 13, meal = MealType.LUNCH)
            add(dinner.random(rng), hour = 19, meal = MealType.DINNER)
            if (rng.nextBoolean()) add(snacks.random(rng), hour = 16, meal = MealType.SNACK)
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
        val recipeMains = listOf(
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

        val out = mutableListOf<FoodEntry>()
        for (daysAgo in totalDays downTo 0) {
            val day = today.minusDays(daysAgo.toLong())
            if (rng.nextInt(20) == 0) continue

            fun add(
                template: MealTemplate,
                hour: Int,
                meal: MealType,
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

            add(breakfast.random(rng), hour = 8, meal = MealType.BREAKFAST, source = sourceFor(daysAgo), withMicros = rng.nextInt(3) != 0)
            add(lunch.random(rng), hour = 13, meal = MealType.LUNCH, source = sourceFor(daysAgo), withMicros = rng.nextInt(3) != 0)
            if (daysAgo % 11 == 0) {
                val recipeLogId = UUID.randomUUID()
                val parts = recipeMains.random(rng)
                add(parts.first, hour = 18, meal = MealType.DINNER, source = FoodSource.MANUAL, recipeLogId = recipeLogId, withMicros = true)
                add(parts.second, hour = 18, meal = MealType.DINNER, source = FoodSource.MANUAL, recipeLogId = recipeLogId, withMicros = true)
                add(parts.third, hour = 18, meal = MealType.DINNER, source = FoodSource.MANUAL, recipeLogId = recipeLogId, withMicros = true)
            } else {
                add(dinner.random(rng), hour = 19, meal = MealType.DINNER, source = sourceFor(daysAgo), withMicros = rng.nextInt(3) != 0)
            }
            if (rng.nextBoolean()) {
                add(snacks.random(rng), hour = 16, meal = MealType.SNACK, source = sourceFor(daysAgo), withMicros = rng.nextBoolean())
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
                mealType = MealType.BREAKFAST,
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
                mealType = MealType.LUNCH,
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
                mealType = MealType.DINNER,
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
                mealType = MealType.DINNER,
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
                    i < 3 -> MealType.BREAKFAST
                    i < 6 -> MealType.LUNCH
                    i < 8 -> MealType.DINNER
                    else -> MealType.SNACK
                },
                fiber = t.fiber,
                sodium = t.sodium,
                sugar = t.sugar,
            )
        }
    }

    fun sampleChat(now: Instant = Instant.now()): List<ChatMessage> {
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
        return turns.mapIndexed { i, (role, content) ->
            ChatMessage(
                role = role,
                content = content,
                timestamp = now.minusSeconds(120L * (turns.size - i).toLong()),
            )
        }
    }
}

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
