package app.chompass.services.grounding

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroundingToolsTest {

    @Test
    fun finalizeGrounding_parsesComponentsAndSourceIds() = runBlocking {
        val tools = GroundingTools(
            usdaIndex = null,
            historyPool = emptyList(),
            prefs = null,
        )
        val args = JSONObject(
            """
            {
              "meal_name": "Eggs and toast",
              "emoji": "🍳",
              "components": [
                {"name":"Egg","source_id":"123","source_kind":"usda","grams":50},
                {"name":"Toast","reject_to_estimate":true}
              ]
            }
            """.trimIndent(),
        )
        val result = tools.execute("finalize_grounding", args)
        assertTrue(JSONObject(result).optBoolean("ok"))
        val fin = tools.lastFinalize
        assertNotNull(fin)
        assertEquals("Eggs and toast", fin!!.mealName)
        assertEquals(2, fin.components.size)
        assertEquals("123", fin.components[0].sourceId)
        assertEquals(50.0, fin.components[0].grams!!, 0.001)
        assertTrue(fin.components[1].rejectToEstimate)
        assertFalse(fin.components[0].rejectToEstimate)
    }

    @Test
    fun searchUsda_withoutIndex_returnsError() = runBlocking {
        val tools = GroundingTools(null, emptyList(), null)
        val raw = tools.execute("search_usda", JSONObject().put("query", "egg"))
        assertTrue(JSONObject(raw).has("error"))
    }

    @Test
    fun finalizeGrounding_marksUnseenSourceIdAsNeedsChoice() = runBlocking {
        val tools = GroundingTools(
            usdaIndex = null,
            historyPool = emptyList(),
            prefs = null,
        )
        tools.rememberSourceId("111")
        val args = JSONObject(
            """
            {
              "meal_name": "Banana",
              "components": [
                {"name":"Banana","source_id":"999","source_kind":"usda","grams":118}
              ]
            }
            """.trimIndent(),
        )
        val result = JSONObject(tools.execute("finalize_grounding", args))
        assertTrue(result.optBoolean("ok"))
        assertTrue(tools.lastFinalize!!.components[0].needsUserChoice)
        assertTrue(result.has("invalid_source_ids"))
    }

    @Test
    fun toolSchemas_coverAllNames() {
        for (name in GroundingTools.TOOL_NAMES) {
            val schema = GroundingTools.parametersSchema(name)
            assertEquals("object", schema.getString("type"))
            assertTrue(GroundingTools.TOOL_DESCRIPTIONS.containsKey(name))
        }
    }
}
