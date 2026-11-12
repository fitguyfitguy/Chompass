package app.chompass.models

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveDayClassifierTest {
    private val today = LocalDate.of(2026, 6, 30)
    private val lightDates = (1..8).map { today.minusDays(it.toLong()) }
    private val hardDates = (9..16).map { today.minusDays(it.toLong()) }
    private val daily = (lightDates.map { it.toString() to 300 } + hardDates.map { it.toString() to 900 }).toMap()

    private fun row(atHour8: Int): List<Int> = List(24) { hour -> if (hour == 8) atHour8 else 0 }

    private fun hourly(light: Int = 40, hard: Int = 200, hardDays: List<LocalDate> = hardDates): Map<String, List<Int>> =
        lightDates.associate { it.toString() to row(light) } +
            hardDays.associate { it.toString() to row(hard) }

    private fun classify(
        minuteOfDay: Int = 540,
        isToday: Boolean = true,
        liveActive: Int = 200,
        dayTypeAverage: Int? = null,
        hourlyByDay: Map<String, List<Int>> = hourly(),
        dayTypeDates: Set<String> = emptySet(),
    ) = ActiveDayClassifier.classify(
        today = today,
        minuteOfDay = minuteOfDay,
        isToday = isToday,
        blendedActive = 600,
        dayTypeAverage = dayTypeAverage,
        dayTypeName = if (dayTypeAverage != null) "Training" else null,
        mergedActiveByDay = daily,
        hourlyByDay = hourlyByDay,
        liveActive = liveActive,
        dayTypeDates = dayTypeDates,
    )

    @Test
    fun minuteZero_staysOnBlend() {
        val result = classify(minuteOfDay = 0)
        assertEquals(0, result.step)
        assertEquals(600, result.projectedActive)
    }

    @Test
    fun sameHourOnHardMedian_stepsToHarder() {
        val result = classify(liveActive = 200)
        assertEquals(2, result.step)
        assertEquals(ActiveDayClassifier.Kind.HARDER, result.kind)
        assertEquals(900, result.projectedActive)
        assertTrue(result.hourlyReady)
    }

    @Test
    fun closenessBetweenThresholds_leansHalfway() {
        // Medians 40 and 200. live 160 is 40 from the hard median and 120 from
        // the light one: closeness 0.75, inside [0.65, 0.85).
        val result = classify(liveActive = 160)
        assertEquals(1, result.step)
        assertEquals(750, result.projectedActive)
        assertEquals(ActiveDayClassifier.Kind.HARDER, result.kind)
    }

    @Test
    fun fewerThanFiveSameHourSamples_staysOnBlend() {
        val result = classify(
            liveActive = 900,
            hourlyByDay = hourly(hardDays = hardDates.take(4)),
        )
        assertEquals(0, result.step)
        assertFalse(result.hourlyReady)
        assertEquals(600, result.projectedActive)
    }

    @Test
    fun dayTypeAverage_isDestinationNotKMeans() {
        val result = classify(
            liveActive = 200,
            dayTypeAverage = 900,
            dayTypeDates = hardDates.map { it.toString() }.toSet(),
        )
        assertEquals(2, result.step)
        assertEquals(ActiveDayClassifier.Kind.DAY_TYPE, result.kind)
        assertEquals(900, result.projectedActive)
        assertEquals("Training", result.dayTypeName)
    }

    @Test
    fun pastDay_doesNotClassify() {
        val result = classify(isToday = false, liveActive = 900)
        assertEquals(0, result.step)
        assertEquals(0, result.projectedActive)
        assertEquals(ActiveDayClassifier.Kind.NONE, result.kind)
        assertFalse(result.hourlyReady)
    }
}
