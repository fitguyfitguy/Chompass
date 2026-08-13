package app.chompass.data

import app.chompass.services.weather.OmCity
import app.chompass.services.weather.OpenMeteoClient
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * Weather input for the dynamic water goal (issue #3 Phase 5). Resolves the
 * `expectedHighC` the WATER-DYN-A math consumes from one of two sources:
 *
 * - [SOURCE_MANUAL] — the existing manual °C wheel ([PreferencesStore.waterManualTempC]),
 *   always available, no network. Default and fallback.
 * - [SOURCE_OPEN_METEO] — direct Open-Meteo forecast for a manually chosen
 *   city (no API key, no account, no location permission). Trusted while its
 *   forecast date is today.
 *
 * The missing-source fallback to the manual °C keeps the reminder chain and
 * widget from ever breaking. [state] is a single reactive flow; callers
 * (Settings, Home, widget snapshot, reminder planner) consume the same
 * [WeatherState.effectiveHighC].
 *
 * (Weather-app broadcast input — Breezy Weather etc. — is parked: the
 * Gadgetbridge `ACTION_GENERIC_WEATHER` contract is documented in
 * docs/WEATHER_INTEGRATION_DESIGN.md for a future revisit.)
 */
class WeatherRepository(
    private val prefs: PreferencesStore,
    private val openMeteo: OpenMeteoClient,
) {
    companion object {
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_OPEN_METEO = "openmeteo"
    }

    /** Full reactive snapshot of the weather inputs; [effectiveHighC] feeds WATER-DYN-A. */
    data class WeatherState(
        val source: String,
        val manualHighC: Int,
        val omCity: OmCity?,
        val omHighC: Int?,
        val omDate: String?,
        val omUpdatedAtMillis: Long?,
    ) {
        /** High °C used by the water math for today. Falls back to the manual °C
         *  whenever the chosen source has no data fresh for today. */
        val effectiveHighC: Int
            get() = when (source) {
                SOURCE_OPEN_METEO -> if (omDate == LocalDate.now().toString()) omHighC ?: manualHighC else manualHighC
                else -> manualHighC
            }

        /** Whether the Open-Meteo cache applies to today (or is missing/stale). */
        val omFreshToday: Boolean get() = omDate == LocalDate.now().toString()
    }

    private data class OmCache(
        val city: OmCity?,
        val highC: Int?,
        val date: String?,
        val updatedAtMillis: Long?,
    )

    private val omWeatherFlow: Flow<OmCache> = combine(
        prefs.weatherOmCity,
        prefs.weatherOmHighC,
        prefs.weatherOmDate,
        prefs.weatherOmUpdatedAt,
    ) { city, highC, date, at ->
        OmCache(
            city = omCityFromCache(city),
            highC = highC.takeIf { it != 0 },
            date = date,
            updatedAtMillis = at.takeIf { it != 0L },
        )
    }

    val state: Flow<WeatherState> = combine(
        prefs.weatherSource,
        prefs.waterManualTempC,
        omWeatherFlow,
    ) { source, manualC, om ->
        WeatherState(
            source = source,
            manualHighC = manualC,
            omCity = om.city,
            omHighC = om.highC,
            omDate = om.date,
            omUpdatedAtMillis = om.updatedAtMillis,
        )
    }

    internal fun omCityFromCache(omCityJson: String?): OmCity? =
        omCityJson?.let { runCatching { prefs.json.decodeFromString(OmCity.serializer(), it) }.getOrNull() }

    suspend fun setSource(source: String) {
        prefs.setWeatherSource(source)
        if (source == SOURCE_OPEN_METEO) refreshOpenMeteo()
    }

    /** Selects an Open-Meteo city and fetches today's high immediately. */
    suspend fun selectOmCity(city: OmCity): Boolean {
        prefs.setWeatherOmCity(prefs.json.encodeToString(OmCity.serializer(), city))
        return refreshOpenMeteo()
    }

    /**
     * Fetches today's high for the selected city. No-op (true) when the cache
     * already covers today; false when there is no city or the fetch failed
     * (previous cache — or the manual fallback — stays in effect).
     */
    suspend fun refreshOpenMeteo(): Boolean {
        val city = omCityFromCache(prefs.weatherOmCity.first()) ?: return false
        if (prefs.weatherOmDate.first() == LocalDate.now().toString()) return true
        val highC = openMeteo.todayHighC(city) ?: return false
        prefs.setWeatherOmHighC(highC)
        prefs.setWeatherOmDate(LocalDate.now().toString())
        prefs.setWeatherOmUpdatedAt(System.currentTimeMillis())
        return true
    }
}
