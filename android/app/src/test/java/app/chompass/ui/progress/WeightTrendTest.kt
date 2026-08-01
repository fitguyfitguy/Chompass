package app.chompass.ui.progress

import app.chompass.parity.ParityFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class WeightTrendTest {
    @Test
    fun resolvePrefersLastViewedOverDefault() {
        assertEquals(TimeRange.MONTH, TimeRange.resolve("1M", "1Y"))
        assertEquals(TimeRange.YEAR, TimeRange.resolve(null, "1Y"))
        assertEquals(TimeRange.WEEK, TimeRange.resolve(null, null))
        assertEquals(TimeRange.WEEK, TimeRange.resolve("nope", "also-nope"))
        assertEquals(TimeRange.ALL_TIME, TimeRange.resolve(null, "All"))
    }

    @Test
    fun parityFixtureMatchesComputeWeightTrend() {
        val rootObj = ParityFixtures.readJson("weight-trend-expected.json")
        val windowDays = rootObj.getInt("windowDays")
        val minDays = rootObj.getInt("minDaysInWindow")
        val cases = rootObj.getJSONArray("cases")
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val inputsJson = c.getJSONArray("inputs")
            val inputs = (0 until inputsJson.length()).map { j ->
                val row = inputsJson.getJSONObject(j)
                WeightTrendInput(
                    at = Instant.parse(row.getString("date")),
                    weightKg = row.getDouble("weightKg"),
                )
            }
            val expectedJson = c.getJSONArray("expected")
            val expected = (0 until expectedJson.length()).map { j ->
                val row = expectedJson.getJSONObject(j)
                WeightTrendPoint(
                    day = LocalDate.parse(row.getString("date")),
                    valueKg = row.getDouble("valueKg"),
                )
            }
            val actual = computeWeightTrend(
                weighIns = inputs,
                zone = ZoneOffset.UTC,
                windowDays = windowDays,
                minDaysInWindow = minDays,
            )
            assertEquals("case ${c.getString("id")} size", expected.size, actual.size)
            for (k in expected.indices) {
                assertEquals("case ${c.getString("id")} day[$k]", expected[k].day, actual[k].day)
                assertEquals(
                    "case ${c.getString("id")} value[$k]",
                    expected[k].valueKg,
                    actual[k].valueKg,
                    1e-9,
                )
            }
        }
    }

    @Test
    fun splitTrendSegmentsBreaksLargeGaps() {
        val points = listOf(
            WeightTrendPoint(LocalDate.of(2026, 3, 2), 80.0),
            WeightTrendPoint(LocalDate.of(2026, 3, 3), 79.5),
            WeightTrendPoint(LocalDate.of(2026, 3, 20), 78.0),
            WeightTrendPoint(LocalDate.of(2026, 3, 21), 77.5),
        )
        val segments = splitTrendSegments(points, maxGapDays = 7)
        assertEquals(2, segments.size)
        assertEquals(2, segments[0].size)
        assertEquals(2, segments[1].size)
        assertTrue(segments[0].first().day == LocalDate.of(2026, 3, 2))
    }
}
