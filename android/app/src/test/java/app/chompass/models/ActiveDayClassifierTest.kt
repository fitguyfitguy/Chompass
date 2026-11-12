package app.chompass.models

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        blendedActive: Int = 600,
        dayTypeAverage: Int? = null,
        dayTypeDates: Set<String> = emptySet(),
    ) = ActiveDayClassifier.classify(
        today = today,
        minuteOfDay = minuteOfDay,
        isToday = isToday,
        blendedActive = blendedActive,
        dayTypeAverage = dayTypeAverage,
        dayTypeName = if (dayTypeAverage != null) "Training" else null,
        mergedActiveByDay = daily,
        hourlyByDay = hourly(),
        liveActive = 0,
        dayTypeDates = dayTypeDates,
    )

    @Test
    fun minuteZero_withDayType_showsTheAverage() {
        val result = classify(
            minuteOfDay = 0,
            dayTypeAverage = 900,
            dayTypeDates = hardDates.map { it.toString() }.toSet(),
        )
        assertEquals(900, result.projectedActive)
        assertEquals(ActiveDayClassifier.Kind.DAY_TYPE, result.kind)
    }

    @Test
    fun noDayType_staysOnBlend() {
        val result = classify()
        assertEquals(600, result.projectedActive)
        assertEquals(ActiveDayClassifier.Kind.NONE, result.kind)
    }

    @Test
    fun dayTypeDestination_usesPastDaysNotCallerAverage() {
        val result = classify(
            dayTypeAverage = 100,
            dayTypeDates = hardDates.map { it.toString() }.toSet(),
        )
        assertEquals(900, result.projectedActive)
    }

    @Test
    fun pastDay_doesNotProject() {
        val result = classify(isToday = false, dayTypeAverage = 900)
        assertEquals(0, result.projectedActive)
    }

    @Test
    fun divergence_namesTheCloserDayType() {
        val result = ActiveDayClassifier.divergence(
            today = today,
            minuteOfDay = 540,
            liveActive = 40,
            currentName = "Training",
            currentDates = hardDates.map { it.toString() }.toSet(),
            others = listOf(
                ActiveDayClassifier.DayTypeGroup(
                    profileId = "rest",
                    name = "Rest",
                    dates = lightDates.map { it.toString() }.toSet(),
                ),
            ),
            hourlyByDay = hourly(),
            mergedActiveByDay = daily,
        )
        assertEquals("Rest", result?.suggestedName)
        assertEquals("rest", result?.suggestedProfileId)
        assertEquals("Training", result?.currentName)
    }

    @Test
    fun divergence_staysQuietWhenTheHourMatches() {
        val result = ActiveDayClassifier.divergence(
            today = today,
            minuteOfDay = 540,
            liveActive = 200,
            currentName = "Training",
            currentDates = hardDates.map { it.toString() }.toSet(),
            others = listOf(
                ActiveDayClassifier.DayTypeGroup(
                    profileId = "rest",
                    name = "Rest",
                    dates = lightDates.map { it.toString() }.toSet(),
                ),
            ),
            hourlyByDay = hourly(),
            mergedActiveByDay = daily,
        )
        assertNull(result)
    }

    @Test
    fun divergence_needsEnoughSameHourSamples() {
        val result = ActiveDayClassifier.divergence(
            today = today,
            minuteOfDay = 540,
            liveActive = 40,
            currentName = "Training",
            currentDates = hardDates.take(4).map { it.toString() }.toSet(),
            others = listOf(
                ActiveDayClassifier.DayTypeGroup(
                    profileId = "rest",
                    name = "Rest",
                    dates = lightDates.map { it.toString() }.toSet(),
                ),
            ),
            hourlyByDay = hourly(hardDays = hardDates.take(4)),
            mergedActiveByDay = daily,
        )
        assertNull(result)
    }
}
