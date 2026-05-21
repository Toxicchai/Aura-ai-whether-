package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// --- Geocoding City Search Models ---
@JsonClass(generateAdapter = true)
data class GeocodingResponse(
    @Json(name = "results") val results: List<GeocodingResult>?
)

@JsonClass(generateAdapter = true)
data class GeocodingResult(
    @Json(name = "id") val id: Long,
    @Json(name = "name") val name: String,
    @Json(name = "latitude") val latitude: Double,
    @Json(name = "longitude") val longitude: Double,
    @Json(name = "country") val country: String?,
    @Json(name = "admin1") val admin1: String?,
    @Json(name = "country_code") val countryCode: String?
)

// --- Forecast Response Models ---
@JsonClass(generateAdapter = true)
data class WeatherResponse(
    @Json(name = "latitude") val latitude: Double,
    @Json(name = "longitude") val longitude: Double,
    @Json(name = "timezone") val timezone: String,
    @Json(name = "current") val current: CurrentWeather?,
    @Json(name = "hourly") val hourly: HourlyForecast?,
    @Json(name = "daily") val daily: DailyForecast?
)

@JsonClass(generateAdapter = true)
data class CurrentWeather(
    @Json(name = "time") val time: String,
    @Json(name = "temperature_2m") val temperature: Double,
    @Json(name = "relative_humidity_2m") val humidity: Double,
    @Json(name = "apparent_temperature") val feelsLike: Double,
    @Json(name = "weather_code") val weatherCode: Int,
    @Json(name = "pressure_msl") val pressure: Double,
    @Json(name = "wind_speed_10m") val windSpeed: Double,
    @Json(name = "wind_direction_10m") val windDirection: Double,
    @Json(name = "uv_index") val uvIndex: Double,
    @Json(name = "visibility") val visibility: Double
)

@JsonClass(generateAdapter = true)
data class HourlyForecast(
    @Json(name = "time") val time: List<String>,
    @Json(name = "temperature_2m") val temperatureList: List<Double>,
    @Json(name = "relative_humidity_2m") val humidityList: List<Double>?,
    @Json(name = "weather_code") val weatherCodeList: List<Int>,
    @Json(name = "uv_index") val uvIndexList: List<Double>?
)

@JsonClass(generateAdapter = true)
data class DailyForecast(
    @Json(name = "time") val time: List<String>,
    @Json(name = "weather_code") val weatherCodeList: List<Int>,
    @Json(name = "temperature_2m_max") val tempMaxList: List<Double>,
    @Json(name = "temperature_2m_min") val tempMinList: List<Double>,
    @Json(name = "sunrise") val sunriseList: List<String>,
    @Json(name = "sunset") val sunsetList: List<String>,
    @Json(name = "uv_index_max") val uvIndexMaxList: List<Double>?
)

// --- Air Quality Model ---
@JsonClass(generateAdapter = true)
data class AirQualityResponse(
    @Json(name = "latitude") val latitude: Double,
    @Json(name = "longitude") val longitude: Double,
    @Json(name = "current") val current: AirQualityData?
)

@JsonClass(generateAdapter = true)
data class AirQualityData(
    @Json(name = "time") val time: String,
    @Json(name = "us_aqi") val aqiValue: Double,
    @Json(name = "pm2_5") val pm25: Double?,
    @Json(name = "pm10") val pm10: Double?,
    @Json(name = "ozone") val ozone: Double?,
    @Json(name = "nitrogen_dioxide") val no2: Double?,
    @Json(name = "sulfur_dioxide") val so2: Double?
)

// --- Unified Formatted State representation for Clean UI Rendering ---
data class WeatherState(
    val cityName: String,
    val current: CurrentWeather,
    val hourlyItems: List<HourlyDisplayItem>,
    val dailyItems: List<DailyDisplayItem>,
    val airQuality: AirQualityData?,
    val isRaining: Boolean,
    val weatherDescription: String,
    val dynamicThemeColors: WeatherThemeConfig,
    val alerts: List<WeatherAlert> = emptyList()
)

data class WeatherAlert(
    val title: String,
    val description: String,
    val severity: String, // "Severe", "Moderate", "Advisory"
    val sender: String,
    val timeLabel: String
)

@JsonClass(generateAdapter = true)
data class HistoricalWeatherResponse(
    @Json(name = "latitude") val latitude: Double,
    @Json(name = "longitude") val longitude: Double,
    @Json(name = "daily") val daily: HistoricalDailyData?
)

@JsonClass(generateAdapter = true)
data class HistoricalDailyData(
    @Json(name = "time") val time: List<String>,
    @Json(name = "temperature_2m_max") val tempMaxList: List<Double>,
    @Json(name = "temperature_2m_min") val tempMinList: List<Double>,
    @Json(name = "weather_code") val weatherCodeList: List<Int>?
)

data class HistoricalDayInfo(
    val dateLabel: String,
    val tempMax: Double,
    val tempMin: Double,
    val weatherCode: Int,
    val weatherDesc: String,
    val avgTemp: Double
)

data class HistoricalTrendItem(
    val year: Int,
    val tempMax: Double,
    val tempMin: Double,
    val avgTemp: Double
)

data class HourlyDisplayItem(
    val timeLabel: String,         // e.g. "10:00 AM" or "14:00"
    val temperature: Double,
    val weatherCode: Int,
    val weatherDesc: String,
    val isNight: Boolean
)

data class DailyDisplayItem(
    val dateLabel: String,         // e.g. "Monday"
    val tempMax: Double,
    val tempMin: Double,
    val weatherCode: Int,
    val weatherDesc: String,
    val sunrise: String,          // formatted e.g. "05:32 AM"
    val sunset: String,           // formatted e.g. "08:14 PM"
    val uvMax: Double
)

data class WeatherThemeConfig(
    val isNight: Boolean,
    val backgroundStart: Long,     // Hex value, e.g. 0xFF192A56
    val backgroundEnd: Long,
    val accentColor: Long,         // For text highlights & action buttons
    val labelText: String          // String showing condition name
)
