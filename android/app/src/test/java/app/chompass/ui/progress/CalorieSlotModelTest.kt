package app.chompass.ui.progress

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/**
 * Chart-math behind the progress visuals: untracked backdrop bands, the
 * calendar-day calorie axis, and its weekly roll-up. Pure functions — the
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

    // -- bucketCalorieSlots -------------------------------------------------

    @Test
    fun buckets_passthroughWithinMax() {
        val day = LocalDate.of(2026, 10, 5)
        val slots = listOf(CalorieSlot(day, 1500, untracked = false))
        assertEquals(slots, bucketCalorieSlots(slots))
        assertTrue(bucketCalorieSlots(emptyList()).isEmpty())
    }

    @Test
    fun buckets_rollUpToIsoWeeksWithSums() {
        // 100 days forces weekly buckets; logs land inside the first two weeks.
        val start = LocalDate.of(2026, 1, 5)
        val end = start.plusDays(99)
        val logged = mapOf(
            LocalDate.of(2026, 1, 5) to 2000,
            LocalDate.of(2026, 1, 7) to 1500,
            LocalDate.of(2026, 1, 13) to 900,
        )
        val slots = buildCalorieSlots(start, end, logged, emptySet())
        val buckets = bucketCalorieSlots(slots)
        assertEquals(15, buckets.size)
        assertEquals(CalorieSlot(LocalDate.of(2026, 1, 5), 3500, untracked = false), buckets[0])
        assertEquals(CalorieSlot(LocalDate.of(2026, 1, 12), 900, untracked = false), buckets[1])
        // No logs that week -> null kcal, not 0.
        assertNull(buckets[2].kcal)
        buckets.forEach { assertEquals(DayOfWeek.MONDAY, it.day.dayOfWeek) }
    }

    @Test
    fun buckets_allUntrackedWeekBecomesDashSlot() {
        val start = LocalDate.of(2026, 1, 5)
        val end = start.plusDays(99)
        // First week fully untracked -> one dash slot with null kcal.
        val untracked = (0..6).map { start.plusDays(it.toLong()).toString() }.toSet()
        val buckets = bucketCalorieSlots(buildCalorieSlots(start, end, emptyMap(), untracked))
        assertEquals(
            CalorieSlot(LocalDate.of(2026, 1, 5), null, untracked = true),
            buckets.first(),
        )
    }

    @Test
    fun buckets_mixedWeekMajorityUntrackedNullKcal() {
        val start = LocalDate.of(2026, 1, 5)
        val end = start.plusDays(99)
        // 4 of 7 days untracked, nothing logged -> dash slot, null kcal.
        val untracked = (0..3).map { start.plusDays(it.toLong()).toString() }.toSet()
        val buckets = bucketCalorieSlots(buildCalorieSlots(start, end, emptyMap(), untracked))
        assertEquals(
            CalorieSlot(LocalDate.of(2026, 1, 5), null, untracked = true),
            buckets.first(),
        )
    }
}
