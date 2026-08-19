package app.chompass.ui.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CalorieBarDownsampleTest {
    @Test
    fun weekStaysDaily() {
        val daily = (0..6).map { i ->
            LocalDate.of(2026, 8, 13).plusDays(i.toLong()) to 2000 + i
        }
        assertEquals(daily, downsampleCalorieBars(daily))
    }

    @Test
    fun monthStaysDaily() {
        val daily = (0..29).map { i ->
            LocalDate.of(2026, 8, 1).plusDays(i.toLong()) to 1800
        }
        assertEquals(daily, downsampleCalorieBars(daily))
    }

    @Test
    fun yearUsesWeeklyBucketsUnderTheCap() {
        val start = LocalDate.of(2025, 8, 20)
        val daily = (0..364).map { i -> start.plusDays(i.toLong()) to 2000 }
        val bars = downsampleCalorieBars(daily)
        assertTrue(bars.size <= 90)
        assertTrue(bars.size < daily.size)
        assertEquals(daily.sumOf { it.second }, bars.sumOf { it.second })
    }

    @Test
    fun allTimeStillCapsDrawCalls() {
        val start = LocalDate.of(2016, 8, 20)
        val daily = (0..3649).map { i -> start.plusDays(i.toLong()) to 1800 }
        val bars = downsampleCalorieBars(daily)
        assertTrue(bars.size <= 90)
        assertEquals(daily.sumOf { it.second }, bars.sumOf { it.second })
    }
}
