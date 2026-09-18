package net.orionx.messenger.weather

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A place the weather screen can show. The canvas ships a short list of preset cities. */
data class WeatherCity(val key: String, val name: String, val latitude: Double, val longitude: Double)

val ORION_WEATHER_CITIES = listOf(
    WeatherCity("msk", "москва", 55.7558, 37.6173),
    WeatherCity("spb", "петербург", 59.9311, 30.3609),
    WeatherCity("nsk", "новосибирск", 55.0084, 82.9357),
)

data class WeatherHour(val label: String, val temperature: Int, val code: Int)

data class WeatherDay(val label: String, val high: Int, val low: Int, val code: Int)

data class WeatherReport(
    val temperature: Int,
    val feelsLike: Int,
    val code: Int,
    val high: Int,
    val low: Int,
    val windKph: Int,
    val humidity: Int,
    val pressureMm: Int,
    val hours: List<WeatherHour>,
    val days: List<WeatherDay>,
)

/**
 * Open-Meteo — a free forecast API that needs no key or account, which is what the design calls
 * for. Everything the screen shows comes from a single request.
 */
object OrionWeatherApi {

    fun load(city: WeatherCity): WeatherReport {
        val url = buildString {
            append("https://api.open-meteo.com/v1/forecast")
            append("?latitude=").append(city.latitude)
            append("&longitude=").append(city.longitude)
            append("&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m,relative_humidity_2m,surface_pressure")
            append("&hourly=temperature_2m,weather_code")
            append("&daily=weather_code,temperature_2m_max,temperature_2m_min")
            append("&timezone=auto&forecast_days=5")
        }
        val body = get(url)
        val root = JSONObject(body)

        val current = root.getJSONObject("current")
        val daily = root.getJSONObject("daily")
        val hourly = root.getJSONObject("hourly")

        val dailyHigh = daily.getJSONArray("temperature_2m_max")
        val dailyLow = daily.getJSONArray("temperature_2m_min")
        val dailyCode = daily.getJSONArray("weather_code")
        val dailyTime = daily.getJSONArray("time")

        val hourTimes = hourly.getJSONArray("time")
        val hourTemps = hourly.getJSONArray("temperature_2m")
        val hourCodes = hourly.getJSONArray("weather_code")

        // The API returns the whole day from midnight; start the strip at the current hour.
        val nowIso = current.optString("time")
        val startIndex = (0 until hourTimes.length()).firstOrNull { hourTimes.getString(it) >= nowIso } ?: 0
        val hours = (startIndex until minOf(startIndex + 7, hourTimes.length())).map { index ->
            WeatherHour(
                label = if (index == startIndex) "сейчас" else hourTimes.getString(index).substringAfter('T').take(5),
                temperature = hourTemps.getDouble(index).toInt(),
                code = hourCodes.getInt(index),
            )
        }

        val days = (0 until dailyTime.length()).map { index ->
            WeatherDay(
                label = weekdayLabel(dailyTime.getString(index)),
                high = dailyHigh.getDouble(index).toInt(),
                low = dailyLow.getDouble(index).toInt(),
                code = dailyCode.getInt(index),
            )
        }

        return WeatherReport(
            temperature = current.getDouble("temperature_2m").toInt(),
            feelsLike = current.getDouble("apparent_temperature").toInt(),
            code = current.getInt("weather_code"),
            high = dailyHigh.getDouble(0).toInt(),
            low = dailyLow.getDouble(0).toInt(),
            windKph = current.getDouble("wind_speed_10m").toInt(),
            humidity = current.getDouble("relative_humidity_2m").toInt(),
            // Open-Meteo reports hPa; the canvas shows millimetres of mercury.
            pressureMm = (current.getDouble("surface_pressure") * 0.750062).toInt(),
            hours = hours,
            days = days,
        )
    }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 12_000
            connection.readTimeout = 12_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            require(code in 200..299) { "weather http $code" }
            text
        } finally {
            connection.disconnect()
        }
    }

    private fun weekdayLabel(isoDate: String): String = runCatching {
        val date = java.time.LocalDate.parse(isoDate)
        when (date.dayOfWeek) {
            java.time.DayOfWeek.MONDAY -> "пн"
            java.time.DayOfWeek.TUESDAY -> "вт"
            java.time.DayOfWeek.WEDNESDAY -> "ср"
            java.time.DayOfWeek.THURSDAY -> "чт"
            java.time.DayOfWeek.FRIDAY -> "пт"
            java.time.DayOfWeek.SATURDAY -> "сб"
            java.time.DayOfWeek.SUNDAY -> "вс"
        }
    }.getOrDefault(isoDate.takeLast(2))
}

/** WMO weather codes, collapsed to the handful of conditions the design shows. */
enum class WeatherLook { SUN, PARTLY, CLOUD, RAIN, SNOW, STORM, FOG }

fun weatherLook(code: Int): WeatherLook = when (code) {
    0 -> WeatherLook.SUN
    1, 2 -> WeatherLook.PARTLY
    3 -> WeatherLook.CLOUD
    45, 48 -> WeatherLook.FOG
    in 51..57, in 61..67, in 80..82 -> WeatherLook.RAIN
    in 71..77, in 85..86 -> WeatherLook.SNOW
    in 95..99 -> WeatherLook.STORM
    else -> WeatherLook.CLOUD
}

fun weatherCondition(code: Int): String = when (code) {
    0 -> "ясно"
    1 -> "малооблачно"
    2 -> "переменная облачность"
    3 -> "пасмурно"
    45, 48 -> "туман"
    in 51..57 -> "морось"
    in 61..67 -> "дождь"
    in 71..77 -> "снег"
    in 80..82 -> "ливень"
    85, 86 -> "снегопад"
    in 95..99 -> "гроза"
    else -> "облачно"
}
