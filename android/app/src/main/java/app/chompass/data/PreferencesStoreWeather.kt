package app.chompass.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Weather input for the dynamic water goal (issue #3 Phase 5). The source
// pref + caches back [WeatherRepository]; see docs/WEATHER_INTEGRATION_DESIGN.md.

internal val PreferencesStore.weatherSourceImpl: Flow<String>
    get() = stringPref(Keys.WEATHER_SOURCE)
        .map { it ?: WeatherRepository.SOURCE_MANUAL }
internal suspend fun PreferencesStore.setWeatherSourceImpl(v: String) =
    setStringPref(Keys.WEATHER_SOURCE, v)

internal val PreferencesStore.weatherOmCityImpl: Flow<String?>
    get() = stringPref(Keys.WEATHER_OM_CITY)
internal suspend fun PreferencesStore.setWeatherOmCityImpl(v: String?) =
    setStringPrefOrRemove(Keys.WEATHER_OM_CITY, v)

internal val PreferencesStore.weatherOmHighCImpl: Flow<Int>
    get() = intPref(Keys.WEATHER_OM_HIGH_C, 0)
internal suspend fun PreferencesStore.setWeatherOmHighCImpl(v: Int) =
    setIntPref(Keys.WEATHER_OM_HIGH_C, v)

internal val PreferencesStore.weatherOmDateImpl: Flow<String?>
    get() = stringPref(Keys.WEATHER_OM_DATE)
internal suspend fun PreferencesStore.setWeatherOmDateImpl(v: String?) =
    setStringPrefOrRemove(Keys.WEATHER_OM_DATE, v)

internal val PreferencesStore.weatherOmUpdatedAtImpl: Flow<Long>
    get() = longPref(Keys.WEATHER_OM_UPDATED_AT, 0L)
internal suspend fun PreferencesStore.setWeatherOmUpdatedAtImpl(v: Long) =
    setLongPref(Keys.WEATHER_OM_UPDATED_AT, v)
