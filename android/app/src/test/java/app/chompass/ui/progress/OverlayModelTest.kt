package app.chompass.ui.progress

import androidx.compose.ui.graphics.Color
import app.chompass.models.BodyFatEntry
import app.chompass.models.BodyMeasurement
import app.chompass.models.UnitFormat
import app.chompass.models.WeightEntry
import app.chompass.ui.theme.AppColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.TimeZone

class OverlayModelTest {
    private val zone = ZoneOffset.UTC

    @Before
    fun utcZone() {
        // The model buckets days with ZoneId.systemDefault(); pin it to UTC so
        // the epoch-ms assertions match the UTC-constructed fixtures.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    private fun at(day: LocalDate) = day.atStartOfDay(zone).toInstant()

    private fun daysFrom(from: LocalDate, count: Int) = (0 until count).map { from.plusDays(it.toLong()) }

    private fun meta(vararg keys: String) = keys.associateWith { key ->
        OverlaySeriesMeta(label = key, unitLabel = "", color = Color(0xFF112233))
    }

    @Test
    fun scaleMapsMinToBottomAndMaxToTop() {
        val scale = OverlayScale.of(listOf(60.0, 70.0, 80.0), goal = null)
        assertEquals(0f, scale.fraction(60.0))
        assertEquals(1f, scale.fraction(80.0))
        assertEquals(0.5f, scale.fraction(70.0))
    }

    @Test
    fun scaleClampsGoalInWithFivePercentPadding() {
        // Goal above the data: extended top edge keeps the rule 5% inside.
        val above = OverlayScale.of(listOf(60.0, 80.0), goal = 90.0)
        assertEquals(0f, above.fraction(60.0))
        assertTrue("goal inset from top", above.fraction(90.0) < 1f)
        assertTrue(above.fraction(80.0) < above.fraction(90.0))
        // Goal below the data: mirrored.
        val below = OverlayScale.of(listOf(60.0, 80.0), goal = 50.0)
        assertEquals(1f, below.fraction(80.0))
        assertTrue("goal inset from bottom", below.fraction(50.0) > 0f)
        // Goal inside the range: no padding, data still maps min→0 max→1.
        val inside = OverlayScale.of(listOf(60.0, 80.0), goal = 70.0)
        assertEquals(0f, inside.fraction(60.0))
        assertEquals(1f, inside.fraction(80.0))
    }

    @Test
    fun constantSeriesRendersFlatMidLine() {
        val scale = OverlayScale.of(listOf(75.0, 75.0, 75.0), goal = null)
        assertEquals(0.5f, scale.fraction(75.0))
        assertEquals(0.5f, scale.fraction(0.0))
        val withGoal = OverlayScale.of(listOf(75.0, 75.0), goal = 75.0)
        assertEquals(0.5f, withGoal.fraction(75.0))
    }

    @Test
    fun emptyValuesFallBackToUnitScale() {
        val scale = OverlayScale.of(emptyList(), goal = null)
        assertEquals(0.0, scale.min, 1e-12)
        assertEquals(1.0, scale.max, 1e-12)
    }

    @Test
    fun emptyInputsYieldEmptyModel() {
        val model = buildOverlayChartModel(
            weights = emptyList(),
            bodyFats = emptyList(),
            measurements = emptyList(),
            sites = listOf(BodyMeasurement.Site.WAIST),
            goalWeightKg = null,
            goalBodyFatFraction = null,
            useMetricWeight = true,
            useMetricLength = true,
            meta = meta(OVERLAY_KEY_WEIGHT, OVERLAY_KEY_BODY_FAT, "waist"),
            dayTypeByDay = emptyMap(),
            untrackedDays = emptySet(),
            typeColorOf = { Color.Transparent },
        )
        assertTrue(model.series.isEmpty())
        assertTrue(model.markers.isEmpty())
        assertTrue(model.dayValues.isEmpty())
    }

    @Test
    fun weightSeriesConvertsToLbsAndMapsDayValues() {
        val weights = listOf(
            WeightEntry(weightKg = 80.0, date = at(LocalDate.of(2026, 8, 1))),
            WeightEntry(weightKg = 82.0, date = at(LocalDate.of(2026, 8, 2))),
            WeightEntry(weightKg = 81.0, date = at(LocalDate.of(2026, 8, 3))),
        )
        val model = buildOverlayChartModel(
            weights = weights,
            bodyFats = emptyList(),
            measurements = emptyList(),
            sites = emptyList(),
            goalWeightKg = 75.0,
            goalBodyFatFraction = null,
            useMetricWeight = false, // lbs
            useMetricLength = true,
            meta = meta(OVERLAY_KEY_WEIGHT),
            dayTypeByDay = emptyMap(),
            untrackedDays = emptySet(),
            typeColorOf = { Color.Transparent },
        )
        assertEquals(listOf(OVERLAY_KEY_WEIGHT), model.series.map { it.key })
        val series = model.series.single()
        // kg→lb conversion on raw points and the goal rule.
        assertEquals(UnitFormat.kgToLbs(81.0), series.currentValue!!, 1e-9)
        assertEquals(UnitFormat.kgToLbs(75.0), series.goalDisplayValue!!, 1e-9)
        // Per-day lookup keyed by ISO day.
        assertEquals(
            UnitFormat.kgToLbs(80.0),
            model.dayValues["2026-08-01"]!![OVERLAY_KEY_WEIGHT]!!,
            1e-9,
        )
        // Delta over range from first to last raw daily point.
        assertEquals(UnitFormat.kgToLbs(1.0), series.deltaOverRange!!, 1e-9)
    }

    @Test
    fun bodyFatSeriesUsesPercentsAndSharedAxisSpansAllSeries() {
        val weights = listOf(
            WeightEntry(weightKg = 80.0, date = at(LocalDate.of(2026, 8, 5))),
        )
        val bodyFats = listOf(
            BodyFatEntry(bodyFatFraction = 0.20, date = at(LocalDate.of(2026, 8, 1))),
            BodyFatEntry(bodyFatFraction = 0.18, date = at(LocalDate.of(2026, 8, 3))),
        )
        val model = buildOverlayChartModel(
            weights = weights,
            bodyFats = bodyFats,
            measurements = emptyList(),
            sites = emptyList(),
            goalWeightKg = null,
            goalBodyFatFraction = 0.15,
            useMetricWeight = true,
            useMetricLength = true,
            meta = meta(OVERLAY_KEY_WEIGHT, OVERLAY_KEY_BODY_FAT),
            dayTypeByDay = emptyMap(),
            untrackedDays = emptySet(),
            typeColorOf = { Color.Transparent },
        )
        val bf = model.series.first { it.key == OVERLAY_KEY_BODY_FAT }
        assertEquals(18.0, bf.currentValue!!, 1e-9)
        assertEquals(15.0, bf.goalDisplayValue!!, 1e-9)
        assertNull(model.series.first { it.key == OVERLAY_KEY_WEIGHT }.deltaOverRange)
        // Axis spans the union: first BF day through the weight day (+1 slot).
        assertEquals(at(LocalDate.of(2026, 8, 1)).toEpochMilli(), model.xFromMs)
        assertTrue(model.xToMs > at(LocalDate.of(2026, 8, 5)).toEpochMilli())
        assertNotNull(model.markers.firstOrNull())
    }

    @Test
    fun measurementSeriesConvertCmToInchesAndSitesAreDroppedWhenEmpty() {
        val measurements = listOf(
            BodyMeasurement(date = at(LocalDate.of(2026, 8, 1)), waistCm = 80.0),
            BodyMeasurement(date = at(LocalDate.of(2026, 8, 2)), waistCm = 79.0),
        )
        val model = buildOverlayChartModel(
            weights = emptyList(),
            bodyFats = emptyList(),
            measurements = measurements,
            sites = listOf(BodyMeasurement.Site.WAIST, BodyMeasurement.Site.CHEST),
            goalWeightKg = null,
            goalBodyFatFraction = null,
            useMetricWeight = true,
            useMetricLength = false, // inches
            meta = meta("waist", "chest"),
            dayTypeByDay = emptyMap(),
            untrackedDays = emptySet(),
            typeColorOf = { Color.Transparent },
        )
        // Waist has data; chest doesn't → dropped.
        assertEquals(listOf("waist"), model.series.map { it.key })
        val waist = model.series.single()
        assertEquals(UnitFormat.cmToInches(79.0), waist.currentValue!!, 1e-9)
        // Site colors follow the fixed palette offset (index 1 + ordinal % 4).
        assertEquals(
            AppColors.ChartSeries[1 + BodyMeasurement.Site.WAIST.ordinal % (AppColors.ChartSeries.size - 1)],
            overlaySeriesColor("waist"),
        )
        assertEquals(AppColors.ChartSeries[0], overlaySeriesColor(OVERLAY_KEY_BODY_FAT))
        assertEquals(AppColors.Calorie, overlaySeriesColor(OVERLAY_KEY_WEIGHT))
    }

    @Test
    fun showsDotsThresholdAt31PointsAndDownsamplingBeyond60() {
        val start = LocalDate.of(2026, 1, 1)
        fun weights(count: Int) = daysFrom(start, count).map {
            WeightEntry(weightKg = 80.0 + it.dayOfYear % 5, date = at(it))
        }
        val metaMap = meta(OVERLAY_KEY_WEIGHT)
        val at31 = buildOverlayChartModel(
            weights = weights(31), bodyFats = emptyList(), measurements = emptyList(),
            sites = emptyList(), goalWeightKg = null, goalBodyFatFraction = null,
            useMetricWeight = true, useMetricLength = true, meta = metaMap,
            dayTypeByDay = emptyMap(), untrackedDays = emptySet(),
            typeColorOf = { Color.Transparent },
        ).series.single()
        assertTrue(at31.showsDots)
        val at32 = buildOverlayChartModel(
            weights = weights(32), bodyFats = emptyList(), measurements = emptyList(),
            sites = emptyList(), goalWeightKg = null, goalBodyFatFraction = null,
            useMetricWeight = true, useMetricLength = true, meta = metaMap,
            dayTypeByDay = emptyMap(), untrackedDays = emptySet(),
            typeColorOf = { Color.Transparent },
        ).series.single()
        assertFalse(at32.showsDots)
        val many = buildOverlayChartModel(
            weights = weights(200), bodyFats = emptyList(), measurements = emptyList(),
            sites = emptyList(), goalWeightKg = null, goalBodyFatFraction = null,
            useMetricWeight = true, useMetricLength = true, meta = metaMap,
            dayTypeByDay = emptyMap(), untrackedDays = emptySet(),
            typeColorOf = { Color.Transparent },
        ).series.single()
        assertTrue("raw points downsampled", many.points.size <= 60)
        assertTrue("trend points downsampled", many.trendPoints.size <= 60)
        // Daily bucketing collapses same-day entries.
        val doubled = buildOverlayChartModel(
            weights = weights(30) + weights(30), bodyFats = emptyList(), measurements = emptyList(),
            sites = emptyList(), goalWeightKg = null, goalBodyFatFraction = null,
            useMetricWeight = true, useMetricLength = true, meta = metaMap,
            dayTypeByDay = emptyMap(), untrackedDays = emptySet(),
            typeColorOf = { Color.Transparent },
        ).series.single()
        assertEquals(30, doubled.points.size)
    }
}
