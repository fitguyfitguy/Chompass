package app.chompass.parity

import app.chompass.models.AutoBalanceMacro
import app.chompass.models.MacroBalance
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Golden vectors from `testdata/parity/macro-balance-expected.json` — shared
 * with the PWA `chompass-core/__tests__/macro-balance.test.js`. Both runners
 * assert the same table (macro balance write-path invariant). Update the JSON
 * when the rebalance semantics change.
 */
@RunWith(Parameterized::class)
class MacroBalanceParityTest(
    private val scenarioId: String,
    private val scenario: JSONObject,
) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): List<Array<Any>> {
            val root = ParityFixtures.readJson("macro-balance-expected.json")
            val scenarios = root.getJSONArray("scenarios")
            return (0 until scenarios.length()).map { i ->
                val s = scenarios.getJSONObject(i)
                arrayOf(s.getString("id"), s)
            }
        }
    }

    @Test
    fun rebalanceMatchesGoldenVector() {
        val input = scenario.getJSONObject("input")
        val expected = scenario.getJSONObject("expected")
        val locked = scenario.getJSONArray("locked")
            .let { arr -> (0 until arr.length()).map { arr.getString(it) }.toSet() }
        val result = MacroBalance.rebalance(
            calories = scenario.getInt("calories"),
            protein = input.getInt("protein"),
            carbs = input.getInt("carbs"),
            fat = input.getInt("fat"),
            mode = when (scenario.getString("mode")) {
                "keto" -> MacroBalance.Mode.KETO
                else -> MacroBalance.Mode.STANDARD
            },
            locked = locked.map { AutoBalanceMacro.valueOf(it.uppercase()) }.toSet(),
            fatFloorG = scenario.getInt("fatFloorG"),
        )
        assertEquals("$scenarioId protein", expected.getInt("protein"), result.protein)
        assertEquals("$scenarioId carbs", expected.getInt("carbs"), result.carbs)
        assertEquals("$scenarioId fat", expected.getInt("fat"), result.fat)
    }
}
