package app.chompass.ui.progress

import androidx.compose.ui.graphics.Color
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Marker-lane bucketing under the Progress trend charts (UI-UX 2026-09-23 §10):
 * per-day dots up to [buildMarkerLane]'s slot budget, ISO-week majority
 * buckets beyond it, and a stable auto color for unpinned profiles.
 */
class MarkerLaneTest {
    private val green = Color(0xFF66BB6A)
    private val blue = Color(0xFF42A5F5)
    private val colors = mapOf("p-training" to green, "p-rest" to blue)
    private val colorOf: (String) -> Color = { colors.getValue(it) }

    @Test
    fun `spans up to 90 days stay day-granular`() {
        val start = LocalDate.of(2026, 7, 1)
        val end = start.plusDays(89) // 90 days total
        val lane = buildMarkerLane(start, end, emptyMap(), emptySet(), colorOf)

        assertEquals(90, lane.size)
        assertEquals(start, lane.first().date)
        assertEquals(end, lane.last().date)
    }

    @Test
    fun `longer spans collapse to iso-week buckets with majority type`() {
        val start = LocalDate.of(2026, 1, 1) // Thursday
        val end = start.plusDays(119) // 120 days → week buckets
        // Make week 1 Monday..Sunday: only the Wednesday is training.
        val monday = start.plusDays(4)
        val week = (0L..6L).associate { monday.plusDays(it).toString() to "p-rest" }
        val types = week + (monday.plusDays(2).toString() to "p-training")

        val lane = buildMarkerLane(start, end, types, emptySet(), colorOf)

        assertTrue(lane.size < 120)
        assertTrue(lane.size >= 17) // at least 17 ISO weeks
        // Bucket keys are Mondays.
        lane.forEach { assertTrue(it.date.dayOfWeek.value == 1 || it.date == lane.first().date) }
        // Majority rest (6 of 7) → blue wins over the single training day.
        assertEquals(blue, lane.first { it.date == monday }.typeColor)
    }

    @Test
    fun `majority-untracked week renders as a dash marker`() {
        val start = LocalDate.of(2026, 1, 1)
        val end = start.plusDays(119)
        val monday = start.plusDays(4)
        val untracked = (0L..6L).map { monday.plusDays(it).toString() }.toSet() +
            monday.plusDays(14).toString() // full week + one stray day elsewhere

        val lane = buildMarkerLane(start, end, emptyMap(), untracked, colorOf)

        val firstWeek = lane.first { it.date == monday }
        assertTrue(firstWeek.untracked)
        // Days outside any fully-untracked week stay tracked.
        val otherWeeks = lane.filter { it.date != monday && it.date != monday.plusWeeks(1) }
        assertTrue(otherWeeks.none { it.untracked })
    }

    @Test
    fun `auto color lookup is stable for a fixed profile id`() {
        // Mirrors DayTypePalette's pinned/auto split: same id, same color,
        // no dependence on call order or session.
        val resolved = app.chompass.ui.theme.dayTypeColor(null, "seed-training")
        assertEquals(resolved, app.chompass.ui.theme.dayTypeColor(null, "seed-training"))
        assertEquals(
            app.chompass.ui.theme.dayTypeColor("green", "anything"),
            app.chompass.ui.theme.dayTypeColor("green", "anything"),
        )
        // Pinned keys ignore the id entirely.
        assertEquals(
            app.chompass.ui.theme.dayTypeColor("green", "seed-training"),
            app.chompass.ui.theme.dayTypeColor("green", "seed-rest"),
        )
    }

    @Test
    fun `single-day span yields one marker`() {
        val day = LocalDate.of(2026, 9, 23)
        val lane = buildMarkerLane(day, day, emptyMap(), emptySet(), colorOf)
        assertEquals(1, lane.size)
        assertFalse(lane[0].untracked)
    }
}
