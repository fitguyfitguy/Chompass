package app.chompass.services.weather

import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Open-Meteo geocoding hit. */
@Serializable
data class OmCity(
    val id: Long,
    val name: String,
    val country: String? = null,
    val admin1: String? = null,
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
) {
    /** "Berlin, Land Berlin, Germany" — compact fallback "Berlin". */
    val displayName: String
        get() = listOfNotNull(name, admin1, country).joinToString(", ")
            .ifBlank { name }
}

/**
 * Minimal Open-Meteo client: city search (geocoding API) + today's max
 * temperature (forecast API). No API key, no account, no location permission —
 * the user's manually chosen city is all that is ever sent. Free for
 * non-commercial use; data CC BY 4.0 (attribution shown in Settings).
 */
class OpenMeteoClient(
    private val okHttp: OkHttpClient,
    /** Injectable for tests; default is the public endpoint. */
    private val geocodingBaseUrl: String = "https://geocoding-api.open-meteo.com/v1/search",
    /** Injectable for tests; default is the public endpoint. */
    private val forecastBaseUrl: String = "https://api.open-meteo.com/v1/forecast",
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun searchCities(query: String): List<OmCity> {
        if (query.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            val url = geocodingBaseUrl.toHttpUrl().newBuilder()
                .addQueryParameter("name", query.trim())
                .addQueryParameter("count", "8")
                .addQueryParameter("language", "en")
                .addQueryParameter("format", "json")
                .build()
            runCatching {
                okHttp.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) return@use emptyList<OmCity>()
                    val body = response.body?.string() ?: return@use emptyList()
                    json.decodeFromString<GeocodingResponse>(body).results.orEmpty()
                }
            }.getOrDefault(emptyList())
        }
    }

    /** Today's forecast high in °C for [city], null when the fetch fails. */
    suspend fun todayHighC(city: OmCity): Int? {
        return withContext(Dispatchers.IO) {
            val url = forecastBaseUrl.toHttpUrl().newBuilder()
                .addQueryParameter("latitude", city.latitude.toString())
                .addQueryParameter("longitude", city.longitude.toString())
                .addQueryParameter("daily", "temperature_2m_max")
                .addQueryParameter("timezone", city.timezone)
                .addQueryParameter("forecast_days", "1")
                .build()
            runCatching {
                okHttp.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body?.string() ?: return@use null
                    val parsed = json.decodeFromString<ForecastResponse>(body)
                    parsed.daily?.temperature_2m_max?.firstOrNull()?.roundToInt()
                }
            }.getOrNull()
        }
    }

    @Serializable
    private data class GeocodingResponse(val results: List<OmCity>? = null)

    @Serializable
    private data class ForecastResponse(val daily: Daily? = null) {
        @Serializable
        data class Daily(val time: List<String>? = null, val temperature_2m_max: List<Double>? = null)
    }
}
