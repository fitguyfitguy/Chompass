package app.chompass.parity

import app.chompass.models.DayTargets
import app.chompass.models.GoalJournalEntry
import app.chompass.models.MacroPlan
import app.chompass.models.MacroPlanResolver
import java.time.LocalDate
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Golden vectors from `testdata/parity/macro-plan-expected.json` — shared with
 * the PWA `chompass-core/__tests__/macro-plan.test.js`. Both runners assert the
 * same table (MACRO-CYCLE-A/B/D, Codeberg #60). Update the JSON when
 * resolution or averaging semantics change.
 */
@RunWith(Parameterized::class)
class MacroPlanParityTest(
    private val scenarioId: String,
    private val scenario: JSONObject,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val baseTargets: DayTargets

        init {
            val root = ParityFixtures.readJson("macro-plan-expected.json")
            val base = root.getJSONObject("base")
            baseTargets = DayTargets(
                calories = base.getInt("calories"),
                proteinG = base.getInt("proteinG"),
                carbsG = base.getInt("carbsG"),
                fatG = base.getInt("fatG"),
            )
        }

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): List<Array<Any>> {
            val root = ParityFixtures.readJson("macro-plan-expected.json")
            val scenarios = root.getJSONArray("scenarios")
            return (0 until scenarios.length()).map { i ->
                val s = scenarios.getJSONObject(i)
                arrayOf(s.getString("id"), s)
            }
        }
    }

    @Test
    fun matchesSharedParityFixture() {
        val plan: MacroPlan? = scenario.optJSONObject("plan")?.let {
            json.decodeFromString(MacroPlan.serializer(), it.toString())
        }
        val expect = scenario.getJSONObject("expect")
        when (scenario.getString("kind")) {
            "resolve" -> {
                val r = MacroPlanResolver.resolve(plan, baseTargets, LocalDate.parse(scenario.getString("date")))
                if (expect.has("profileId")) {
                    if (expect.isNull("profileId")) assertNull(scenarioId, r.profileId)
                    else assertEquals(scenarioId, expect.getString("profileId"), r.profileId)
                }
                if (expect.has("profileName")) {
                    if (expect.isNull("profileName")) assertNull(scenarioId, r.profileName)
                    else assertEquals(scenarioId, expect.getString("profileName"), r.profileName)
                }
                assertTargets(expect, r.targets)
            }
            "averageForward" -> {
                val windowDays = if (scenario.has("windowDays")) scenario.getInt("windowDays") else null
                val r = MacroPlanResolver.averageForward(
                    plan, baseTargets, LocalDate.parse(scenario.getString("today")), windowDays,
                )
                assertTargets(expect, r)
            }
            "resolveJournaled" -> {
                val entries = json.decodeFromString(
                    ListSerializer(GoalJournalEntry.serializer()),
                    scenario.getJSONArray("entries").toString(),
                )
                val r = MacroPlanResolver.resolveJournaled(
                    entries,
                    plan,
                    baseTargets,
                    LocalDate.parse(scenario.getString("date")),
                    LocalDate.parse(scenario.getString("today")),
                )
                if (expect.has("profileId")) {
                    if (expect.isNull("profileId")) assertNull(scenarioId, r.profileId)
                    else assertEquals(scenarioId, expect.getString("profileId"), r.profileId)
                }
                if (expect.has("profileName")) {
                    if (expect.isNull("profileName")) assertNull(scenarioId, r.profileName)
                    else assertEquals(scenarioId, expect.getString("profileName"), r.profileName)
                }
                assertTargets(expect, r.targets)
            }
            "journalAverage" -> {
                val entries = json.decodeFromString(
                    ListSerializer(GoalJournalEntry.serializer()),
                    scenario.getJSONArray("entries").toString(),
                )
                val r = MacroPlanResolver.journalAverage(
                    entries, LocalDate.parse(scenario.getString("from")), LocalDate.parse(scenario.getString("to")),
                )
                if (expect.optBoolean("null")) {
                    assertNull(scenarioId, r)
                } else {
                    assertTargets(expect, r!!)
                }
            }
            else -> error("Unknown scenario kind in macro-plan-expected.json: $scenarioId")
        }
    }

    private fun assertTargets(expect: JSONObject, targets: DayTargets) {
        assertEquals(scenarioId, expect.getInt("calories"), targets.calories)
        assertEquals(scenarioId, expect.getInt("proteinG"), targets.proteinG)
        assertEquals(scenarioId, expect.getInt("carbsG"), targets.carbsG)
        assertEquals(scenarioId, expect.getInt("fatG"), targets.fatG)
    }
}
