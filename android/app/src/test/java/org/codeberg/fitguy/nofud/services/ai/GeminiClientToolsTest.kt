package org.codeberg.fitguy.nofud.services.ai

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiClientToolsTest {

    @Test
    fun buildToolsArray_disabledWithoutFunctions_returnsNull() {
        assertNull(GeminiClient.buildToolsArray(enableGoogleSearch = false, functionDeclarations = null))
    }

    @Test
    fun buildToolsArray_searchOnly_includesGoogleSearchTool() {
        val tools = GeminiClient.buildToolsArray(enableGoogleSearch = true, functionDeclarations = null)!!

        assertEquals(1, tools.length())
        assertTrue(tools.getJSONObject(0).has("google_search"))
    }

    @Test
    fun buildToolsArray_searchAndFunctions_includesBothTools() {
        val declarations = JSONArray().put(
            JSONObject().apply {
                put("name", "get_data_summary")
                put("description", "summary")
                put("parameters", JSONObject().put("type", "object"))
            }
        )
        val tools = GeminiClient.buildToolsArray(enableGoogleSearch = true, functionDeclarations = declarations)!!

        assertEquals(2, tools.length())
        assertTrue(tools.getJSONObject(0).has("google_search"))
        assertTrue(tools.getJSONObject(1).has("functionDeclarations"))
        assertEquals(1, tools.getJSONObject(1).getJSONArray("functionDeclarations").length())
    }

    @Test
    fun buildToolsArray_functionsOnly_matchesCoachShape() {
        val declarations = JSONArray().put(
            JSONObject().apply {
                put("name", "get_data_summary")
                put("description", "summary")
                put("parameters", JSONObject().put("type", "object"))
            }
        )
        val tools = GeminiClient.buildToolsArray(enableGoogleSearch = false, functionDeclarations = declarations)!!

        assertEquals(1, tools.length())
        assertTrue(tools.getJSONObject(0).has("functionDeclarations"))
    }
}
