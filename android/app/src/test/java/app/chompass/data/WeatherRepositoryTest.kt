package app.chompass.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Weather-source resolution feeding WATER-DYN-A: the Open-Meteo cache is
 * trusted while its date is today; anything missing/stale falls back to the
 * manual °C so the goal never breaks.
 */
class WeatherRepositoryTest {
    private fun state(
        source: String = WeatherRepository.SOURCE_MANUAL,
        manual: Int = 25,
        omHighC: Int? = null,
        omDate: String? = null,
    ) = WeatherRepository.WeatherState(
        source = source,
        manualHighC = manual,
        omCity = null,
        omHighC = omHighC,
        omDate = omDate,
        omUpdatedAtMillis = null,
    )

    @Test
    fun resolution_manualSource_usesManualValue() {
        assertEquals(25, state(WeatherRepository.SOURCE_MANUAL, manual = 25).effectiveHighC)
    }

    @Test
    fun resolution_meteoSource_freshToday_usesOmHigh() {
        val s = state(
            source = WeatherRepository.SOURCE_OPEN_METEO,
            manual = 19,
            omHighC = 31,
            omDate = LocalDate.now().toString(),
        )
        assertEquals(31, s.effectiveHighC)
    }

    @Test
    fun resolution_meteoSource_staleDate_fallsBackToManual() {
        val s = state(
            source = WeatherRepository.SOURCE_OPEN_METEO,
            manual = 19,
            omHighC = 31,
            omDate = LocalDate.now().minusDays(1).toString(),
        )
        assertEquals(19, s.effectiveHighC)
    }

    @Test
    fun resolution_meteoSource_noCache_fallsBackToManual() {
        val s = state(source = WeatherRepository.SOURCE_OPEN_METEO, manual = 22)
        assertEquals(22, s.effectiveHighC)
    }

    @Test
    fun omFreshToday_reflectsDate() {
        val fresh = state(
            source = WeatherRepository.SOURCE_OPEN_METEO,
            omHighC = 30,
            omDate = LocalDate.now().toString(),
        )
        val stale = state(
            source = WeatherRepository.SOURCE_OPEN_METEO,
            omHighC = 30,
            omDate = LocalDate.now().minusDays(1).toString(),
        )
        assertEquals(true, fresh.omFreshToday)
        assertEquals(false, stale.omFreshToday)
    }
}
