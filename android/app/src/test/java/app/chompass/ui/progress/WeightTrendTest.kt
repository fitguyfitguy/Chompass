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

    /** Progress v2: the weight-typed API is a thin delegate of the generic
     *  metric math — outputs must match exactly on the parity fixtures. */
    @Test
    fun weightApiEquivalenceWithGenericMetricMath() {
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
            val expected = computeWeightTrend(inputs, ZoneOffset.UTC, windowDays, minDays)
            val generic = computeMetricTrend(
                inputs.map { MetricTrendInput(at = it.at, value = it.weightKg) },
                ZoneOffset.UTC,
                windowDays = windowDays,
                minDaysInWindow = minDays,
            )
            assertEquals("case ${c.getString("id")} generic size", expected.size, generic.size)
            expected.forEachIndexed { k, point ->
                assertEquals("case ${c.getString("id")} day[$k]", point.day, generic[k].day)
                assertEquals("case ${c.getString("id")} value[$k]", point.valueKg, generic[k].value, 1e-12)
            }
            // average*ByDay equivalence on the same inputs.
            val avgWeight = averageWeightByDay(inputs, ZoneOffset.UTC)
            val avgMetric = averageMetricByDay(
                inputs.map { MetricTrendInput(at = it.at, value = it.weightKg) },
                ZoneOffset.UTC,
            )
            assertEquals(avgWeight.map { it.day }, avgMetric.map { it.day })
            avgWeight.zip(avgMetric) { w, m ->
                assertEquals(w.valueKg, m.value, 1e-12)
            }
        }
    }

    @Test
    fun metricSegmentsMatchWeightSegmentsAndFilterInvalidValues() {
        val zone = ZoneOffset.UTC
        fun at(day: LocalDate) = day.atStartOfDay(zone).toInstant()
        val days = listOf(
            LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 3),
            LocalDate.of(2026, 3, 20), LocalDate.of(2026, 3, 21),
        )
        val weightPoints = days.map { WeightTrendPoint(it, 80.0) }
        val metricPoints = days.map { MetricTrendPoint(it, 80.0) }
        assertEquals(
            splitTrendSegments(weightPoints, maxGapDays = 7).map { seg -> seg.map { it.day } },
            splitMetricSegments(metricPoints, maxGapDays = 7).map { seg -> seg.map { it.day } },
        )
        // Non-positive values ignored, same-calendar-day samples averaged.
        val avg = averageMetricByDay(
            listOf(
                MetricTrendInput(at(days[0]), 0.0),
                MetricTrendInput(at(days[0]), 82.0),
                MetricTrendInput(at(days[0]), 84.0),
                MetricTrendInput(at(days[1]), -3.0),
            ),
            zone,
        )
        assertEquals(listOf(days[0]), avg.map { it.day })
        assertEquals(83.0, avg.single().value, 1e-12)
    }
}
