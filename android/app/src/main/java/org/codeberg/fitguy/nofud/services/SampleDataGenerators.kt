package org.codeberg.fitguy.nofud.services

import org.codeberg.fitguy.nofud.models.BodyFatEntry
import org.codeberg.fitguy.nofud.models.FoodEntry
import org.codeberg.fitguy.nofud.models.FoodSource
import org.codeberg.fitguy.nofud.models.MealType
import org.codeberg.fitguy.nofud.models.WeightEntry
import org.codeberg.fitguy.nofud.services.health.DebugActivityDay
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
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
}

private data class MealTemplate(
    val name: String,
    val cal: Int,
    val p: Int,
    val c: Int,
    val f: Int,
    val emoji: String,
)
