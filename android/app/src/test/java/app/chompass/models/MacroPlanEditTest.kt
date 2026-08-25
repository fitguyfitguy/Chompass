package app.chompass.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Write-path invariants for the macro day-plan editor (Codeberg #60,
 * docs/local/MACRO_PROFILES_DESIGN.md § Write-path invariants — phase 2).
 */
class MacroPlanEditTest {
    private val today = LocalDate.of(2026, 9, 1)
    private val training = MacroDayProfile("t", "Training day", 2800, 170, 350, 78)
    private val rest = MacroDayProfile("r", "Rest day", 2100, 150, 160, 78)
    private val light = MacroDayProfile("l", "Light day", 2300, 150, 220, 76)

    private fun plan(
        profiles: List<MacroDayProfile> = listOf(training, rest),
        enabled: Boolean = true,
        mode: MacroPlanMode = MacroPlanMode.CYCLE,
        default: String? = "t",
        weekdays: Map<String, String> = emptyMap(),
        pattern: List<String> = listOf("t", "t", "r"),
        anchor: String? = "2026-08-18",
        assignments: Map<String, String> = emptyMap(),
    ) = MacroPlan(
        enabled = enabled,
        profiles = profiles,
        mode = mode,
        defaultProfileId = default,
        weekdayProfileIds = weekdays,
        cyclePattern = pattern,
        cycleAnchorDay = anchor,
        dayAssignments = assignments,
    )

    // -- setEnabled --------------------------------------------------------------

    @Test
    fun `enable with two profiles seeds default and pattern`() {
        val enabling = plan(enabled = false, default = null, pattern = emptyList(), anchor = null)
        val on = MacroPlanEdit.setEnabled(enabling, true, today)!!
        assertTrue(on.enabled)
        assertEquals("t", on.defaultProfileId)
        assertTrue(on.cyclePattern.size >= 2)
        assertEquals(today.toString(), on.cycleAnchorDay)
        assertEquals(MacroPlanMode.CYCLE, on.mode)
    }

    @Test
    fun `enable with one profile stays disabled`() {
        val solo = plan(profiles = listOf(training), enabled = false)
        val result = MacroPlanEdit.setEnabled(solo, true, today)!!
        assertFalse(result.enabled)
    }

    @Test
    fun `disable keeps data`() {
        val off = MacroPlanEdit.setEnabled(plan(), false, today)!!
        assertFalse(off.enabled)
        assertEquals(2, off.profiles.size)
        assertEquals(listOf("t", "t", "r"), off.cyclePattern)
    }

    @Test
    fun `enable from null plan returns null`() {
        assertNull(MacroPlanEdit.setEnabled(null, true, today))
    }

    // -- upsertProfile (MACRO-CYCLE-C clamps) -----------------------------------

    @Test
    fun `upsert clamps calories to the safety floor`() {
        val profile = MacroDayProfile("new", "Aggressive cut", 900, 150, 60, 40)
        val result = MacroPlanEdit.upsertProfile(plan(), profile, bmr = 1700.0, tdee = 2500.0, today = today)
        // floor = max(bmr, ABSOLUTE_FLOOR) = 1700
        assertEquals(1700, result.profiles.first { it.id == "new" }.calories)
    }

    @Test
    fun `upsert clamps calories to the ceiling`() {
        val profile = MacroDayProfile("new", "Bulk", 9000, 200, 800, 200)
        val result = MacroPlanEdit.upsertProfile(plan(), profile, bmr = 1700.0, tdee = 2500.0, today = today)
        val saved = result.profiles.first { it.id == "new" }
        assertTrue(saved.calories <= CalorieSafety.ceilingKcal(2500.0, 1700))
    }

    @Test
    fun `upsert replaces an existing profile without growing the list`() {
        val edited = training.copy(name = "Heavy training")
        val result = MacroPlanEdit.upsertProfile(plan(), edited, bmr = 1700.0, tdee = 2500.0, today = today)
        assertEquals(2, result.profiles.size)
        assertEquals("Heavy training", result.profiles.first { it.id == "t" }.name)
    }

    @Test
    fun `upsert refuses an add at the 7-profile cap`() {
        val full = plan(
            profiles = (1..7).map { MacroDayProfile("p$it", "P$it", 2400, 150, 250, 70) },
            mode = MacroPlanMode.MANUAL,
            pattern = emptyList(),
        )
        val extra = MacroDayProfile("p8", "P8", 2400, 150, 250, 70)
        val result = MacroPlanEdit.upsertProfile(full, extra, bmr = 1700.0, tdee = 2500.0, today = today)
        assertEquals(7, result.profiles.size)
    }

    // -- deleteProfile (scrub + re-point) ----------------------------------------

