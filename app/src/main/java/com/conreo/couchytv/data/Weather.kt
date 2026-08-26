package com.conreo.couchytv.data

import android.content.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Thematic bucket for a WMO weather code, so the icon set stays small. */
enum class WeatherIconKind { Clear, PartlyCloudy, Cloudy, Fog, Rain, Snow, Storm }

/** Maps Open-Meteo's WMO weather_code to a display bucket. */
fun iconKindFor(code: Int): WeatherIconKind = when (code) {
    0 -> WeatherIconKind.Clear
    1, 2 -> WeatherIconKind.PartlyCloudy
    3 -> WeatherIconKind.Cloudy
    45, 48 -> WeatherIconKind.Fog
    51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> WeatherIconKind.Rain
    71, 73, 75, 77, 85, 86 -> WeatherIconKind.Snow
    95, 96, 99 -> WeatherIconKind.Storm
    else -> WeatherIconKind.Cloudy
}

/** One day of the week-ahead forecast; date is ISO yyyy-MM-dd (Open-Meteo, timezone=auto). */
data class DailyForecast(val date: String, val low: Double, val high: Double, val kind: WeatherIconKind)

/** Temperatures are already in the unit the caller requested from Open-Meteo. */
data class WeatherData(
    val temp: Double,
    val low: Double,
    val high: Double,
    val kind: WeatherIconKind,
    val fetchedAt: Long,
    /** Today first, up to 7 days — powers the optional weekly-forecast dialog. */
    val daily: List<DailyForecast> = emptyList(),
)

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class ForecastResponse(
    val current: CurrentBlock? = null,
    val daily: DailyBlock? = null,
)
@Serializable
private data class CurrentBlock(
    @SerialName("temperature_2m") val temperature: Double = 0.0,
    @SerialName("weather_code") val weatherCode: Int = 0,
)
@Serializable
private data class DailyBlock(
    val time: List<String> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int> = emptyList(),
    @SerialName("temperature_2m_max") val max: List<Double> = emptyList(),
    @SerialName("temperature_2m_min") val min: List<Double> = emptyList(),
)

@Serializable
private data class IpApiResponse(val latitude: Double? = null, val longitude: Double? = null)

@Serializable
private data class ZippopotamResponse(val places: List<ZippopotamPlace> = emptyList())
@Serializable
private data class ZippopotamPlace(
    @SerialName("latitude") val lat: String = "",
    @SerialName("longitude") val lon: String = "",
)

/**
 * Weather + location lookups. Every call is a plain keyless HTTPS GET to a
 * FOSS-friendly service (Open-Meteo for weather; ipapi.co or zippopotam.us
 * only to resolve a coordinate, depending on the user's chosen source) — no
 * SDKs, no accounts, no device location permission.
 */
object WeatherRepository {

    // Resolving GeoIP/zip doesn't change within a session, so it's cached
    // separately from the weather refresh to minimize third-party requests.
    @Volatile private var cachedLocationKey: String? = null
    @Volatile private var cachedLocation: Pair<Double, Double>? = null

    @Volatile private var lastGood: WeatherData? = null

    private fun get(url: String): String? = runCatching {
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            requestMethod = "GET"
        }.let { conn ->
            conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        }
    }.getOrNull()

    private fun resolveLocation(config: LauncherConfig): Pair<Double, Double>? {
        val key = "${config.weatherLocationSource}|${config.weatherLat}|${config.weatherLon}|" +
            "${config.weatherZip}|${config.weatherCountry}"
        cachedLocation?.let { if (cachedLocationKey == key) return it }

        val resolved = when (config.weatherLocationSource) {
            WEATHER_SRC_LATLONG -> config.weatherLat to config.weatherLon
            WEATHER_SRC_ZIP -> {
                val country = config.weatherCountry.trim().ifEmpty { "us" }.lowercase()
                val zip = URLEncoder.encode(config.weatherZip.trim(), "UTF-8")
                if (zip.isEmpty()) null else
                    get("https://api.zippopotam.us/$country/$zip")
                        ?.let { runCatching { json.decodeFromString(ZippopotamResponse.serializer(), it) }.getOrNull() }
                        ?.places?.firstOrNull()
                        ?.let { p -> p.lat.toDoubleOrNull()?.let { lat -> lat to (p.lon.toDoubleOrNull() ?: return null) } }
            }
            else -> get("https://ipapi.co/json/")
                ?.let { runCatching { json.decodeFromString(IpApiResponse.serializer(), it) }.getOrNull() }
                ?.let { r -> r.latitude?.let { lat -> r.longitude?.let { lon -> lat to lon } } }
        }
        if (resolved != null) {
            cachedLocationKey = key
            cachedLocation = resolved
        }
        return resolved
    }

    /** Returns the last successful fetch on failure, so a blip doesn't blank the chip. */
    suspend fun fetch(context: Context, config: LauncherConfig): WeatherData? {
        val (lat, lon) = resolveLocation(config) ?: return lastGood
        val unit = if (config.weatherUnit == 1) "fahrenheit" else "celsius"
        // One request covers both the status-bar chip (today) and the optional
        // weekly-forecast dialog — 7 days of daily data is a tiny payload.
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min" +
            "&temperature_unit=$unit&timezone=auto&forecast_days=7"
        val body = get(url) ?: return lastGood
        val parsed = runCatching { json.decodeFromString(ForecastResponse.serializer(), body) }.getOrNull()
            ?: return lastGood
        val current = parsed.current ?: return lastGood
        val daily = parsed.daily?.let { d ->
            d.time.indices.map { i ->
                DailyForecast(
                    date = d.time[i],
                    low = d.min.getOrElse(i) { current.temperature },
                    high = d.max.getOrElse(i) { current.temperature },
                    kind = iconKindFor(d.weatherCode.getOrElse(i) { current.weatherCode }),
                )
            }
        }.orEmpty()
        val data = WeatherData(
            temp = current.temperature,
            low = daily.firstOrNull()?.low ?: current.temperature,
            high = daily.firstOrNull()?.high ?: current.temperature,
            kind = iconKindFor(current.weatherCode),
            fetchedAt = System.currentTimeMillis(),
            daily = daily,
        )
        lastGood = data
        return data
    }
}
