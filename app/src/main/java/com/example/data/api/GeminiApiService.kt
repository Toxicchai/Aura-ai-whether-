package com.example.data.api

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    @Json(name = "contents") val contents: List<GeminiContent>
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    @Json(name = "parts") val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    @Json(name = "text") val text: String
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    @Json(name = "candidates") val candidates: List<GeminiCandidate>?
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    @Json(name = "content") val content: GeminiContent?
)

interface GeminiApi {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateInsight(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiClient {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val api: GeminiApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApi::class.java)
    }

    suspend fun getWeatherInsight(
        temp: Double,
        feelsLike: Double,
        condition: String,
        humidity: Double,
        windSpeed: Double,
        aqi: Double,
        cityName: String
    ): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY" || apiKey.contains("PLACEHOLDER")) {
            return@withContext "Provide a valid Gemini API Key in the AI Studio secrets panel to unlock real-time Gemini weather insights & Outfit Guidance."
        }

        val prompt = """
            You are Aura, an elite, sassy, yet practical AI lifestyle and weather stylist.
            Provide a short, extremely engaging 2-sentence outdoor outfit recommendation and activity guidance based on the following real-time weather in $cityName:
            - Temperature: $temp°C (Feels like $feelsLike°C)
            - Weather condition: $condition
            - Humidity: $humidity%
            - Wind Speed: $windSpeed km/h
            - Air Quality (US AQI): $aqi (0-50 is Good, 51-100 Moderate, 101+ Poor)
            
            Keep your response short, premium, conversational, and practical (under 50 words). Focus strictly on outfit pairing and advice. Include one relevant modern emoji. Don't sound like generic AI.
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(GeminiPart(text = prompt))
                )
            )
        )

        try {
            val response = api.generateInsight(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                ?: "Unable to style your insight at the moment. Breathe in the aura."
        } catch (e: Exception) {
            "Breathe in the current aura. AI insights are temporarily pausing: ${e.localizedMessage}"
        }
    }
}
