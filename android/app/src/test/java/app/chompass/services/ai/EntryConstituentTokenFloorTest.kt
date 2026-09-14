package app.chompass.services.ai

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Response-token floors for entry ops that embed the constituents schema:
 * micros rows (#86) and macros-only rows (#97 — local OpenAI-compatible
 * servers keep the 1024 default cap, and a 12-row macros reply lands past
 * it, surfacing as "Could not understand the AI response").
 */
class EntryConstituentTokenFloorTest {
    @Test
    fun constituentOpsGetFloorWhenUserCapLower() {
        assertEquals(4096, floorResponseTokensForOp("analyzeText", 1024, EntryConstituentPromptKind.MICROS))
        assertEquals(4096, floorResponseTokensForOp("analyzeAuto", 1024, EntryConstituentPromptKind.MICROS))
        assertEquals(4096, floorResponseTokensForOp("analyzeFood", 512, EntryConstituentPromptKind.MICROS))
        assertEquals(4096, floorResponseTokensForOp("analyzeFoodMulti", 1024, EntryConstituentPromptKind.MICROS))
    }

    @Test
    fun macrosRowsGetTheSmaller97Floor() {
        assertEquals(2048, floorResponseTokensForOp("analyzeText", 1024, EntryConstituentPromptKind.MACROS))
        assertEquals(2048, floorResponseTokensForOp("analyzeFoodMulti", 512, EntryConstituentPromptKind.MACROS))
        // The micros floor is not applied to macros-only legs: a cap between
        // the two floors passes through untouched.
        assertEquals(4095, floorResponseTokensForOp("analyzeFood", 4095, EntryConstituentPromptKind.MACROS))
    }

    @Test
    fun userCapAtOrAboveFloorWins() {
        assertEquals(8192, floorResponseTokensForOp("analyzeText", 8192, EntryConstituentPromptKind.MICROS))
        assertEquals(4096, floorResponseTokensForOp("analyzeFood", 4096, EntryConstituentPromptKind.MICROS))
        assertEquals(3000, floorResponseTokensForOp("analyzeText", 3000, EntryConstituentPromptKind.MACROS))
    }

    @Test
    fun nonConstituentOpsAndLeanKeepUserCap() {
        assertEquals(1024, floorResponseTokensForOp("analyzeLabel", 1024, EntryConstituentPromptKind.MICROS))
        assertEquals(1024, floorResponseTokensForOp("inferServing", 1024, EntryConstituentPromptKind.MACROS))
        // Constituents toggle off and the on-device lean schema stay uncapped
        // by the constituent floors.
        assertEquals(1024, floorResponseTokensForOp("analyzeText", 1024, EntryConstituentPromptKind.NONE))
        assertEquals(256, floorResponseTokensForOp("analyzeFoodMulti", 256, EntryConstituentPromptKind.NONE))
        assertEquals(256, floorResponseTokensForOp("analyzeFoodMulti", 256, EntryConstituentPromptKind.LEAN))
    }
}