    @Test
    fun `delete with replacement re-points every reference`() {
        val p = plan(
            default = "t",
            weekdays = mapOf("MONDAY" to "t", "TUESDAY" to "r"),
            pattern = listOf("t", "t", "r"),
            assignments = mapOf(today.toString() to "t", today.plusDays(2).toString() to "r"),
        )
        val result = MacroPlanEdit.deleteProfile(p, "t", "r", today)
        assertEquals(listOf(rest), result.profiles)
        assertEquals("r", result.defaultProfileId)
        assertEquals(mapOf("MONDAY" to "r", "TUESDAY" to "r"), result.weekdayProfileIds)
        assertEquals(listOf("r", "r", "r"), result.cyclePattern)
        assertEquals("r", result.dayAssignments[today.toString()])
        // below MIN_PROFILES after delete: disabled, data kept
        assertFalse(result.enabled)
    }

    @Test
    fun `delete without replacement scrubs references`() {
        val p = plan(
            profiles = listOf(training, rest, light),
            default = "t",
            weekdays = mapOf("MONDAY" to "t", "TUESDAY" to "r"),
            pattern = listOf("t", "t", "r"),
            assignments = mapOf(today.toString() to "t"),
        )
        val result = MacroPlanEdit.deleteProfile(p, "t", null, today)
        assertEquals(listOf(rest, light), result.profiles)
        assertNull(result.defaultProfileId)
        assertEquals(mapOf("TUESDAY" to "r"), result.weekdayProfileIds)
        assertEquals(listOf("r"), result.cyclePattern)
        assertTrue(result.dayAssignments.isEmpty())
        // CYCLE pattern dropped below 2: mode falls back to MANUAL, stays enabled (3 profiles before)
        assertEquals(MacroPlanMode.MANUAL, result.mode)
        assertTrue(result.enabled)
    }

    @Test
    fun `delete unknown id is a no-op`() {
        val p = plan()
        assertEquals(p, MacroPlanEdit.deleteProfile(p, "ghost", null, today))
    }

    // -- mode + schedule writes ---------------------------------------------------

    @Test
    fun `setMode to cycle seeds anchor and pattern when empty`() {
        val p = plan(mode = MacroPlanMode.MANUAL, pattern = emptyList(), anchor = null)
        val result = MacroPlanEdit.setMode(p, MacroPlanMode.CYCLE, today)
        assertEquals(MacroPlanMode.CYCLE, result.mode)
        assertEquals(today.toString(), result.cycleAnchorDay)
        assertEquals(listOf("t", "r"), result.cyclePattern)
    }

    @Test
    fun `setMode keeps an existing pattern`() {
        val result = MacroPlanEdit.setMode(plan(), MacroPlanMode.WEEKDAYS, today)
        assertEquals(MacroPlanMode.WEEKDAYS, result.mode)
        assertEquals(listOf("t", "t", "r"), result.cyclePattern)
    }

    @Test
    fun `weekday write and clear`() {
        val withDay = MacroPlanEdit.setWeekday(plan(), java.time.DayOfWeek.FRIDAY, "r")
        assertEquals("r", withDay.weekdayProfileIds["FRIDAY"])
        val cleared = MacroPlanEdit.setWeekday(withDay, java.time.DayOfWeek.FRIDAY, null)
        assertTrue(cleared.weekdayProfileIds.isEmpty())
    }

    @Test
    fun `day assignment write and remove`() {
        val withOverride = MacroPlanEdit.setDayAssignment(plan(), today.plusDays(3), "r")
        assertEquals("r", withOverride.dayAssignments[today.plusDays(3).toString()])
        val removed = MacroPlanEdit.setDayAssignment(withOverride, today.plusDays(3), null)
        assertTrue(removed.dayAssignments.isEmpty())
    }

    @Test
    fun `restartCycle re-anchors today`() {
        val result = MacroPlanEdit.restartCycle(plan(), today)
        assertEquals(today.toString(), result.cycleAnchorDay)
    }

    @Test
    fun `reorder permutes the profile list`() {
        val result = MacroPlanEdit.reorder(plan(), listOf("r", "t"))
        assertEquals(listOf(rest, training), result.profiles)
    }

    // -- keto pause + normalization -----------------------------------------------

    @Test
    fun `keto pause keeps data disabled`() {
        val paused = MacroPlanEdit.pausedForKeto(plan())!!
        assertFalse(paused.enabled)
        assertEquals(2, paused.profiles.size)
        assertEquals(listOf("t", "t", "r"), paused.cyclePattern)
    }

    @Test
    fun `normalized prunes stale assignments and unknown references`() {
        val stale = plan(
            assignments = mapOf(
                "2020-01-01" to "t",                    // outside ±366d: pruned
                today.toString() to "t",                // kept
                today.plusDays(10).toString() to "ghost", // unknown profile: dropped
            ),
            weekdays = mapOf("MONDAY" to "ghost"),
            default = "ghost",
        )
        val result = MacroPlanEdit.normalized(stale, today)
        assertEquals(mapOf(today.toString() to "t"), result.dayAssignments)
        assertTrue(result.weekdayProfileIds.isEmpty())
        assertNull(result.defaultProfileId)
    }

    @Test
    fun `cycle pattern filtered to known ids`() {
        val dirty = plan(pattern = listOf("t", "ghost", "r", "ghost"))
        val result = MacroPlanEdit.normalized(dirty, today)
        assertEquals(listOf("t", "r"), result.cyclePattern)
    }
}
