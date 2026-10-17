package app.chompass.ui.progress

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/**
 * Chart-math behind the progress visuals: untracked backdrop bands and the
 * calendar-day calorie axis (daily at every range). Pure functions — the
 * drawing lives in DrawScope code verified by screenshots.
 */
class CalorieSlotModelTest {
    private val zone = ZoneId.systemDefault()
    private fun epoch(day: LocalDate) = day.atStartOfDay(zone).toInstant().toEpochMilli()

    // -- buildUntrackedSpans ------------------------------------------------

    @Test
    fun spans_emptyWithoutUntrackedDays() {
        val spans = buildUntrackedSpans(
            start = LocalDate.of(2026, 10, 1),
            end = LocalDate.of(2026, 10, 7),
            untracked = emptySet(),
        )
        assertTrue(spans.isEmpty())
    }

    @Test
    fun spans_singleDayAndConsecutiveRunsMerge() {
        val start = LocalDate.of(2026, 10, 1)
        val end = LocalDate.of(2026, 10, 10)
        val untracked = setOf("2026-10-03", "2026-10-04", "2026-10-05", "2026-10-08")
        val bands = buildUntrackedSpans(start, end, untracked)
        assertEquals(
            listOf(
                UntrackedBand(
                    epoch(LocalDate.of(2026, 10, 3))..epoch(LocalDate.of(2026, 10, 5)),
                    partial = false,
                ),
                UntrackedBand(
                    epoch(LocalDate.of(2026, 10, 8))..epoch(LocalDate.of(2026, 10, 8)),
                    partial = false,
                ),
            ),
            bands,
        )
    }

    @Test
    fun spans_clampToRequestedRange() {
        val start = LocalDate.of(2026, 10, 5)
        val end = LocalDate.of(2026, 10, 9)
        // 10-04 precedes the window; 10-09 is the boundary day itself.
        val untracked = setOf("2026-10-04", "2026-10-09", "2026-10-10")
        val bands = buildUntrackedSpans(start, end, untracked)
        assertEquals(
            listOf(
                UntrackedBand(
                    epoch(LocalDate.of(2026, 10, 9))..epoch(LocalDate.of(2026, 10, 9)),
                    partial = false,
                ),
            ),
            bands,
        )
    }

    @Test
    fun spans_weeklyMajorityBucketsAboveMaxSlots() {
        // 100 days from a Monday: 2026-01-05 .. 2026-04-14, forces ISO-week buckets.
        val start = LocalDate.of(2026, 1, 5)
        assertEquals(DayOfWeek.MONDAY, start.dayOfWeek)
        val end = start.plusDays(99)
        // Week 1 fully untracked (7/7), week 2 majority (4/7) -> both merge into
        // one full band covering week 2's full 7 days; week 3 minority (3/7)
        // -> partial band over week 3.
        val untracked = (0..6).map { start.plusDays(it.toLong()).toString() }.toSet() +
            (7..10).map { start.plusDays(it.toLong()).toString() }.toSet() +
            (14..16).map { start.plusDays(it.toLong()).toString() }.toSet()
        val bands = buildUntrackedSpans(start, end, untracked)
        assertEquals(
            listOf(
                UntrackedBand(epoch(start)..epoch(start.plusDays(13)), partial = false),
                UntrackedBand(epoch(start.plusDays(14))..epoch(start.plusDays(20)), partial = true),
            ),
            bands,
        )
    }

    @Test
    fun spans_weeklyMajorityClampsTailToRangeEnd() {
        val start = LocalDate.of(2026, 1, 5)
        // 95 days still forces weekly buckets; range ends mid-week.
        val end = start.plusDays(94)
        // Days 90..94 untracked: day 90 alone in its week (1/7 -> partial band
        // over that whole week), the partial final week holds 91..94 (4/4,
        // majority) -> full band from that Monday, clamped to the range end.
        val untracked = (90..94).map { start.plusDays(it.toLong()).toString() }.toSet()
        val bands = buildUntrackedSpans(start, end, untracked)
        assertEquals(
            listOf(
                UntrackedBand(
                    epoch(start.plusDays(84))..epoch(start.plusDays(90)),
                    partial = true,
                ),
                UntrackedBand(epoch(start.plusDays(91))..epoch(end), partial = false),
            ),
            bands,
        )
    }

    // -- buildMarkerLane ----------------------------------------------------

