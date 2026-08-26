package app.chompass.parity

import app.chompass.models.DayTypeActiveStats
import app.chompass.models.GoalJournalEntry
import java.time.LocalDate
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class DayTypeActiveParityTest(
    private val scenarioId: String,
    private val scenario: JSONObject,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): List<Array<Any>> {
            val root = ParityFixtures.readJson("day-type-active-expected.json")
            val scenarios = root.getJSONArray("scenarios")
            return (0 until scenarios.length()).map { i ->
                val s = scenarios.getJSONObject(i)
                arrayOf(s.getString("id"), s)
            }
        }
    }

    @Test
    fun matchesSharedParityFixture() {
        val root = ParityFixtures.readJson("day-type-active-expected.json")
        val today = LocalDate.parse(root.getString("today"))
        val window = root.getInt("windowDays")
        val minSamples = root.getInt("minSamples")
        when (scenario.getString("kind")) {
            "compute", "mergeThenCompute" -> {
                val journal = decodeJournal(scenario.getJSONArray("journal").toString())
                val active = if (scenario.getString("kind") == "mergeThenCompute") {
                    DayTypeActiveStats.mergeDayTotals(
                        toIntMap(scenario.getJSONObject("healthConnectByDay")),
                        toIntMap(scenario.getJSONObject("manualByDay")),
                    )
                } else {
                    toIntMap(scenario.getJSONObject("activeByDay"))
                }
                val result = DayTypeActiveStats.compute(journal, active, today, window, minSamples)
                val expect = scenario.getJSONObject("expect")
                val averages = expect.getJSONObject("averages")
                assertEquals(scenarioId, averages.length(), result.byProfileId.size)
                averages.keys().forEach { id ->
                    val row = averages.getJSONObject(id)
                    val got = result.byProfileId[id]!!
                    assertEquals("$scenarioId $id avg", row.getInt("averageKcal"), got.averageKcal)
                    assertEquals("$scenarioId $id n", row.getInt("sampleCount"), got.sampleCount)
                }
                val typical = expect.getJSONObject("typical")
                typical.keys().forEach { id ->
                    assertEquals("$scenarioId typical $id", typical.getInt(id), result.typicalFor(id, minSamples))
                }
                result.byProfileId.keys.forEach { id ->
                    if (!typical.has(id)) {
                        assertEquals("$scenarioId no typical $id", null, result.typicalFor(id, minSamples))
                    }
                }
            }
            "resolveTypical" -> {
                val journal = decodeJournal(scenario.getJSONArray("journal").toString())
                val active = toIntMap(scenario.getJSONObject("activeByDay"))
                val stats = DayTypeActiveStats.compute(journal, active, today, window, minSamples)
                val cases = scenario.getJSONArray("cases")
                for (i in 0 until cases.length()) {
                    val c = cases.getJSONObject(i)
                    val viewed = if (c.isNull("viewedProfileId")) null else c.getString("viewedProfileId")
                    val r = DayTypeActiveStats.resolveTypical(
                        viewed, stats, c.getInt("blendedMeasured"), c.getInt("palEstimate"), minSamples,
                    )
                    assertEquals("$scenarioId[$i] kcal", c.getInt("expectKcal"), r.kcal)
                    assertEquals("$scenarioId[$i] flag", c.getBoolean("typicalIsDayType"), r.typicalIsDayType)
                }
            }
            "prune" -> {
                val pruned = DayTypeActiveStats.pruneHistory(
                    toIntMap(scenario.getJSONObject("map")),
                    today,
                    scenario.getInt("keepDays"),
                )
                val expectKeys = (0 until scenario.getJSONArray("expectKeys").length()).map {
                    scenario.getJSONArray("expectKeys").getString(it)
                }.toSet()
                assertEquals(scenarioId, expectKeys, pruned.keys)
                assertFalse(pruned.containsKey("2026-06-01"))
                assertTrue(pruned.containsKey("2026-08-20"))
            }
            else -> error("Unknown scenario kind: $scenarioId")
        }
    }

    private fun decodeJournal(raw: String): List<GoalJournalEntry> =
        json.decodeFromString(ListSerializer(GoalJournalEntry.serializer()), raw)

    private fun toIntMap(obj: JSONObject): Map<String, Int> {
        val out = mutableMapOf<String, Int>()
        obj.keys().forEach { out[it] = obj.getInt(it) }
        return out
    }
}
