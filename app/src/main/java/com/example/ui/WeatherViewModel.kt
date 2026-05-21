package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.GeminiClient
import com.example.data.db.AppDatabase
import com.example.data.db.FavoriteCity
import com.example.data.model.GeocodingResult
import com.example.data.model.WeatherState
import com.example.data.model.HistoricalDayInfo
import com.example.data.model.HistoricalTrendItem
import com.example.data.repository.WeatherRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface WeatherUiState {
    object Loading : WeatherUiState
    data class Success(val weatherState: WeatherState) : WeatherUiState
    data class Error(val message: String) : WeatherUiState
}

sealed interface HistoricalUiState {
    object Idle : HistoricalUiState
    object Loading : HistoricalUiState
    data class Success(val dayInfo: HistoricalDayInfo, val trendList: List<HistoricalTrendItem>) : HistoricalUiState
    data class Error(val message: String) : HistoricalUiState
}

@OptIn(FlowPreview::class)
class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = WeatherRepository(db.favoriteCityDao())

    // UI states
    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    // Dark/Light override: null = auto system, true = force dark, false = force light
    private val _themeOverride = MutableStateFlow<Boolean?>(null)
    val themeOverride: StateFlow<Boolean?> = _themeOverride.asStateFlow()

    // Query states
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchResults = MutableStateFlow<List<GeocodingResult>>(emptyList())
    val searchResults: StateFlow<List<GeocodingResult>> = _searchResults.asStateFlow()

    // Favorites flow
    val favoriteCities: StateFlow<List<FavoriteCity>> = repository.favoriteCities
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isCurrentCityFavorite = MutableStateFlow(false)
    val isCurrentCityFavorite: StateFlow<Boolean> = _isCurrentCityFavorite.asStateFlow()

    // Gemini states
    private val _geminiInsight = MutableStateFlow<String?>(null)
    val geminiInsight: StateFlow<String?> = _geminiInsight.asStateFlow()

    private val _isGeneratingInsight = MutableStateFlow(false)
    val isGeneratingInsight: StateFlow<Boolean> = _isGeneratingInsight.asStateFlow()

    // Historical operations state
    private val _historicalUiState = MutableStateFlow<HistoricalUiState>(HistoricalUiState.Idle)
    val historicalUiState: StateFlow<HistoricalUiState> = _historicalUiState.asStateFlow()

    private val _historyQueryDate = MutableStateFlow("")
    val historyQueryDate: StateFlow<String> = _historyQueryDate.asStateFlow()

    // Holds the currently loaded coordinates to easily reload or toggle favorites
    private var lastLoadedCoords: Pair<Double, Double>? = null
    private var lastLoadedCityName: String = "Delhi"

    init {
        // Debounced search queries for smooth autocomplete
        viewModelScope.launch {
            _searchQuery
                .debounce(400)
                .distinctUntilChanged()
                .collect { query ->
                    if (query.trim().length >= 2) {
                        _isSearching.value = true
                        val results = repository.searchCitySuggestions(query)
                        _searchResults.value = results
                        _isSearching.value = false
                    } else {
                        _searchResults.value = emptyList()
                    }
                }
        }

        // Standard default weather retrieval
        loadWeather(28.6139, 77.2090, "Delhi") // Default beautiful starting coordinate: New Delhi
    }

    fun loadWeather(lat: Double, lon: Double, cityName: String) {
        viewModelScope.launch {
            _uiState.value = WeatherUiState.Loading
            _geminiInsight.value = null
            resetHistoricalQuery()
            lastLoadedCoords = Pair(lat, lon)
            lastLoadedCityName = cityName

            try {
                val state = repository.fetchWeather(lat, lon, cityName)
                _uiState.value = WeatherUiState.Success(state)

                // Update current active favorites check
                // Generate simple hash for pinning coordinates that are generalGPS (without open-meteo ID)
                val cityId = generateCityId(lat, lon)
                _isCurrentCityFavorite.value = repository.isCityFavorite(cityId)

                // Fetch Gemini Insights matching loaded metrics
                generateAIOutfitInsight(state)

            } catch (e: Exception) {
                _uiState.value = WeatherUiState.Error(e.localizedMessage ?: "Network connection error. Try again.")
            }
        }
    }

    fun queryHistoricalWeather(dateStr: String) {
        val coords = lastLoadedCoords ?: Pair(28.6139, 77.2090)
        if (dateStr.isEmpty()) return
        _historyQueryDate.value = dateStr
        viewModelScope.launch {
            _historicalUiState.value = HistoricalUiState.Loading
            try {
                val (dayInfo, trendList) = repository.fetchHistoricalWeather(coords.first, coords.second, dateStr)
                _historicalUiState.value = HistoricalUiState.Success(dayInfo, trendList)
            } catch (e: Exception) {
                _historicalUiState.value = HistoricalUiState.Error(e.localizedMessage ?: "Failed to retrieve historical conditions.")
            }
        }
    }

    fun resetHistoricalQuery() {
        _historicalUiState.value = HistoricalUiState.Idle
        _historyQueryDate.value = ""
    }

    fun reloadCurrentWeather() {
        val coords = lastLoadedCoords ?: Pair(28.6139, 77.2090)
        loadWeather(coords.first, coords.second, lastLoadedCityName)
    }

    private fun generateAIOutfitInsight(state: WeatherState) {
        viewModelScope.launch {
            _isGeneratingInsight.value = true
            try {
                // Determine air quality index
                val aqi = state.airQuality?.aqiValue ?: 20.0
                val insight = GeminiClient.getWeatherInsight(
                    temp = state.current.temperature,
                    feelsLike = state.current.feelsLike,
                    condition = state.weatherDescription,
                    humidity = state.current.humidity,
                    windSpeed = state.current.windSpeed,
                    aqi = aqi,
                    cityName = state.cityName
                )
                _geminiInsight.value = insight
            } catch (e: Exception) {
                _geminiInsight.value = "AI stylings temporarily static. Breathe the fresh air around you."
            } finally {
                _isGeneratingInsight.value = false
            }
        }
    }

    fun toggleFavorite() {
        val coords = lastLoadedCoords ?: return
        val state = _uiState.value
        if (state is WeatherUiState.Success) {
            val successState = state.weatherState
            val cityId = generateCityId(coords.first, coords.second)

            viewModelScope.launch {
                if (_isCurrentCityFavorite.value) {
                    repository.removeFavoriteById(cityId)
                    _isCurrentCityFavorite.value = false
                } else {
                    val favorite = FavoriteCity(
                        id = cityId,
                        name = successState.cityName,
                        country = null,
                        admin1 = null,
                        latitude = coords.first,
                        longitude = coords.second
                    )
                    repository.addFavorite(favorite)
                    _isCurrentCityFavorite.value = true
                }
            }
        }
    }

    fun deleteFavoriteCity(city: FavoriteCity) {
        viewModelScope.launch {
            repository.removeFavorite(city)
            // If the deleted city is currently displayed, update favorite icon
            val currentCoords = lastLoadedCoords
            if (currentCoords != null) {
                val currentId = generateCityId(currentCoords.first, currentCoords.second)
                if (currentId == city.id) {
                    _isCurrentCityFavorite.value = false
                }
            }
        }
    }

    fun searchSuggestSelected(suggestion: GeocodingResult) {
        _searchQuery.value = "" // Clear query to close search dropdown
        _searchResults.value = emptyList()
        val displayName = buildString {
            append(suggestion.name)
            if (!suggestion.admin1.isNullOrEmpty()) append(", ${suggestion.admin1}")
            if (!suggestion.country.isNullOrEmpty()) append(", ${suggestion.country}")
        }
        loadWeather(suggestion.latitude, suggestion.longitude, displayName)
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleThemeOverride() {
        _themeOverride.value = when (_themeOverride.value) {
            null -> true   // Force dark
            true -> false  // Force light
            false -> null  // Reset to auto (system preference)
        }
    }

    private fun generateCityId(lat: Double, lon: Double): Long {
        // Generate a deterministic unique ID based on latitude and longitude coordinates
        val multiplier = 10000.0
        val lHash = (lat * multiplier).toLong()
        val rHash = (lon * multiplier).toLong()
        return (lHash * 31) + rHash
    }
}
