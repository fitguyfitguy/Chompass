package app.chompass.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServingUnitHeuristicsTest {
    @Test
    fun matchingRule_matchesWholeWordKeywords() {
        assertEquals("pizza", ServingUnitHeuristics.matchingRule("Pepperoni pizza")?.id)
        assertEquals("egg", ServingUnitHeuristics.matchingRule("Boiled egg")?.id)
        assertEquals("burger", ServingUnitHeuristics.matchingRule("Cheeseburger")?.id)
    }

    @Test
    fun matchingRule_avoidsSubstringFalsePositives() {
        assertNull(ServingUnitHeuristics.matchingRule("Eggplant parmesan"))
        // "cheesecake" contains "cake" as a substring but not as a word token
        assertNull(ServingUnitHeuristics.matchingRule("Cheesecake"))
    }

    @Test
    fun matchingRule_handlesPluralsAndMultiWordKeywords() {
        assertEquals("sandwich", ServingUnitHeuristics.matchingRule("Turkey sandwiches")?.id)
        assertEquals("icecream", ServingUnitHeuristics.matchingRule("Vanilla ice cream")?.id)
        assertEquals("hotdog", ServingUnitHeuristics.matchingRule("Hot dog with mustard")?.id)
        assertEquals("bar", ServingUnitHeuristics.matchingRule("Chocolate protein bar")?.id)
    }

    @Test
    fun matchingRule_returnsFirstTableHitInRuleOrder() {
        // "bread" appears before "sandwich" in RULES; plain toast should hit bread
        assertEquals("bread", ServingUnitHeuristics.matchingRule("Toast")?.id)
        assertEquals("slice", ServingUnitHeuristics.matchingRule("Toast")?.unit)
        assertEquals(30.0, ServingUnitHeuristics.matchingRule("Toast")!!.defaultGramsPerUnit, 0.001)
    }

    @Test
    fun matchingRule_returnsNullForUnknownFoods() {
        assertNull(ServingUnitHeuristics.matchingRule(""))
        assertNull(ServingUnitHeuristics.matchingRule("!!!"))
        assertNull(ServingUnitHeuristics.matchingRule("Grilled salmon fillet"))
    }

    @Test
    fun rules_haveStableUniqueIds() {
        val ids = ServingUnitHeuristics.RULES.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ServingUnitHeuristics.RULES.all { it.keywords.isNotEmpty() })
        assertTrue(ServingUnitHeuristics.RULES.all { it.defaultGramsPerUnit > 0 })
    }

    @Test
    fun suggestedRuleFeedsManualEntryServingMath() {
        // ManualEntryDialog turns the suggested rule into a ServingUnitOption
        // and derives serving grams as quantity x gramsPerUnit (mirrors the AI
        // heuristic path in FoodAnalysisService).
        val rule = ServingUnitHeuristics.matchingRule("Pizza margherita")!!
        val option = ServingUnitOption(unit = rule.unit, gramsPerUnit = rule.defaultGramsPerUnit)
        assertEquals("slice", option.unit)
        assertEquals(120.0, option.gramsPerUnit, 0.001)

        val quantity = ServingUnitOption.parseQuantity("2")
        assertEquals(240.0, quantity!! * option.gramsPerUnit, 0.001)
    }
}
