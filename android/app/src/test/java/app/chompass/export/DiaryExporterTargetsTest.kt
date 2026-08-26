package app.chompass.export

import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.GoalJournalEntry
import app.chompass.models.GoalJournalSource
import app.chompass.models.MacroDayProfile
import app.chompass.models.MacroPlan
import app.chompass.models.MacroPlanMode
import app.chompass.models.MealType
import app.chompass.models.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Per-day targets in diary exports (Codeberg #60 phase 3): journal-first — a
 * journaled day exports its frozen targets, gaps resolve live from the plan,
 * plan-off exports keep the single current-target shape.
 */
class DiaryExporterTargetsTest {
    private val today: LocalDate = LocalDate.of(2026, 7, 24)
    private val zone = java.time.ZoneId.systemDefault()

    private fun entry(day: LocalDate, calories: Int) = FoodEntry(
        name = "Meal $day",
        calories = calories,
        protein = 10.0,
        carbs = 20.0,
        fat = 5.0,
        source = FoodSource.MANUAL,
        mealType = MealType.LUNCH.id,
        timestamp = day.atTime(12, 0).atZone(zone).toInstant(),
    )

    private fun profile(plan: MacroPlan?) = UserProfile(
        customCalories = 2400,
        customProtein = 150,
        customCarbs = 250,
        customFat = 70,
        macroPlan = plan,
    )

    private fun cyclePlan() = MacroPlan(
        enabled = true,
        profiles = listOf(
            MacroDayProfile("t", "Training day", 2800, 170, 350, 78),
            MacroDayProfile("r", "Rest day", 2100, 150, 160, 78),
        ),
        mode = MacroPlanMode.CYCLE,
        defaultProfileId = "r",
        cyclePattern = listOf("t", "t", "r"),
        cycleAnchorDay = "2026-07-20",
    )

    private fun journalEntry(date: LocalDate, calories: Int, protein: Int, carbs: Int, fat: Int) = GoalJournalEntry(
        date = date.toString(),
        calories = calories,
        proteinG = protein,
        carbsG = carbs,
        fatG = fat,
        profileId = "r",
        profileName = "Rest day",
        updatedAtMillis = 1_000L,
        source = GoalJournalSource.PLAN,
    )

    @Test
    fun jsonExportUsesPerDayTargetsFromJournalThenResolver() {
        // 2026-07-23 journaled (rest), 2026-07-24 unjournaled: cycle offset 4 -> pattern[1] = t.
        val journal = listOf(journalEntry(LocalDate.of(2026, 7, 23), 2100, 150, 160, 78))
        val (_, content) = DiaryExporter.build(
            entries = listOf(entry(LocalDate.of(2026, 7, 23), 1800), entry(LocalDate.of(2026, 7, 24), 1900)),
            start = LocalDate.of(2026, 7, 23),
            end = LocalDate.of(2026, 7, 24),
            format = DiaryFormat.JSON,
            profile = profile(cyclePlan()),
            mealDisplay = { it },
            goalJournal = journal,
            today = today,
        ) ?: error("expected export")

        val doc = Json.parseToJsonElement(content).jsonObject
        val days = doc["days"]!!.jsonArray.map { it.jsonObject }
        assertEquals(2, days.size)
        val day23 = days[0]["targets"]!!.jsonObject
        assertEquals(2100, day23["calories"]!!.toString().toInt())
        assertEquals(150.0, day23["protein_g"]!!.toString().toDouble(), 0.0)
        val day24 = days[1]["targets"]!!.jsonObject
        assertEquals(2800, day24["calories"]!!.toString().toInt())
        assertEquals(170.0, day24["protein_g"]!!.toString().toDouble(), 0.0)
        // Remaining follows the day's own targets.
        assertEquals(300, days[0]["remaining"]!!.jsonObject["calories"]!!.toString().toInt())
        assertEquals(900, days[1]["remaining"]!!.jsonObject["calories"]!!.toString().toInt())
    }

    @Test
    fun planOffExportsStayOnTheSingleCurrentTargetShape() {
        val (_, content) = DiaryExporter.build(
            entries = listOf(entry(LocalDate.of(2026, 7, 23), 1800), entry(LocalDate.of(2026, 7, 24), 1900)),
            start = LocalDate.of(2026, 7, 23),
            end = LocalDate.of(2026, 7, 24),
            format = DiaryFormat.JSON,
            profile = profile(plan = null),
            mealDisplay = { it },
            today = today,
        ) ?: error("expected export")

        val days = Json.parseToJsonElement(content).jsonObject["days"]!!.jsonArray.map { it.jsonObject }
        assertEquals(2400, days[0]["targets"]!!.jsonObject["calories"]!!.toString().toInt())
        assertEquals(2400, days[1]["targets"]!!.jsonObject["calories"]!!.toString().toInt())
    }

    @Test
    fun markdownShowsPerDayTargets() {
        val journal = listOf(journalEntry(LocalDate.of(2026, 7, 23), 2100, 150, 160, 78))
        val (_, content) = DiaryExporter.build(
            entries = listOf(entry(LocalDate.of(2026, 7, 23), 1800), entry(LocalDate.of(2026, 7, 24), 1900)),
            start = LocalDate.of(2026, 7, 23),
            end = LocalDate.of(2026, 7, 24),
            format = DiaryFormat.MARKDOWN,
            profile = profile(cyclePlan()),
            mealDisplay = { it },
            goalJournal = journal,
            today = today,
        ) ?: error("expected export")

        assertTrue(content.contains("- Calories: 1800 / 2100 kcal"))
        assertTrue(content.contains("- Calories: 1900 / 2800 kcal"))
        assertFalse(content.contains("2800 / 2100"))
    }

    @Test
    fun nullProfileExportsZeroTargetsEvenWithAJournal() {
        val journal = listOf(journalEntry(LocalDate.of(2026, 7, 23), 2100, 150, 160, 78))
        val (_, content) = DiaryExporter.build(
            entries = listOf(entry(LocalDate.of(2026, 7, 23), 1800)),
            start = LocalDate.of(2026, 7, 23),
            end = LocalDate.of(2026, 7, 23),
            format = DiaryFormat.JSON,
            profile = null,
            mealDisplay = { it },
            goalJournal = journal,
            today = today,
        ) ?: error("expected export")

        val days = Json.parseToJsonElement(content).jsonObject["days"]!!.jsonArray.map { it.jsonObject }
        // No profile: the journaled day still keeps its frozen numbers.
        assertEquals(2100, days[0]["targets"]!!.jsonObject["calories"]!!.toString().toInt())
    }
}
