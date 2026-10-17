package app.chompass.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Live-run slot for food analysis (Codeberg #136). Only a genuinely in-flight
 * run may refuse a new one: a run that ended (or never got superseded by a
 * live run) must release the slot, and a stale generation must never release
 * it — that stale-release refusal is exactly the "queue Run-with wedged"
 * regression this guard prevents.
 */
class AnalysisRunGateTest {
    @Test
    fun tryBegin_firstSucceeds_secondWhileHeldRefused() {
        val gate = AnalysisRunGate()
        val first = gate.tryBegin()
        assertTrue(first != null && first > 0)
        assertNull(gate.tryBegin())
    }

    @Test
    fun end_currentGenerationReleases_nextBeginSucceeds() {
        val gate = AnalysisRunGate()
        val gen = gate.tryBegin()!!
        gate.end(gen)
        assertTrue(gate.tryBegin() != null)
    }

    @Test
    fun end_staleGenerationDoesNotRelease_beginStillRefused() {
        val gate = AnalysisRunGate()
        val stale = gate.tryBegin()!!
        gate.invalidate()
        val current = gate.tryBegin()!!
        assertNotEquals(stale, current)
        // The #136 invariant: ending an old generation must not free a slot
        // held by a newer run.
        gate.end(stale)
        assertNull(gate.tryBegin())
        gate.end(current)
        assertTrue(gate.tryBegin() != null)
    }

    @Test
    fun invalidate_releasesSlot_andOldGenerationsAreNotCurrent() {
        val gate = AnalysisRunGate()
        val first = gate.tryBegin()!!
        gate.invalidate()
        assertFalse(gate.isCurrent(first))
        assertTrue(gate.tryBegin() != null)
    }

    @Test
    fun end_afterInvalidate_isNoOpForNewRun() {
        val gate = AnalysisRunGate()
        val first = gate.tryBegin()!!
        gate.invalidate()
        val second = gate.tryBegin()!!
        gate.end(first)
        assertFalse(gate.canBegin())
        gate.end(second)
        assertTrue(gate.canBegin())
    }

    @Test
    fun isCurrent_onlyNewestGenerationHolds() {
        val gate = AnalysisRunGate()
        val first = gate.tryBegin()!!
        assertTrue(gate.isCurrent(first))
        val second = gate.tryBegin()
        assertNull(second)
        gate.invalidate()
        val third = gate.tryBegin()!!
        assertTrue(gate.isCurrent(third))
        assertFalse(gate.isCurrent(first))
        // canBegin mirrors the live slot for UI affordances.
        assertFalse(gate.canBegin())
        gate.end(third)
        assertTrue(gate.canBegin())
    }
}