    @Test
    fun lane_dailyUntrackedDaysGetFullDashes() {
        val markers = buildMarkerLane(
            start = LocalDate.of(2026, 10, 5),
            end = LocalDate.of(2026, 10, 7),
            types = emptyMap(),
            untracked = setOf("2026-10-06"),
            typeColorOf = { Color.Transparent },
        )
        assertEquals(
            listOf(
                DayMarker(LocalDate.of(2026, 10, 5), null, untracked = false),
                DayMarker(LocalDate.of(2026, 10, 6), null, untracked = true),
                DayMarker(LocalDate.of(2026, 10, 7), null, untracked = false),
            ),
            markers,
        )
    }

    @Test
    fun lane_weeklyMajorityFullMinorityPartial() {
        val start = LocalDate.of(2026, 1, 5)
        val end = start.plusDays(99)
        // Week 1 fully untracked -> full dash; week 2 holds 2/7 -> partial;
        // week 3 untouched -> neither.
        val untracked = (0..6).map { start.plusDays(it.toLong()).toString() }.toSet() +
            (10..11).map { start.plusDays(it.toLong()).toString() }.toSet()
        val markers = buildMarkerLane(
            start = start,
            end = end,
            types = emptyMap(),
            untracked = untracked,
            typeColorOf = { Color.Transparent },
        )
        assertEquals(
            DayMarker(start, null, untracked = true, partialUntracked = false),
            markers[0],
        )
        assertEquals(
            DayMarker(start.plusDays(7), null, untracked = false, partialUntracked = true),
            markers[1],
        )
        assertEquals(
            DayMarker(start.plusDays(14), null, untracked = false, partialUntracked = false),
            markers[2],
        )
    }

    // -- calorieLabelIndices ------------------------------------------------

    @Test
    fun labels_endSlotReplacesCrowdedPick() {
        // 30 slots at step 4: uniform picks end at 28; forcing 29 by appending
        // would crowd it (1-slot gap in a 4-slot box pitch) -> replace instead.
        assertEquals(listOf(0, 4, 8, 12, 16, 20, 24, 29), calorieLabelIndices(30, 4))
    }

    @Test
    fun labels_endSlotKeptWhenAlreadyPicked() {
        assertEquals(listOf(0, 3, 6), calorieLabelIndices(7, 3))
        assertEquals(listOf(0), calorieLabelIndices(1, 4))
        assertEquals(emptyList<Int>(), calorieLabelIndices(0, 4))
    }

    // -- buildCalorieSlots --------------------------------------------------

    @Test
    fun slots_missingUntrackedAndZeroStayDistinct() {
        val start = LocalDate.of(2026, 10, 5)
        val end = LocalDate.of(2026, 10, 9)
        val logged = mapOf(
            LocalDate.of(2026, 10, 5) to 1800,
            LocalDate.of(2026, 10, 6) to 0,
        )
        val untracked = setOf("2026-10-08")
        val slots = buildCalorieSlots(start, end, logged, untracked)
        assertEquals(5, slots.size)
        assertEquals(
            listOf(
                CalorieSlot(LocalDate.of(2026, 10, 5), 1800, untracked = false),
                CalorieSlot(LocalDate.of(2026, 10, 6), 0, untracked = false),
                CalorieSlot(LocalDate.of(2026, 10, 7), null, untracked = false),
                CalorieSlot(LocalDate.of(2026, 10, 8), null, untracked = true),
                CalorieSlot(LocalDate.of(2026, 10, 9), null, untracked = false),
            ),
            slots,
        )
    }

    @Test
    fun slots_untrackedWinsOverLogged() {
        val day = LocalDate.of(2026, 10, 5)
        val slots = buildCalorieSlots(day, day, mapOf(day to 2200), setOf(day.toString()))
        assertEquals(listOf(CalorieSlot(day, null, untracked = true)), slots)
    }

    @Test
    fun slots_emptyForInvertedRange() {
        val day = LocalDate.of(2026, 10, 5)
        assertTrue(buildCalorieSlots(day.plusDays(1), day, emptyMap(), emptySet()).isEmpty())
    }

    @Test
    fun geometry_denseDailyModeFitsEverySlot() {
        val density = Density(1f)
        // More days than 4px gaps fit (6M/1Y/All): gap compresses, the last
        // bar still ends inside the area instead of overflowing off-axis.
        val dense = calorieBarGeometry(areaWidthPx = 1000f, n = 365, density = density)
        assertEquals(2f, dense.barWidth)
        assertTrue(dense.gap < 4f)
        assertEquals(1000f, dense.startX + 364 * (dense.barWidth + dense.gap) + dense.barWidth, 1f)
        // Sparse mode unchanged: 4px gaps, capped width, centered group.
        val sparse = calorieBarGeometry(areaWidthPx = 1000f, n = 5, density = density)
        assertEquals(4f, sparse.gap)
        assertEquals(60f, sparse.barWidth)
        assertEquals(342f, sparse.startX, 0.01f)
    }
}
