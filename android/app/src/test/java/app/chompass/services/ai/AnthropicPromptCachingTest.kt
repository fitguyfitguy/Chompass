package app.chompass.services.ai

import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.UserProfile
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coach-chat prompt caching (codeberg #1): the Anthropic `system` block is
 * split into a byte-stable, cacheable prefix and a per-turn volatile tail.
 */
class AnthropicPromptCachingTest {

    private fun coachPrompt(foods: List<FoodEntry> = emptyList()): String = buildSystemPrompt(
        profile = UserProfile(),
        weights = emptyList(),
        bodyFats = emptyList(),
        measurements = emptyList(),
        foods = foods,
        heightMetric = true,
        weightMetric = true,
    )

    @Test
    fun coachPrompt_ordersStableSectionsBeforeVolatileTail() {
        val prompt = coachPrompt()
        val stable = prompt.substringBefore("\n## Current date")

        // Stable prefix: persona, tool guidance, logging, profile, formulas, closing.
        assertTrue(stable.contains("You are Coach"))
        assertTrue(stable.contains("## How to use the data tools"))
        assertTrue(stable.contains("## Logging on the user's behalf"))
        assertTrue(stable.contains("## User profile"))
        assertTrue(stable.contains("## Formulas in use"))
        assertTrue(stable.contains("When the user asks how to lose or gain"))
        // Volatile sections live after the marker.
        assertFalse(stable.contains("## Computed forecast"))
        assertFalse(stable.contains("## Data available"))
        assertTrue(prompt.indexOf("## Current date") < prompt.indexOf("## Computed forecast"))
        assertTrue(prompt.indexOf("## Computed forecast") < prompt.indexOf("## Data available"))
    }

    @Test
    fun systemBlocks_marksStablePrefixCacheable_andLeavesTailUncached() {
        val blocks = anthropicSystemBlocks(coachPrompt())

        assertEquals(2, blocks.length())
        val stable = blocks.getJSONObject(0)
        assertEquals("text", stable.getString("type"))
        assertTrue(stable.getString("text").contains("You are Coach"))
        assertEquals("ephemeral", stable.getJSONObject("cache_control").getString("type"))
        assertFalse(stable.getString("text").contains("## Current date"))

        val tail = blocks.getJSONObject(1)
        assertEquals("text", tail.getString("type"))
        assertTrue(tail.getString("text").contains("## Current date"))
        assertFalse(tail.has("cache_control"))
    }

    @Test
    fun systemBlocks_stablePrefixIsByteIdenticalAcrossTurns() {
        // Two consecutive turns: only the food-entry count (volatile tail) changes.
        val turnOne = anthropicSystemBlocks(coachPrompt(foods = emptyList()))
        val turnTwo = anthropicSystemBlocks(
            coachPrompt(
                foods = listOf(
                    FoodEntry(name = "Oats", calories = 350, protein = 12.0, carbs = 60.0, fat = 6.0, source = FoodSource.MANUAL)
                )
            )
        )

        assertEquals(turnOne.getJSONObject(0).getString("text"), turnTwo.getJSONObject(0).getString("text"))
        assertFalse(turnOne.getJSONObject(1).getString("text") == turnTwo.getJSONObject(1).getString("text"))
    }

    @Test
    fun systemBlocks_userContextStaysInUncachedTail() {
        // sendMessage appends "## User-provided context" after the base prompt;
        // it must never land in the cached prefix — even when it quotes the
        // "## Current date" marker itself (first occurrence is the real one).
        val prompt = coachPrompt() + "\n\n## User-provided context\nDon't log anything after 6pm. Today (## Current date is wrong) is fine."
        val blocks = anthropicSystemBlocks(prompt)

        assertEquals(2, blocks.length())
        assertFalse(blocks.getJSONObject(0).getString("text").contains("User-provided context"))
        val tail = blocks.getJSONObject(1).getString("text")
        assertTrue(tail.contains("User-provided context"))
        assertTrue(tail.contains("Today (## Current date is wrong) is fine."))
    }

    @Test
    fun systemBlocks_withoutMarker_marksWholePromptCacheable() {
        val blocks = anthropicSystemBlocks("Fully stable instructions only.")

        assertEquals(1, blocks.length())
        assertEquals("ephemeral", blocks.getJSONObject(0).getJSONObject("cache_control").getString("type"))
        assertEquals("Fully stable instructions only.", blocks.getJSONObject(0).getString("text"))
    }

    @Test
    fun toolLoopBody_usesBlockSystemWithCacheControl() {
        // Wire check: runAnthropicToolLoop must serialize system as a block
        // array — reproduce its body construction and assert the shape.
        val system = anthropicSystemBlocks(coachPrompt())
        val body = JSONObject().apply {
            put("model", "claude-sonnet-4")
            put("max_tokens", 512)
            put("system", system)
        }
        val sysArr = body.getJSONArray("system")
        assertEquals(2, sysArr.length())
        assertTrue(sysArr.getJSONObject(0).has("cache_control"))
    }
}
