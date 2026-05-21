package com.example.data.api

import com.example.data.model.AirQualityResponse
import com.example.data.model.GeocodingResponse
import com.example.data.model.WeatherResponse
import com.example.data.model.HistoricalWeatherResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface WeatherApi {
    @GET("v1/forecast")
    suspend fun getFullWeatherForecast(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("current") currentFields: String = "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,rain,showers,snowfall,weather_code,cloud_cover,pressure_msl,wind_speed_10m,wind_direction_10m,uv_index,visibility",
        @Query("hourly") hourlyFields: String = "temperature_2m,relative_humidity_2m,weather_code,uv_index",
        @Query("daily") dailyFields: String = "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max",
        @Query("timezone") timezone: String = "auto"
    ): WeatherResponse
}

interface AirQualityApi {
    @GET("v1/air-quality")
    suspend fun getAirQuality(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("current") currentFields: String = "us_aqi,pm2_5,pm10,ozone,nitrogen_dioxide,sulfur_dioxide"
    ): AirQualityResponse
}

interface GeocodingApi {
    @GET("v1/search")
    suspend fun searchCity(
        @Query("name") name: String,
        @Query("count") count: Int = 10,
        @Query("language") language: String = "en",
        @Query("format") format: String = "json"
    ): GeocodingResponse
}

interface HistoricalApi {
    @GET("v1/archive")
    suspend fun getHistoricalWeather(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        @Query("daily") dailyFields: String = "temperature_2m_max,temperature_2m_min,weather_code",
        @Query("timezone") timezone: String = "auto"
    ): HistoricalWeatherResponse
}

object RetrofitClients {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    val weatherService: WeatherApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(WeatherApi::class.java)
    }

    val airQualityService: AirQualityApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://air-quality-api.open-meteo.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(AirQualityApi::class.java)
    }

    val geocodingService: GeocodingApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://geocoding-api.open-meteo.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeocodingApi::class.java)
    }

    val historicalService: HistoricalApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://archive-api.open-meteo.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(HistoricalApi::class.java)
    }
}
