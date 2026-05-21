package com.example.data.repository

import com.example.data.api.RetrofitClients
import com.example.data.db.FavoriteCity
import com.example.data.db.FavoriteCityDao
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class WeatherRepository(private val favoriteCityDao: FavoriteCityDao) {

    // --- Database Local Operations ---
    val favoriteCities: Flow<List<FavoriteCity>> = favoriteCityDao.getAllFavoritesFlow()

    suspend fun addFavorite(city: FavoriteCity) {
        favoriteCityDao.insertFavorite(city)
    }

    suspend fun removeFavorite(city: FavoriteCity) {
        favoriteCityDao.deleteFavorite(city)
    }

    suspend fun removeFavoriteById(cityId: Long) {
        favoriteCityDao.deleteFavoriteById(cityId)
    }

    suspend fun isCityFavorite(cityId: Long): Boolean {
        return favoriteCityDao.isFavorite(cityId)
    }

    // --- Remote API Queries ---
    suspend fun searchCitySuggestions(query: String): List<GeocodingResult> = withContext(Dispatchers.IO) {
        if (query.trim().length < 2) return@withContext emptyList()
        try {
            val response = RetrofitClients.geocodingService.searchCity(query)
            response.results ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun fetchWeather(latitude: Double, longitude: Double, cityName: String): WeatherState = withContext(Dispatchers.IO) {
        // Fetch Weather Forecast (API 1)
        val weatherResponse = RetrofitClients.weatherService.getFullWeatherForecast(latitude, longitude)
        // Fetch Air Quality Index (API 2)
        val airQualityResponse = try {
            RetrofitClients.airQualityService.getAirQuality(latitude, longitude)
        } catch (e: Exception) {
            null
        }

        val current = weatherResponse.current ?: throw Exception("Current weather information is missing")
        val hourly = weatherResponse.hourly ?: throw Exception("Hourly forecast is missing")
        val daily = weatherResponse.daily ?: throw Exception("Daily forecast is missing")

        // 1. Determine if it's currently night based on time of day or sunrise/sunset lists
        val isNight = checkIsNight(current.time, daily.sunriseList.firstOrNull(), daily.sunsetList.firstOrNull())

        // 2. Map Dynamic Background Themes
        val themeConfig = mapWeatherCodeToTheme(current.weatherCode, isNight)

        // 3. Construct 24-hours forecast
        val hourlyItems = mutableListOf<HourlyDisplayItem>()
        val currentTimeMillis = System.currentTimeMillis()
        val timeParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)

        // Process up to 24 future hours
        var count = 0
        for (i in hourly.time.indices) {
            if (count >= 24) break
            try {
                val epoch = timeParser.parse(hourly.time[i])?.time ?: 0L
                // Include this hour if it is roughly current or in the future
                if (epoch >= currentTimeMillis - 1800000) { // Current time minus 30 mins
                    val hourIsNight = checkIsNight(hourly.time[i], daily.sunriseList.firstOrNull(), daily.sunsetList.firstOrNull())
                    hourlyItems.add(
                        HourlyDisplayItem(
                            timeLabel = formatHourlyTimeLabel(hourly.time[i]),
                            temperature = hourly.temperatureList[i],
                            weatherCode = hourly.weatherCodeList[i],
                            weatherDesc = getWeatherDescription(hourly.weatherCodeList[i]),
                            isNight = hourIsNight
                        )
                    )
                    count++
                }
            } catch (e: Exception) {
                // If parsing fails, fall back to sequential items
                if (i < 24) {
                    hourlyItems.add(
                        HourlyDisplayItem(
                            timeLabel = formatHourlyTimeLabel(hourly.time[i]),
                            temperature = hourly.temperatureList[i],
                            weatherCode = hourly.weatherCodeList[i],
                            weatherDesc = getWeatherDescription(hourly.weatherCodeList[i]),
                            isNight = isNight
                        )
                    )
                    count++
                }
            }
        }

        // 4. Construct 7-day forecast
        val dailyItems = daily.time.indices.map { i ->
            DailyDisplayItem(
                dateLabel = formatDailyDateLabel(daily.time[i]),
                tempMax = daily.tempMaxList[i],
                tempMin = daily.tempMinList[i],
                weatherCode = daily.weatherCodeList[i],
                weatherDesc = getWeatherDescription(daily.weatherCodeList[i]),
                sunrise = formatSunriseSunsetTime(daily.sunriseList[i]),
                sunset = formatSunriseSunsetTime(daily.sunsetList[i]),
                uvMax = daily.uvIndexMaxList?.getOrNull(i) ?: 0.0
            )
        }

        val detectedAlerts = detectWeatherAlerts(cityName, current, dailyItems)

        WeatherState(
            cityName = cityName,
            current = current,
            hourlyItems = hourlyItems,
            dailyItems = dailyItems,
            airQuality = airQualityResponse?.current,
            isRaining = current.weatherCode in listOf(51, 53, 55, 61, 63, 65, 80, 81, 82, 95, 96, 99),
            weatherDescription = getWeatherDescription(current.weatherCode),
            dynamicThemeColors = themeConfig,
            alerts = detectedAlerts
        )
    }

    private fun detectWeatherAlerts(
        cityName: String,
        current: CurrentWeather,
        dailyItems: List<DailyDisplayItem>
    ): List<WeatherAlert> {
        val alertList = mutableListOf<WeatherAlert>()
        
        // 1. Extreme Heat Warning
        if (current.temperature >= 38.0) {
            alertList.add(
                WeatherAlert(
                    title = "Extreme Heat Warning",
                    description = "Dangerous heat indices expected over 40°C. Drink plenty of fluids, stay in air-conditioned rooms, and stay out of the sun.",
                    severity = "Severe",
                    sender = "National Weather Centre",
                    timeLabel = "Now - Tomorrow evening"
                )
            )
        } else if (current.temperature >= 35.0) {
            alertList.add(
                WeatherAlert(
                    title = "Heat Advisory",
                    description = "High temperatures may cause heat-related illnesses. Limit strenuous outdoor activities.",
                    severity = "Moderate",
                    sender = "National Weather Centre",
                    timeLabel = "Now - 8:00 PM"
                )
            )
        }

        // 2. Severe Thunderstorms
        if (current.weatherCode in listOf(95, 96, 99)) {
            alertList.add(
                WeatherAlert(
                    title = "Severe Thunderstorm Warning",
                    description = "A severe thunderstorm capable of producing heavy winds, frequent thunder, and hail is active in $cityName. Take shelter inside immediately.",
                    severity = "Severe",
                    sender = "Meteorological Agency",
                    timeLabel = "Active until 9:00 PM"
                )
            )
        }

        // 3. Heavy Rain / Flash Flood Warnings
        if (current.weatherCode in listOf(65, 82) || (current.windSpeed > 40.0 && current.weatherCode in listOf(63, 81))) {
            alertList.add(
                WeatherAlert(
                    title = "Flash Flood Advisory",
                    description = "Torrential rains are causing rapid accumulation of runoff water. Turn around, don't drown when encountering flooded roads.",
                    severity = "Severe",
                    sender = "Emergency Services",
                    timeLabel = "Active now"
                )
            )
        }

        // 4. Extreme Cold Warning / Winter Storm
        if (current.temperature <= -5.0) {
            alertList.add(
                WeatherAlert(
                    title = "Extreme Cold Warning",
                    description = "Dangerously cold arctic air mass in place. Frostbite can occur in minutes. Restrict exposure outdoors and dress in layers.",
                    severity = "Severe",
                    sender = "Arctic Safety Patrol",
                    timeLabel = "Until Wednesday"
                )
            )
        } else if (current.temperature <= 0.0) {
            alertList.add(
                WeatherAlert(
                    title = "Freeze Warning",
                    description = "Sub-freezing temperatures will kill crops, other sensitive outdoor vegetation, and potentially damage outdoor plumbing.",
                    severity = "Moderate",
                    sender = "National Climate Society",
                    timeLabel = "Tonight"
                )
            )
        }

        // 5. Windy Gale Warning
        if (current.windSpeed >= 50.0) {
            alertList.add(
                WeatherAlert(
                    title = "High Wind Warning",
                    description = "Damaging winds exceeding 50 km/h will make driving difficult and could blow down loose objects and tree limbs.",
                    severity = "Severe",
                    sender = "Maritime & Atmospheric Board",
                    timeLabel = "Until midnight"
                )
            )
        } else if (current.windSpeed >= 35.0) {
            alertList.add(
                WeatherAlert(
                    title = "Wind Advisory",
                    description = "Strong winds may blow around unsecured objects. Use extra caution when driving high-profile vehicles.",
                    severity = "Advisory",
                    sender = "Maritime & Atmospheric Board",
                    timeLabel = "Expected until 6:00 PM"
                )
            )
        }

        // 6. UV Index Warning
        if (current.uvIndex >= 10.0) {
            alertList.add(
                WeatherAlert(
                    title = "Extreme UV Alert",
                    description = "Very high risk of harm from unprotected sun exposure. Sunscreen SPF 30+ and protective wear are highly recommended.",
                    severity = "Advisory",
                    sender = "Global Health Organization",
                    timeLabel = "11:00 AM - 4:00 PM"
                )
            )
        }

        // 7. Dense Fog / Low Visibility
        if (current.visibility <= 1000.0 && current.visibility > 0.0) {
            alertList.add(
                WeatherAlert(
                    title = "Dense Fog Advisory",
                    description = "Visibility is reduced below 1 km. Sudden drops in visibility will make driving hazardous. Maintain safe following distances.",
                    severity = "Moderate",
                    sender = "Highways Transport Authority",
                    timeLabel = "Until 10:00 AM"
                )
            )
        }

        if (alertList.isEmpty()) {
            val upcomingSevereDay = dailyItems.firstOrNull { it.weatherCode in listOf(95, 96, 99) }
            if (upcomingSevereDay != null) {
                alertList.add(
                    WeatherAlert(
                        title = "Thunderstorm Watch",
                        description = "Atmospheric stability is degrading. Isolated thunderstorms with heavy downpour are expected during the 7-day outlook.",
                        severity = "Advisory",
                        sender = "Regional Forecast Office",
                        timeLabel = "Outlook for ${upcomingSevereDay.dateLabel}"
                    )
                )
            }
        }

        return alertList
    }

    suspend fun fetchHistoricalWeather(latitude: Double, longitude: Double, dateStr: String): Pair<HistoricalDayInfo, List<HistoricalTrendItem>> = withContext(Dispatchers.IO) {
        val response = RetrofitClients.historicalService.getHistoricalWeather(latitude, longitude, dateStr, dateStr)
        val daily = response.daily ?: throw Exception("Historical daily forecast unavailable for this date range.")
        
        val tempMax = daily.tempMaxList.firstOrNull() ?: 0.0
        val tempMin = daily.tempMinList.firstOrNull() ?: 0.0
        val code = daily.weatherCodeList?.firstOrNull() ?: 0
        val desc = getWeatherDescription(code)
        
        val displayLabel = try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val formatter = SimpleDateFormat("MMM d, yyyy", Locale.US)
            val parsedDate = parser.parse(dateStr)
            if (parsedDate != null) formatter.format(parsedDate) else dateStr
        } catch (e: Exception) {
            dateStr
        }
        
        val dayInfo = HistoricalDayInfo(
            dateLabel = displayLabel,
            tempMax = tempMax,
            tempMin = tempMin,
            weatherCode = code,
            weatherDesc = desc,
            avgTemp = (tempMax + tempMin) / 2.0
        )
        
        val trendItems = mutableListOf<HistoricalTrendItem>()
        val parts = dateStr.split("-")
        if (parts.size == 3) {
            val month = parts[1]
            val day = parts[2]
            
            val currentYearCalendar = Calendar.getInstance().get(Calendar.YEAR)
            val yearsToFetch = (1..10).map { offset ->
                currentYearCalendar - offset
            }.sorted()
            
            val fetchResults = yearsToFetch.map { targetYear ->
                val queryDate = "$targetYear-$month-$day"
                try {
                    val yearResponse = RetrofitClients.historicalService.getHistoricalWeather(latitude, longitude, queryDate, queryDate)
                    val yd = yearResponse.daily
                    if (yd != null) {
                        val maxT = yd.tempMaxList.firstOrNull() ?: 0.0
                        val minT = yd.tempMinList.firstOrNull() ?: 0.0
                        val avgT = (maxT + minT) / 2.0
                        HistoricalTrendItem(
                            year = targetYear,
                            tempMax = maxT,
                            tempMin = minT,
                            avgTemp = avgT
                        )
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }
            }
            fetchResults.filterNotNull().forEach {
                trendItems.add(it)
            }
        }
        
        dayInfo to trendItems
    }

    // --- Detail Parse Helpers for minSdk 24 compatibility (No java.time) ---

    private fun checkIsNight(timeStr: String, sunriseStr: String?, sunsetStr: String?): Boolean {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
            val currentDate = parser.parse(timeStr) ?: return false
            
            val calendar = Calendar.getInstance()
            calendar.time = currentDate
            val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
            val currentMin = calendar.get(Calendar.MINUTE)
            val currentMinsTotal = currentHour * 60 + currentMin

            val srDate = sunriseStr?.let { parser.parse(it) }
            val ssDate = sunsetStr?.let { parser.parse(it) }

            if (srDate != null && ssDate != null) {
                calendar.time = srDate
                val srMins = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
                calendar.time = ssDate
                val ssMins = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

                currentMinsTotal < srMins || currentMinsTotal > ssMins
            } else {
                // Approximate fallback: night is before 6 AM or after 7 PM
                currentHour < 6 || currentHour >= 19
            }
        } catch (e: Exception) {
            val hour = timeStr.substringAfter('T').substringBefore(':').toIntOrNull() ?: 12
            hour < 6 || hour >= 19
        }
    }

    private fun formatHourlyTimeLabel(timeStr: String): String {
        return try {
            val timePart = timeStr.substringAfter('T') // "14:00"
            val hourStr = timePart.substringBefore(':')
            val hour = hourStr.toInt()
            val suffix = if (hour >= 12) "PM" else "AM"
            val displayHour = when {
                hour == 0 -> 12
                hour > 12 -> hour - 12
                else -> hour
            }
            "$displayHour $suffix"
        } catch (e: Exception) {
            timeStr
        }
    }

    private fun formatDailyDateLabel(dateStr: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val formatter = SimpleDateFormat("EEE, d MMM", Locale.US)
            val date = parser.parse(dateStr) ?: return dateStr
            
            // Check if it is today
            val today = Calendar.getInstance()
            val itemCal = Calendar.getInstance().apply { time = date }
            
            if (today.get(Calendar.YEAR) == itemCal.get(Calendar.YEAR) &&
                today.get(Calendar.DAY_OF_YEAR) == itemCal.get(Calendar.DAY_OF_YEAR)) {
                return "Today"
            }
            
            formatter.format(date)
        } catch (e: Exception) {
            dateStr
        }
    }

    fun formatSunriseSunsetTime(timeStr: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
            val formatter = SimpleDateFormat("h:mm a", Locale.US)
            val date = parser.parse(timeStr)
            if (date != null) formatter.format(date) else timeStr.substringAfter('T')
        } catch (e: Exception) {
            timeStr.substringAfter('T')
        }
    }

    fun getWeatherDescription(code: Int): String {
        return when (code) {
            0 -> "Clear Sky"
            1 -> "Mainly Clear"
            2 -> "Partly Cloudy"
            3 -> "Overcast"
            45, 48 -> "Fog"
            51, 53, 55 -> "Drizzle"
            56, 57 -> "Freezing Drizzle"
            61, 63, 65 -> "Continuous Rain"
            66, 67 -> "Freezing Rain"
            71, 73, 75 -> "Snowfall"
            77 -> "Snow Grains"
            80, 81, 82 -> "Rain Showers"
            85, 86 -> "Snow Showers"
            95 -> "Thunderstorm"
            96, 99 -> "Heavy Hail Storm"
            else -> "Atmospheric Aura"
        }
    }

    fun mapWeatherCodeToTheme(code: Int, isNight: Boolean): WeatherThemeConfig {
        return when (code) {
            0 -> { // Clear sky
                if (isNight) {
                    WeatherThemeConfig(
                        isNight = true,
                        backgroundStart = 0xFF0B1218,
                        backgroundEnd = 0xFF14202D,
                        accentColor = 0xFFD0E4FF,
                        labelText = "Midnight Clear"
                    )
                } else {
                    WeatherThemeConfig(
                        isNight = false,
                        backgroundStart = 0xFF0B1218,
                        backgroundEnd = 0xFF1A2736,
                        accentColor = 0xFFE0EEFF,
                        labelText = "Sunny Glow"
                    )
                }
            }
            1, 2, 3 -> { // Light/Partly/Overcast clouds
                if (isNight) {
                    WeatherThemeConfig(
                        isNight = true,
                        backgroundStart = 0xFF0B1218,
                        backgroundEnd = 0xFF131B24,
                        accentColor = 0xFFB9CBE2,
                        labelText = "Overcast Dark"
                    )
                } else {
                    WeatherThemeConfig(
                        isNight = false,
                        backgroundStart = 0xFF0B1218,
                        backgroundEnd = 0xFF182330,
                        accentColor = 0xFFD0E4FF,
                        labelText = "Atmospheric Clouds"
                    )
                }
            }
            45, 48 -> { // Fog
                WeatherThemeConfig(
                    isNight = isNight,
                    backgroundStart = 0xFF0B1218,
                    backgroundEnd = 0xFF19222B,
                    accentColor = 0xFFB2C5DB,
                    labelText = "Mystic Fog"
                )
            }
            51, 53, 55, 56, 57 -> { // Drizzle
                WeatherThemeConfig(
                    isNight = isNight,
                    backgroundStart = 0xFF0B1218,
                    backgroundEnd = 0xFF15222E,
                    accentColor = 0xFF9FC2EC,
                    labelText = "Soft Drizzle"
                )
            }
            61, 63, 65, 66, 67, 80, 81, 82 -> { // Rain / heavy / showers
                if (isNight) {
                    WeatherThemeConfig(
                        isNight = true,
                        backgroundStart = 0xFF0B1218,
                        backgroundEnd = 0xFF112133,
                        accentColor = 0xFF90CDF4,
                        labelText = "Midnight Rain"
                    )
                } else {
                    WeatherThemeConfig(
                        isNight = false,
                        backgroundStart = 0xFF0B1218,
                        backgroundEnd = 0xFF162B42,
                        accentColor = 0xFFEBF8FF,
                        labelText = "Gleaming Rainfall"
                    )
                }
            }
            71, 73, 75, 77, 85, 86 -> { // Snow
                WeatherThemeConfig(
                    isNight = isNight,
                    backgroundStart = 0xFF0B1218,
                    backgroundEnd = 0xFF1B2A3A,
                    accentColor = 0xFFEBF8FF,
                    labelText = "Sleet & Snow"
                )
            }
            95, 96, 99 -> { // Storm
                WeatherThemeConfig(
                    isNight = isNight,
                    backgroundStart = 0xFF0B1218,
                    backgroundEnd = 0xFF141E28,
                    accentColor = 0xFFF7FAFC,
                    labelText = "Severe Thunder"
                )
            }
            else -> {
                WeatherThemeConfig(
                    isNight = isNight,
                    backgroundStart = 0xFF0B1218,
                    backgroundEnd = 0xFF152435,
                    accentColor = 0xFFD0E4FF,
                    labelText = "Quiet Atmospheric"
                )
            }
        }
    }
}
