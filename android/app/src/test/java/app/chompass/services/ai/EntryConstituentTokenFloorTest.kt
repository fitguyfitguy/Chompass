package app.chompass.services.ai

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * #86 review fix: the constituents schema (22 micro fields per row) can exceed
 * the default 1024-token response cap on capped providers, so constituent
 * entry ops get a 4096 floor unless the user cap is already higher.
 */
class EntryConstituentTokenFloorTest {
    @Test
    fun constituentOpsGetFloorWhenUserCapLower() {
        assertEquals(4096, floorResponseTokensForOp("analyzeText", 1024, true))
        assertEquals(4096, floorResponseTokensForOp("analyzeAuto", 1024, true))
        assertEquals(4096, floorResponseTokensForOp("analyzeFood", 512, true))
        assertEquals(4096, floorResponseTokensForOp("analyzeFoodMulti", 1024, true))
    }

    @Test
    fun userCapAtOrAboveFloorWins() {
        assertEquals(8192, floorResponseTokensForOp("analyzeText", 8192, true))
        assertEquals(4096, floorResponseTokensForOp("analyzeFood", 4096, true))
    }

    @Test
    fun nonConstituentOpsAndOptOutKeepUserCap() {
        assertEquals(1024, floorResponseTokensForOp("analyzeLabel", 1024, true))
        assertEquals(1024, floorResponseTokensForOp("inferServing", 1024, true))
        assertEquals(1024, floorResponseTokensForOp("analyzeText", 1024, false))
        assertEquals(256, floorResponseTokensForOp("analyzeFoodMulti", 256, false))
    }
}
