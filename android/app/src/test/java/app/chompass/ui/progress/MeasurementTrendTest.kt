package app.chompass.ui.progress

import app.chompass.models.BodyMeasurement
import app.chompass.models.Gender
import app.chompass.models.UserProfile
import app.chompass.services.SampleDataGenerators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class MeasurementTrendTest {
    private fun trendPoint(daysAgo: Long, value: Double, anchor: LocalDate = LocalDate.of(2026, 8, 13)) =
        TrendPoint(
            timeMs = anchor.minusDays(daysAgo).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            value = value,
        )

    @Test
    fun siteStorageIdsRoundTrip() {
        BodyMeasurement.Site.values().forEach { site ->
            assertEquals(site, BodyMeasurement.Site.fromStorageId(site.storageId))
        }
        assertNull(BodyMeasurement.Site.fromStorageId("bogus"))
        assertNull(BodyMeasurement.Site.fromStorageId(null))
    }

    @Test
    fun chartModelRespectsSeriesBoundsAndTicks() {
        val series = listOf(
            trendPoint(30, 80.0),
            trendPoint(20, 84.0),
            trendPoint(10, 83.0),
            trendPoint(0, 82.5),
        )
        val model = buildMeasurementChartModel(series)
        assertTrue("yMin pads below data", model.yMin < 80.0)
        assertTrue("yMax pads above data", model.yMax > 84.0)
        model.ticks.forEach { tick ->
            assertTrue("tick $tick within range", tick >= model.yMin - 1e-9 && tick <= model.yMax + 1e-9)
        }
        assertEquals(4, model.points.size)
        assertEquals(series.first().timeMs, model.tStart)
        assertEquals(series.last().timeMs, model.tEnd)
        assertEquals(series.last().timeMs - series.first().timeMs, model.tRange)
        assertEquals(null, model.goalDisplayValue)
        assertTrue(model.trendPoints.isEmpty())
        assertTrue(model.trendSegments.isEmpty())
    }

    @Test
    fun chartModelSingleEntryCentersAndSkipsDotsOverflow() {
        val single = buildMeasurementChartModel(listOf(trendPoint(0, 83.0)))
        assertTrue(single.singleEntry)
        assertEquals(1, single.points.size)

        // Dense series still downsamples instead of crashing (ascending time, like real data).
        val dense = buildMeasurementChartModel((499 downTo 0).map { trendPoint(it.toLong(), 80.0 + it % 10) })
        assertTrue(dense.points.size <= 60)
        assertTrue(!dense.singleEntry)
    }

    @Test
    fun chartModelEmptySeriesIsSafe() {
        val model = buildMeasurementChartModel(emptyList())
        assertTrue(model.points.isEmpty())
        assertEquals(1L, model.tRange)
        assertTrue(model.singleEntry)
    }

    @Test
    fun chartModelShowsYearOnlyAcrossYearBoundary() {
        val withinYear = listOf(
            trendPoint(100, 80.0),
            trendPoint(0, 83.0),
        )
        assertEquals(false, buildMeasurementChartModel(withinYear).showsYear)

        // 230 days earlier crosses into the previous calendar year (2025-12-26).
        val crossing = listOf(
            trendPoint(230, 80.0),
            trendPoint(0, 83.0),
        )
        assertEquals(true, buildMeasurementChartModel(crossing).showsYear)
    }

    @Test
    fun previewStateFiltersMeasurementsToRange() {
        val anchor = LocalDate.of(2026, 8, 13)
        val inRange = BodyMeasurement(
            date = anchor.minusDays(100).atStartOfDay(ZoneOffset.UTC).toInstant(),
            waistCm = 86.0,
        )
        val boundary = BodyMeasurement(
            date = anchor.minusDays(179).atTime(23, 59, 59).atZone(ZoneOffset.UTC).toInstant(),
            waistCm = 88.0,
        )
        val tooOld = BodyMeasurement(
            date = anchor.minusDays(200).atStartOfDay(ZoneOffset.UTC).toInstant(),
            waistCm = 90.0,
        )
        val tooNew = BodyMeasurement(
            date = anchor.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
            waistCm = 82.0,
        )
        val ui = buildProgressPreviewUiState(
            profile = UserProfile(
                gender = Gender.MALE,
                heightCm = 178.0,
                weightKg = 75.0,
                goalWeightKg = 70.0,
            ),
            weights = emptyList(),
            bodyFatEntries = emptyList(),
            foods = emptyList(),
            timeRange = TimeRange.SIX_MONTHS,
            anchorDate = anchor,
            bodyMeasurements = listOf(inRange, boundary, tooOld, tooNew),
            measurementSites = setOf(BodyMeasurement.Site.WAIST, BodyMeasurement.Site.NECK),
        )
        assertEquals(
            listOf(boundary.date, inRange.date),
            ui.filteredMeasurements.map { it.date },
        )
        assertEquals(
            setOf(BodyMeasurement.Site.WAIST, BodyMeasurement.Site.NECK),
            ui.measurementSites,
        )
    }

    @Test
    fun previewStateRoundTripsSiteSetThroughStorageIds() {
        val anchor = LocalDate.of(2026, 8, 13)
        val ui = buildProgressPreviewUiState(
            profile = null,
            weights = emptyList(),
            bodyFatEntries = emptyList(),
            foods = emptyList(),
            timeRange = TimeRange.WEEK,
            anchorDate = anchor,
            bodyMeasurements = emptyList(),
            measurementSites = setOf(BodyMeasurement.Site.WAIST, BodyMeasurement.Site.CALF),
        )
        assertEquals(
            setOf(BodyMeasurement.Site.WAIST, BodyMeasurement.Site.CALF),
            ui.measurementSites,
        )
        assertTrue(ui.filteredMeasurements.isEmpty())
    }

    @Test
    fun previewState_holdsCountsNotUnfilteredLists() {
        val anchor = LocalDate.of(2026, 8, 13)
        val weights = SampleDataGenerators.weightSeries(
            totalDays = 400,
            startKg = 80.0,
            endKg = 73.0,
            seed = 1,
            today = anchor,
        )
        val ui = buildProgressPreviewUiState(
            profile = null,
            weights = weights,
            bodyFatEntries = emptyList(),
            foods = emptyList(),
            timeRange = TimeRange.WEEK,
            anchorDate = anchor,
        )
        assertEquals(weights.size, ui.weightCount)
        assertTrue(ui.filteredWeights.size < ui.weightCount)
        assertEquals(weights.maxByOrNull { it.date }?.weightKg, ui.latestWeightKg)
    }
}
