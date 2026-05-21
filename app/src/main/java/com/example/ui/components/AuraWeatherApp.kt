package com.example.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.FavoriteCity
import com.example.data.model.*
import com.example.ui.WeatherUiState
import com.example.ui.WeatherViewModel
import com.example.ui.HistoricalUiState
import androidx.compose.ui.text.font.FontStyle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AuraWeatherApp(viewModel: WeatherViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val favoriteCities by viewModel.favoriteCities.collectAsStateWithLifecycle()
    val isCurrentCityFavorite by viewModel.isCurrentCityFavorite.collectAsStateWithLifecycle()
    val geminiInsight by viewModel.geminiInsight.collectAsStateWithLifecycle()
    val isGeneratingInsight by viewModel.isGeneratingInsight.collectAsStateWithLifecycle()
    val historicalUiState by viewModel.historicalUiState.collectAsStateWithLifecycle()
    val historyQueryDate by viewModel.historyQueryDate.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val verticalScrollState = rememberScrollState()

    // Location Permission State using Accompanist
    val locationPermissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    // Helper functions for GPS location fetching
    @SuppressLint("MissingPermission")
    fun requestDeviceLocationAndLoad() {
                            val fuses = locationPermissionsState.allPermissionsGranted || 
                                    locationPermissionsState.permissions.any { it.status.isGranted }
                            if (fuses) {
                                try {
                                    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                                    fusedLocationClient.lastLocation
                                        .addOnSuccessListener { location: Location? ->
                                            if (location != null) {
                                                viewModel.loadWeather(
                                                    lat = location.latitude,
                                                    lon = location.longitude,
                                                    cityName = "Current Location"
                                                )
                                            } else {
                                                Toast.makeText(context, "No location lock. Please search manually.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        .addOnFailureListener {
                                            Toast.makeText(context, "Failed to get location. Use search instead.", Toast.LENGTH_SHORT).show()
                                        }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Location services unavailable on this device.", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                locationPermissionsState.launchMultiplePermissionRequest()
                            }
    }

    // Determine Theme parameters dynamically from Current Success state or default midnight colors
    val themeConfig = remember(uiState) {
        when (uiState) {
            is WeatherUiState.Success -> (uiState as WeatherUiState.Success).weatherState.dynamicThemeColors
            else -> WeatherThemeConfig(
                isNight = true,
                backgroundStart = 0xFF0B1218,
                backgroundEnd = 0xFF16222F,
                accentColor = 0xFFD0E4FF,
                labelText = "Atmosphere Aura"
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // Dynamic Dynamic Background Gradient and floating elements
        AtmosphericBackground(config = themeConfig)

        // Main application frame
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            
            // --- TOP SEARCH AND OPTIONS ROW ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Real-time Autocomplete Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = { 
                        Text(
                            text = "Search city...", 
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 15.sp
                        ) 
                    },
                    leadingIcon = { 
                        Icon(
                            imageVector = Icons.Rounded.Search, 
                            contentDescription = "Search icon",
                            tint = Color.White.copy(alpha = 0.8f)
                        ) 
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(
                                    imageVector = Icons.Default.Close, 
                                    contentDescription = "Clear search",
                                    tint = Color.White
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .testTag("search_input"),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1A232E).copy(alpha = 0.6f),
                        unfocusedContainerColor = Color(0xFF1A232E).copy(alpha = 0.4f),
                        focusedBorderColor = Color.White.copy(alpha = 0.15f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.05f),
                        focusedTextColor = Color(0xFFE2E2E6),
                        unfocusedTextColor = Color(0xFFE2E2E6)
                    )
                )

                // Current GPS location trigger
                IconButton(
                    onClick = { requestDeviceLocationAndLoad() },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White.copy(alpha = 0.08f), CircleShape)
                        .testTag("gps_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MyLocation,
                        contentDescription = "Locate Me",
                        tint = Color.White
                    )
                }

                // Light / Dark Theme toggle (automatic reset option)
                IconButton(
                    onClick = { viewModel.toggleThemeOverride() },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White.copy(alpha = 0.08f), CircleShape)
                        .testTag("theme_toggle_button")
                ) {
                    val overrideState by viewModel.themeOverride.collectAsStateWithLifecycle()
                    val icon = when (overrideState) {
                        true -> Icons.Rounded.DarkMode
                        false -> Icons.Rounded.LightMode
                        null -> Icons.Rounded.Settings
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = "Theme Customization Mode",
                        tint = Color.White
                    )
                }
            }

            // --- SUGGESTIONS CONTAINER (Overlays below search bar when typing) ---
            if (searchQuery.length >= 2 && searchResults.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .wrapContentHeight()
                        .animateContentSize(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A232E).copy(alpha = 0.95f)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(8.dp)
                    ) {
                        searchResults.forEach { suggestion ->
                            val secondaryLabel = buildString {
                                if (!suggestion.admin1.isNullOrEmpty()) append(suggestion.admin1)
                                if (!suggestion.country.isNullOrEmpty()) {
                                    if (isNotEmpty()) append(", ")
                                    append(suggestion.country)
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        focusManager.clearFocus()
                                        viewModel.searchSuggestSelected(suggestion)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.LocationOn,
                                    contentDescription = "Pin Icon",
                                    tint = Color(0xFF63B3ED)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = suggestion.name,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    if (secondaryLabel.isNotEmpty()) {
                                        Text(
                                            text = secondaryLabel,
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- FAVORITE CITIES CHIPS ROW ---
            if (favoriteCities.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(favoriteCities) { city ->
                        Surface(
                            modifier = Modifier
                                .height(38.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .clickable {
                                    focusManager.clearFocus()
                                    viewModel.loadWeather(city.latitude, city.longitude, city.name)
                                },
                            color = Color(0xFF1A232E).copy(alpha = 0.65f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Favorite,
                                    contentDescription = "Fav Star",
                                    tint = Color(0xFFFF6B6B),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = city.name.substringBefore(','),
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Delete Fav",
                                    tint = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { viewModel.deleteFavoriteCity(city) }
                                )
                            }
                        }
                    }
                }
            }

            // --- MAIN WEATHER SCROLL VIEWS ---
            when (uiState) {
                is WeatherUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                is WeatherUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Error Indicator",
                                    tint = Color(0xFFFF6B6B),
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = (uiState as WeatherUiState.Error).message,
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 15.sp
                                )
                                Button(
                                    onClick = { viewModel.reloadCurrentWeather() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = "Retry",
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Retry Connection", color = Color.White)
                                }
                            }
                        }
                    }
                }
                is WeatherUiState.Success -> {
                    val weather = (uiState as WeatherUiState.Success).weatherState
                    val firstDay = weather.dailyItems.firstOrNull()
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(verticalScrollState)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        
                        // --- 1. HERO MAIN CARD ---
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // City Info with Favorite pin mechanism
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = weather.cityName,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = { viewModel.toggleFavorite() },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("favorite_toggle")
                                ) {
                                    Icon(
                                        imageVector = if (isCurrentCityFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                        contentDescription = "Toggle Saved",
                                        tint = if (isCurrentCityFavorite) Color(0xFFFF6B6B) else Color.White
                                    )
                                }
                            }

                            // Big Temp Text
                            Text(
                                text = "${weather.current.temperature.toInt()}°",
                                fontSize = 86.sp,
                                fontWeight = FontWeight.Light,
                                color = Color(0xFFD0E4FF),
                                modifier = Modifier.padding(vertical = 0.dp)
                            )

                            // Weather description
                            Text(
                                text = weather.weatherDescription,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.9f)
                            )

                            // High / Low temperatures and feels like
                            if (firstDay != null) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "H: ${firstDay.tempMax.toInt()}°",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.5.sp,
                                        color = Color(0xFFD0E4FF).copy(alpha = 0.8f)
                                    )
                                    Text(
                                        text = "L: ${firstDay.tempMin.toInt()}°",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.5.sp,
                                        color = Color(0xFFD0E4FF).copy(alpha = 0.5f)
                                    )
                                    Text(
                                        text = "•",
                                        fontSize = 10.sp,
                                        color = Color.White.copy(alpha = 0.3f)
                                    )
                                    Text(
                                        text = "FEELS LIKE ${weather.current.feelsLike.toInt()}°",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.5.sp,
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }

                        // --- WEATHER SEVERE ALERTS BANNER ---
                        if (weather.alerts.isNotEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                weather.alerts.forEach { alert ->
                                    val (cardBg, tintColor, borderLight) = when (alert.severity) {
                                        "Severe" -> Triple(Color(0xFF3B1E1E).copy(alpha = 0.85f), Color(0xFFFF6B6B), Color(0xFFFF6B6B).copy(alpha = 0.3f))
                                        "Moderate" -> Triple(Color(0xFF422F1E).copy(alpha = 0.85f), Color(0xFFFFAD46), Color(0xFFFFAD46).copy(alpha = 0.3f))
                                        else -> Triple(Color(0xFF1E283B).copy(alpha = 0.85f), Color(0xFFD0E4FF), Color(0xFFD0E4FF).copy(alpha = 0.2f))
                                    }
                                    
                                    Card(
                                        modifier = Modifier.fillMaxWidth().testTag("weather_alert_card"),
                                        shape = RoundedCornerShape(20.dp),
                                        colors = CardDefaults.cardColors(containerColor = cardBg),
                                        border = BorderStroke(1.dp, borderLight)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Icon(
                                                imageVector = if (alert.severity == "Severe") Icons.Rounded.Warning else Icons.Rounded.WarningAmber,
                                                contentDescription = "Severe Alert Indicator",
                                                tint = tintColor,
                                                modifier = Modifier.size(24.dp).padding(top = 2.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(
                                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        text = alert.title.uppercase(),
                                                        color = tintColor,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        letterSpacing = 1.5.sp
                                                    )
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = tintColor.copy(alpha = 0.15f)
                                                    ) {
                                                        Text(
                                                            text = alert.severity.uppercase(),
                                                            color = tintColor,
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                            letterSpacing = 1.sp
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = alert.description,
                                                    color = Color.White.copy(alpha = 0.9f),
                                                    fontSize = 13.sp,
                                                    lineHeight = 18.sp
                                                )
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(top = 4.dp)
                                                ) {
                                                    Text(
                                                        text = "Issued by ${alert.sender} • ${alert.timeLabel}",
                                                        color = Color.White.copy(alpha = 0.5f),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        letterSpacing = 0.5.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // --- 2. GEMINI SMART INFERENCE CARD ---
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A232E).copy(alpha = 0.8f)),
                            border = BorderStroke(1.dp, Color(0xFFD0E4FF).copy(alpha = 0.15f))
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = "Aura AI Sparks",
                                        tint = Color(0xFFD0E4FF),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "AURA AI INDICATION",
                                        color = Color(0xFFD0E4FF),
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.5.sp,
                                        fontSize = 11.sp
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    if (isGeneratingInsight) {
                                        CircularProgressIndicator(
                                            color = Color(0xFFD0E4FF),
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }

                                Crossfade(targetState = geminiInsight, label = "insight_carousel") { insight ->
                                    if (insight != null) {
                                        Text(
                                            text = insight,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Normal,
                                            lineHeight = 20.sp
                                        )
                                    } else {
                                        Text(
                                            text = "Consulting Aura's personal style engine to tailor your weather fit recommendations...",
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 13.sp,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                        )
                                    }
                                }
                            }
                        }

                        // --- 3. HOURLY FORECAST (Next 24 Hours) ---
                        GlassCard(title = "24-HOUR FORECAST", icon = Icons.Rounded.Cloud) {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(18.dp),
                                contentPadding = PaddingValues(bottom = 6.dp)
                            ) {
                                items(weather.hourlyItems) { item ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = item.timeLabel,
                                            color = Color.White.copy(alpha = 0.65f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        // Dynamic Vector symbols drawing corresponding to WMO code
                                        InteractiveWeatherIcon(code = item.weatherCode, isNight = item.isNight, modifier = Modifier.size(28.dp))
                                        
                                        Text(
                                            text = "${item.temperature.toInt()}°",
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // --- 4. AIR QUALITY INDEX (Classification & PM particles breakdown) ---
                        weather.airQuality?.let { aqi ->
                            GlassCard(title = "AIR QUALITY INDEX", icon = Icons.Rounded.Air) {
                                val uqiInt = aqi.aqiValue.toInt()
                                val (desc, progressVal, color) = getAqiRating(aqi.aqiValue)
                                
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Bottom
                                    ) {
                                        Text(
                                            text = "AQI • $uqiInt - $desc",
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // Colored slider showing quality rating position
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(Color.Green, Color.Yellow, Color(0xFFFFA500), Color.Red)
                                                )
                                            )
                                    ) {
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            drawCircle(
                                                color = Color.White,
                                                radius = 7.dp.toPx(),
                                                center = Offset(size.width * progressVal, size.height / 2f),
                                                style = Stroke(width = 2.dp.toPx())
                                            )
                                            drawCircle(
                                                color = color,
                                                radius = 5.dp.toPx(),
                                                center = Offset(size.width * progressVal, size.height / 2f)
                                            )
                                        }
                                    }

                                    // Particulate components micro-breakdown grid
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        AqiParticulateRow(label = "PM2.5", value = "${aqi.pm25?.toInt() ?: 0} µg/m³")
                                        AqiParticulateRow(label = "PM10", value = "${aqi.pm10?.toInt() ?: 0} µg/m³")
                                        AqiParticulateRow(label = "Ozone", value = "${aqi.ozone?.toInt() ?: 0} µg/m³")
                                        AqiParticulateRow(label = "NO₂", value = "${aqi.no2?.toInt() ?: 0} µg/m³")
                                    }
                                }
                            }
                        }

                        // --- 5. WEEKLY 7-DAY FORECAST ---
                        GlassCard(title = "7-DAY OUTLOOK", icon = Icons.Rounded.EventNote) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                weather.dailyItems.forEach { day ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        // Weekday text label
                                        Text(
                                            text = day.dateLabel,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.width(90.dp)
                                        )

                                        // Condition graphic
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            InteractiveWeatherIcon(code = day.weatherCode, isNight = false, modifier = Modifier.size(24.dp))
                                            Text(
                                                text = day.weatherDesc,
                                                color = Color.White.copy(alpha = 0.7f),
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        // Max-Min margins
                                        Row(
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${day.tempMin.toInt()}°",
                                                color = Color.White.copy(alpha = 0.5f),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.End,
                                                modifier = Modifier.width(32.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            // Simulated mini slider
                                            Box(
                                                modifier = Modifier
                                                    .width(50.dp)
                                                    .height(4.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.White.copy(alpha = 0.2f))
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "${day.tempMax.toInt()}°",
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.End,
                                                modifier = Modifier.width(32.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // --- 6. DETAILS PANEL (Wind / Humidity / UV Index / Visibility / Pressure) ---
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            DetailGridSquare(
                                title = "WIND SPEED",
                                value = "${weather.current.windSpeed.toInt()} km/h",
                                secondary = "Bearing: ${weather.current.windDirection.toInt()}°",
                                icon = Icons.Rounded.Air,
                                modifier = Modifier.weight(1f),
                                content = {
                                    // Simulated wind compass compass dial!
                                    Box(
                                        modifier = Modifier.size(42.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            drawCircle(color = Color.White.copy(alpha = 0.3f), style = Stroke(width = 1.5.dp.toPx()))
                                            // Arrow pointing wind Direction
                                            rotate(weather.current.windDirection.toFloat()) {
                                                drawLine(
                                                    color = Color.White,
                                                    start = Offset(size.width / 2f, size.height * 0.8f),
                                                    end = Offset(size.width / 2f, size.height * 0.2f),
                                                    strokeWidth = 2.dp.toPx(),
                                                    cap = StrokeCap.Round
                                                )
                                                // Pointer tip
                                                drawLine(
                                                    color = Color.White,
                                                    start = Offset(size.width / 2f, size.height * 0.2f),
                                                    end = Offset(size.width * 0.35f, size.height * 0.40f),
                                                    strokeWidth = 2.dp.toPx(),
                                                    cap = StrokeCap.Round
                                                )
                                                drawLine(
                                                    color = Color.White,
                                                    start = Offset(size.width / 2f, size.height * 0.2f),
                                                    end = Offset(size.width * 0.65f, size.height * 0.40f),
                                                    strokeWidth = 2.dp.toPx(),
                                                    cap = StrokeCap.Round
                                                )
                                            }
                                        }
                                    }
                                }
                            )

                            DetailGridSquare(
                                title = "HUMIDITY",
                                value = "${weather.current.humidity.toInt()}%",
                                secondary = "Dew point roughly clear",
                                icon = Icons.Rounded.WaterDrop,
                                modifier = Modifier.weight(1f),
                                content = {
                                    CircularGauge(percentage = (weather.current.humidity / 100f).toFloat())
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            val (uvDesc, uvProgress) = getUvIndexDescription(weather.current.uvIndex)
                            DetailGridSquare(
                                title = "UV INDEX",
                                value = "${weather.current.uvIndex.toInt()}",
                                secondary = uvDesc,
                                icon = Icons.Rounded.BrightnessMedium ?: Icons.Rounded.WbSunny,
                                modifier = Modifier.weight(1f),
                                content = {
                                    CircularGauge(percentage = uvProgress)
                                }
                            )

                            val visKM = weather.current.visibility / 1000.0
                            DetailGridSquare(
                                title = "VISIBILITY",
                                value = "${visKM.toInt()} km",
                                secondary = if (visKM >= 10) "Perfect clear look" else "Slight haze",
                                icon = Icons.Rounded.Visibility,
                                modifier = Modifier.weight(1f),
                                content = null
                            )
                        }

                        // --- 7. SUNSET / SUNRISE ARCH CHART ---
                        val sunriseTime = firstDay?.sunrise ?: ""
                        val sunsetTime = firstDay?.sunset ?: ""
                        if (sunriseTime.isNotEmpty() || sunsetTime.isNotEmpty()) {
                            GlassCard(title = "SUNSET & SUNRISE", icon = Icons.Rounded.WbTwilight) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("Sunrise", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                                        Text(sunriseTime, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // A visual arc drawing sunset/sunrise position relative to day
                                    Box(
                                        modifier = Modifier
                                            .size(width = 120.dp, height = 55.dp)
                                            .padding(top = 8.dp),
                                        contentAlignment = Alignment.BottomCenter
                                    ) {
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            // Arc line represent sun transit
                                            drawArc(
                                                color = Color.White.copy(alpha = 0.25f),
                                                startAngle = 180f,
                                                sweepAngle = 180f,
                                                useCenter = false,
                                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                                            )
                                            // Sun sphere marker half-way
                                            drawCircle(
                                                color = Color(0xFFFFD166),
                                                radius = 6.dp.toPx(),
                                                center = Offset(size.width / 2f, 6.dp.toPx())
                                            )
                                        }
                                    }

                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        Text("Sunset", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                                        Text(sunsetTime, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Pressure panel
                        GlassCard(title = "PRESSURE", icon = Icons.Rounded.Speed) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${weather.current.pressure.toInt()} hPa",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Standard sea level pressure is 1013 hPa",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.65f)
                                )
                            }
                        }

                        // --- HISTORICAL COMPARISON MODULE ---
                        GlassCard(title = "HISTORICAL COMPARISON", icon = Icons.Rounded.History) {
                            var dateInput by remember { mutableStateOf("") }
                            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                            var showDatePickerDialog by remember { mutableStateOf(false) }

                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "Compare current climate with past archives. Select a date to view historical conditions and spot 10-year temperature trendlines.",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )

                                // Quick Presets Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val contextLocal = LocalContext.current
                                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                    val cal = Calendar.getInstance()
                                    
                                    val presets = listOf(
                                        Pair("5 yrs ago", 5),
                                        Pair("10 yrs ago", 10),
                                        Pair("20 yrs ago", 20)
                                    )
                                    
                                    Text(
                                        text = "Offsets:",
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                    
                                    presets.forEach { (label, offset) ->
                                        AssistChip(
                                            onClick = {
                                                cal.time = Date()
                                                cal.add(Calendar.YEAR, -offset)
                                                // Adjust by 1 day back to ensure open-meteo has full completed daily archive data available
                                                cal.add(Calendar.DAY_OF_YEAR, -1)
                                                val presetDate = sdf.format(cal.time)
                                                dateInput = presetDate
                                                viewModel.queryHistoricalWeather(presetDate)
                                            },
                                            label = { Text(label, color = Color.White) },
                                            colors = AssistChipDefaults.assistChipColors(
                                                containerColor = Color.White.copy(alpha = 0.08f)
                                            ),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                                        )
                                    }
                                }

                                // Interactive Custom Date Dialog Trigger
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = dateInput,
                                        onValueChange = { dateInput = it },
                                        placeholder = { Text("YYYY-MM-DD", color = Color.White.copy(alpha = 0.4f)) },
                                        label = { Text("Custom Date", color = Color.White.copy(alpha = 0.8f)) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f).testTag("history_date_input"),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color.White,
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent
                                        ),
                                        trailingIcon = {
                                            IconButton(onClick = { showDatePickerDialog = true }) {
                                                Icon(
                                                    imageVector = Icons.Rounded.DateRange,
                                                    contentDescription = "Open Custom Date Picker",
                                                    tint = Color.White
                                                )
                                            }
                                        }
                                    )

                                    Button(
                                        onClick = {
                                            if (dateInput.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                                                viewModel.queryHistoricalWeather(dateInput)
                                            } else {
                                                Toast.makeText(context, "Enter a valid date: YYYY-MM-DD", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.height(56.dp).testTag("query_history_button"),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.White.copy(alpha = 0.12f),
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                                    ) {
                                        Text("Search", fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Interactive Custom Calendar Dialog (Fallback custom selection modal)
                                if (showDatePickerDialog) {
                                    var selYear by remember { mutableStateOf(currentYear - 5) }
                                    var selMonth by remember { mutableStateOf(5) } 
                                    var selDay by remember { mutableStateOf(21) }
                                    
                                    AlertDialog(
                                        onDismissRequest = { showDatePickerDialog = false },
                                        title = { Text("Select Historical Date", color = Color.White) },
                                        containerColor = Color(0xFF1E2836),
                                        text = {
                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                Text("Choose a date in the past archive. Note that archive records are available up to yesterday.", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                                                
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    // Year Input
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text("Year (${1950} - ${currentYear})", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                        OutlinedTextField(
                                                            value = selYear.toString(),
                                                            onValueChange = { selYear = it.toIntOrNull()?.coerceIn(1950, currentYear) ?: (currentYear - 5) },
                                                            singleLine = true,
                                                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                                                        )
                                                    }
                                                    
                                                    // Month Selector
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text("Month (1 - 12)", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                        OutlinedTextField(
                                                            value = selMonth.toString(),
                                                            onValueChange = { selMonth = it.toIntOrNull()?.coerceIn(1, 12) ?: 1 },
                                                            singleLine = true,
                                                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                                                        )
                                                    }
                                                    
                                                    // Day Selector
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text("Day (1 - 31)", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                        OutlinedTextField(
                                                            value = selDay.toString(),
                                                            onValueChange = { selDay = it.toIntOrNull()?.coerceIn(1, 31) ?: 1 },
                                                            singleLine = true,
                                                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        confirmButton = {
                                            TextButton(
                                                onClick = {
                                                    val formattedMonth = String.format("%02d", selMonth)
                                                    val formattedDay = String.format("%02d", selDay)
                                                    val fullDateS = "$selYear-$formattedMonth-$formattedDay"
                                                    dateInput = fullDateS
                                                    showDatePickerDialog = false
                                                    viewModel.queryHistoricalWeather(fullDateS)
                                                }
                                            ) {
                                                Text("SELECT", color = Color(0xFF5AB2FF), fontWeight = FontWeight.Bold)
                                            }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { showDatePickerDialog = false }) {
                                                Text("CANCEL", color = Color.White.copy(alpha = 0.6f))
                                            }
                                        }
                                    )
                                }

                                // Output display based on state
                                when (val hist = historicalUiState) {
                                    is HistoricalUiState.Idle -> {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().height(64.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "No date queried yet",
                                                color = Color.White.copy(alpha = 0.4f),
                                                fontSize = 13.sp,
                                                fontStyle = FontStyle.Italic
                                            )
                                        }
                                    }
                                    is HistoricalUiState.Loading -> {
                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                            Text("Retrieving historical conditions & decade trends...", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                                        }
                                    }
                                    is HistoricalUiState.Error -> {
                                        Text(
                                            text = hist.message,
                                            color = Color(0xFFFF6B6B),
                                            fontSize = 13.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                                        )
                                    }
                                    is HistoricalUiState.Success -> {
                                        val day = hist.dayInfo
                                        val trends = hist.trendList
                                        
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            // 1. Specific Past Day Information Block
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(16.dp),
                                                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(14.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    InteractiveWeatherIcon(
                                                        code = day.weatherCode,
                                                        isNight = false,
                                                        modifier = Modifier.size(48.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = day.dateLabel.uppercase(),
                                                            color = Color.White.copy(alpha = 0.5f),
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            letterSpacing = 1.sp
                                                        )
                                                        Text(
                                                            text = day.weatherDesc,
                                                            color = Color.White,
                                                            fontSize = 16.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            text = "Daily Average: ${day.avgTemp.toInt()}°C",
                                                            color = Color.White.copy(alpha = 0.7f),
                                                            fontSize = 12.sp
                                                        )
                                                    }
                                                    Column(horizontalAlignment = Alignment.End) {
                                                        Text(
                                                            text = "${day.tempMax.toInt()}°",
                                                            color = Color.White,
                                                            fontSize = 24.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            text = "${day.tempMin.toInt()}°",
                                                            color = Color.White.copy(alpha = 0.5f),
                                                            fontSize = 14.sp
                                                        )
                                                    }
                                                }
                                            }

                                            // 2. DECADE COMPARATIVE TRENDS GRAPH
                                            if (trends.isNotEmpty()) {
                                                Column(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    val avgOfDecade = trends.map { it.avgTemp }.average()
                                                    
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.Bottom
                                                    ) {
                                                        Text(
                                                            text = "Decade Trendline (Same Day / Month)".uppercase(),
                                                            color = Color.White.copy(alpha = 0.5f),
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            letterSpacing = 1.sp
                                                        )
                                                        Text(
                                                            text = "10yr Avg: ${String.format("%.1f", avgOfDecade)}°C",
                                                            color = Color(0xFF5AB2FF),
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }

                                                    Card(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        shape = RoundedCornerShape(16.dp),
                                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.4f)),
                                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                                                    ) {
                                                        Column(modifier = Modifier.padding(14.dp)) {
                                                            Canvas(
                                                                modifier = Modifier.fillMaxWidth().height(100.dp)
                                                            ) {
                                                                val width = size.width
                                                                val height = size.height
                                                                val maxTemp = (trends.maxOf { it.tempMax } + 3).coerceAtLeast(10.0)
                                                                val minTemp = (trends.minOf { it.tempMin } - 3).coerceAtLeast(-10.0)
                                                                val range = (maxTemp - minTemp).coerceAtLeast(1.0)
                                                                
                                                                val pointsCount = trends.size
                                                                val stepX = width / (pointsCount - 1).coerceAtLeast(1)
                                                                
                                                                val linePoints = trends.indices.map { idx ->
                                                                    val tItem = trends[idx]
                                                                    val x = idx * stepX
                                                                    val percentage = (tItem.avgTemp - minTemp) / range
                                                                    val y = height - (percentage * height).toFloat()
                                                                    Offset(x.toFloat(), y)
                                                                }
                                                                
                                                                val gridCount = 3
                                                                for (g in 0..gridCount) {
                                                                    val gY = height * g / gridCount
                                                                    drawLine(
                                                                        color = Color.White.copy(alpha = 0.05f),
                                                                        start = Offset(0f, gY),
                                                                        end = Offset(width, gY),
                                                                        strokeWidth = 1.dp.toPx()
                                                                    )
                                                                }
                                                                
                                                                if (linePoints.size > 1) {
                                                                    for (p in 0 until linePoints.size - 1) {
                                                                        drawLine(
                                                                            color = Color(0xFF5AB2FF),
                                                                            start = linePoints[p],
                                                                            end = linePoints[p + 1],
                                                                            strokeWidth = 3.dp.toPx(),
                                                                            cap = StrokeCap.Round
                                                                        )
                                                                    }
                                                                }
                                                                
                                                                linePoints.forEachIndexed { index, p ->
                                                                    drawCircle(
                                                                        color = Color.White,
                                                                        radius = 3.5.dp.toPx(),
                                                                        center = p
                                                                    )
                                                                    drawCircle(
                                                                        color = Color(0xFF007FFF),
                                                                        radius = 2.dp.toPx(),
                                                                        center = p
                                                                    )
                                                                }
                                                            }
                                                            
                                                            Spacer(modifier = Modifier.height(10.dp))
                                                            
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                trends.forEach { item ->
                                                                    Column(
                                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                                                    ) {
                                                                        Text(
                                                                            text = "'${item.year.toString().takeLast(2)}",
                                                                            color = Color.White.copy(alpha = 0.4f),
                                                                            fontSize = 9.sp,
                                                                            fontWeight = FontWeight.Bold
                                                                        )
                                                                        Text(
                                                                            text = "${item.avgTemp.toInt()}°",
                                                                            color = Color.White,
                                                                            fontSize = 11.sp,
                                                                            fontWeight = FontWeight.Bold
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

// --- AIR QUALITY & UV INTERPRETATION HELPERS ---
private fun getAqiRating(aqi: Double): Triple<String, Float, Color> {
    return when {
        aqi <= 50 -> Triple("Good", (aqi / 300f).toFloat().coerceAtMost(0.15f), Color.Green)
        aqi <= 100 -> Triple("Moderate", 0.15f + ((aqi - 50) / 100f).toFloat().coerceAtMost(0.20f), Color.Yellow)
        aqi <= 150 -> Triple("Unhealthy for Sensitive", 0.35f + ((aqi - 100) / 100f).toFloat().coerceAtMost(0.20f), Color(0xFFFF9F0A))
        aqi <= 200 -> Triple("Unhealthy", 0.55f + ((aqi - 150) / 100f).toFloat().coerceAtMost(0.20f), Color.Red)
        else -> Triple("Very Unhealthy", 0.75f + ((aqi - 200) / 300f).toFloat().coerceAtMost(0.25f), Color(0xFF8E44AD))
    }
}

private fun getUvIndexDescription(uv: Double): Pair<String, Float> {
    return when {
        uv <= 2 -> Pair("Low intensity", (uv / 12f).toFloat())
        uv <= 5 -> Pair("Moderate exposure", (uv / 12f).toFloat())
        uv <= 7 -> Pair("High - Seek shade", (uv / 12f).toFloat())
        uv <= 10 -> Pair("Very High exposure", (uv / 12f).toFloat())
        else -> Pair("Extreme exposure", (uv / 12f).toFloat().coerceAtMost(1f))
    }
}

// --- GLASS-MORPHIC CONTAINER OR CARD FOR MATERIAL 3 ---
@Composable
fun GlassCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF1A232E).copy(alpha = 0.65f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFFD0E4FF).copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = title.uppercase(),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
            }
            content()
        }
    }
}

@Composable
fun DetailGridSquare(
    title: String,
    value: String,
    secondary: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: (@Composable () -> Unit)? = null
) {
    Surface(
        modifier = modifier.aspectRatio(1.15f),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF1A232E),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFFD0E4FF).copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = title.uppercase(),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = value,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = secondary,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (content != null) {
                    Box(modifier = Modifier.padding(start = 4.dp)) {
                        content()
                    }
                }
            }
        }
    }
}

@Composable
fun CircularGauge(percentage: Float) {
    Box(
        modifier = Modifier.size(40.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Track
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                style = Stroke(width = 3.dp.toPx())
            )
            // Progress arc
            drawArc(
                color = Color(0xFFD0E4FF),
                startAngle = -90f,
                sweepAngle = 360f * percentage,
                useCenter = false,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
fun AqiParticulateRow(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(text = label, color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(text = value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

// --- INTERACTIVE CRISP COMPOSABLE WEATHER ICONS (Vector Graphics Canvas-style) ---
@Composable
fun InteractiveWeatherIcon(code: Int, isNight: Boolean, modifier: Modifier = Modifier) {
    val icon = when (code) {
        0 -> if (isNight) Icons.Rounded.NightsStay else Icons.Rounded.WbSunny
        1, 2, 3 -> if (isNight) Icons.Rounded.NightsStay else Icons.Rounded.CloudQueue ?: Icons.Rounded.Cloud
        45, 48 -> Icons.Rounded.BlurOn
        51, 53, 55, 56, 57 -> Icons.Rounded.WaterDrop
        61, 63, 65, 66, 67, 80, 81, 82 -> Icons.Rounded.WaterDrop
        71, 73, 75, 77, 85, 86 -> Icons.Rounded.AcUnit
        95, 96, 99 -> Icons.Rounded.Thunderstorm ?: Icons.Rounded.Warning
        else -> Icons.Rounded.Cloud
    }
    
    val tint = when (code) {
        0 -> if (isNight) Color(0xFFCBD5E1) else Color(0xFFFFD166)
        1, 2, 3 -> if (isNight) Color(0xFF94A3B8) else Color(0xFFE2E8F0)
        45, 48 -> Color(0xFFCBD5E1)
        51, 53, 55, 61, 63, 65, 80, 81, 82 -> Color(0xFF63B3ED)
        71, 73, 75, 77, 85, 86 -> Color(0xFFE2E8F0)
        95, 96, 99 -> Color(0xFFF6E05E)
        else -> Color.White
    }

    Icon(
        imageVector = icon,
        contentDescription = "Weather Icon Condition",
        tint = tint,
        modifier = modifier
    )
}
