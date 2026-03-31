package com.ghostpin.app.ui

import android.content.Context
import android.location.LocationManager
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostpin.app.data.ProfileManager
import com.ghostpin.app.data.RouteRepository
import com.ghostpin.app.data.SimulationRepository
import com.ghostpin.app.data.SimulationConfig
import com.ghostpin.app.data.db.FavoriteSimulationEntity
import com.ghostpin.app.data.db.SimulationHistoryEntity
import com.ghostpin.app.routing.GeocodingProvider
import com.ghostpin.app.routing.GpxParser
import com.ghostpin.app.service.estimateRuntimeDurationSec
import com.ghostpin.app.service.SimulationState
import com.ghostpin.core.model.AppMode
import com.ghostpin.core.model.DefaultCoordinates
import com.ghostpin.core.model.MovementProfile
import com.ghostpin.core.model.Route
import com.ghostpin.core.model.Waypoint
import com.ghostpin.core.model.formatDuration
import com.ghostpin.core.math.GeoMath
import com.ghostpin.engine.interpolation.RepeatPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ghostpin.app.service.SimulationService

/**
 * ViewModel managing simulation UI state.
 *
 * Sprint 6 additions:
 * - [selectedMode] StateFlow holding the current [AppMode].
 * - [setAppMode] — switches mode and updates repository's manual-mode flag.
 * - [gpxLoadState] — tracks the result of the file-picker GPX import flow.
 * - [loadGpxFromUri] — reads a .gpx file, parses it with [GpxParser],
 * ```
 *    and pre-loads the resulting [Route] into [SimulationRepository] so
 *    [SimulationService] can use it without calling OSRM.
 * ```
 */
@HiltViewModel
class SimulationViewModel
@Inject
constructor(
        private val repository: SimulationRepository,
        private val profileManager: ProfileManager,
        private val routeRepository: RouteRepository,
        private val gpxParser: GpxParser,
        private val geocodingProvider: GeocodingProvider,
) : ViewModel() {

    init {}

    // ── Geocoding suggestions (Task 26) ──────────────────────────────────

    private val _geoSuggestions = MutableStateFlow<List<GeocodingProvider.GeoResult>>(emptyList())
    val geoSuggestions: StateFlow<List<GeocodingProvider.GeoResult>> = _geoSuggestions.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var searchJob: Job? = null

    /** Debounced address search — fires Nominatim query 400ms after last keystroke. */
    fun searchAddress(query: String) {
        searchJob?.cancel()
        if (query.length < 3) {
            _geoSuggestions.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(400) // debounce
            _isSearching.value = true
            _geoSuggestions.value = geocodingProvider.search(query)
            _isSearching.value = false
        }
    }

    /** Add a waypoint from a geocoding suggestion. */
    fun addWaypointFromGeoResult(result: GeocodingProvider.GeoResult) {
        addWaypoint(Waypoint(result.lat, result.lng, label = result.displayName))
        _geoSuggestions.value = emptyList()
    }

    fun clearSuggestions() {
        _geoSuggestions.value = emptyList()
    }



    private val _deviceLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    /**
     * Emits the real device coordinates once on first resolution. Observed by [InteractiveMap] to
     * call [MapController.moveTo] before the user places any pins. Null until [initializeLocation]
     * succeeds.
     */
    val deviceLocation: StateFlow<Pair<Double, Double>?> = _deviceLocation.asStateFlow()
    private var hasInitializedLocation = false

    fun initializeLocation(context: Context) {
        if (hasInitializedLocation) return
        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            var realLocation = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)

            val networkLoc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (networkLoc != null) {
                if (realLocation == null || networkLoc.time > realLocation.time) {
                    realLocation = networkLoc
                }
            }

            if (realLocation == null) {
                realLocation = lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
            }

            if (realLocation != null) {
                val shouldSeedPins =
                    _startLat.value == DefaultCoordinates.START_LAT &&
                        _startLng.value == DefaultCoordinates.START_LNG &&
                        _endLat.value == DefaultCoordinates.END_LAT &&
                        _endLng.value == DefaultCoordinates.END_LNG &&
                        _waypoints.value.isEmpty() &&
                        repository.route.value == null

                if (shouldSeedPins) {
                    _startLat.value = realLocation.latitude
                    _startLng.value = realLocation.longitude
                    _endLat.value = realLocation.latitude
                    _endLng.value = realLocation.longitude
                }
                // Signal the map to center — only set once (null → value)
                if (_deviceLocation.value == null) {
                    _deviceLocation.value = realLocation.latitude to realLocation.longitude
                }
                hasInitializedLocation = true
            }
        } catch (e: SecurityException) {
            // Permission not granted — silently fall back to defaults
        }
    }

    // ── Profile ───────────────────────────────────────────────────────────────

    val profiles: StateFlow<List<MovementProfile>> =
        profileManager.observeAll()
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                listOf(
                    MovementProfile.PEDESTRIAN,
                    MovementProfile.BICYCLE,
                    MovementProfile.CAR,
                    MovementProfile.URBAN_VEHICLE,
                    MovementProfile.DRONE,
                )
            )

    private val _selectedProfile = MutableStateFlow(MovementProfile.PEDESTRIAN)
    val selectedProfile: StateFlow<MovementProfile> = _selectedProfile.asStateFlow()

    fun selectProfile(profile: MovementProfile) {
        _selectedProfile.value = profile
    }

    // ── Route ETA (Task 27) ─────────────────────────────────────────

    /**
     * Human-readable ETA string for the currently loaded route + selected profile.
     * Null when no route is available. Recomputes automatically on route/profile change.
     * Example values: "5m 30s", "1h 12m".
     */
    val routeEtaText: StateFlow<String?> =
        combine(repository.route, _selectedProfile) { route, profile ->
            route?.let { formatDuration(estimateRuntimeDurationSec(it, profile)) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ── Operating mode ────────────────────────────────────────────────────────

    private val _selectedMode = MutableStateFlow(AppMode.CLASSIC)
    val selectedMode: StateFlow<AppMode> = _selectedMode.asStateFlow()

    fun setAppMode(mode: AppMode) {
        _selectedMode.value = mode
        repository.setManualMode(mode == AppMode.JOYSTICK)

        // Clear any pre-loaded GPX route when switching away from GPX mode
        if (mode != AppMode.GPX) {
            _gpxLoadState.value = GpxLoadState.Idle
            // Only clear the repository route if there is no running simulation
            if (repository.state.value is SimulationState.Idle) {
                repository.emitRoute(null)
            }
        }
    }

    private val _repeatPolicy = MutableStateFlow(RepeatPolicy.NONE)
    val repeatPolicy: StateFlow<RepeatPolicy> = _repeatPolicy.asStateFlow()

    private val _repeatCount = MutableStateFlow(2)
    val repeatCount: StateFlow<Int> = _repeatCount.asStateFlow()

    fun setRepeatPolicy(policy: RepeatPolicy) {
        _repeatPolicy.value = policy
    }

    fun setRepeatCount(value: Int) {
        _repeatCount.value = value.coerceAtLeast(1)
    }

    // ── GPX route loading (Task 23) ───────────────────────────────────────────

    /** Represents the lifecycle of a GPX file import triggered by the user's file picker. */
    sealed class GpxLoadState {
        data object Idle : GpxLoadState()
        data object Loading : GpxLoadState()
        data class Success(val route: Route) : GpxLoadState()
        data class Error(val message: String) : GpxLoadState()
    }

    private val _gpxLoadState = MutableStateFlow<GpxLoadState>(GpxLoadState.Idle)
    val gpxLoadState: StateFlow<GpxLoadState> = _gpxLoadState.asStateFlow()

    /**
     * Reads a `.gpx` file from [uri], parses it with [GpxParser], and pre-loads the resulting
     * [Route] into [SimulationRepository].
     *
     * [SimulationService] will detect the pre-loaded route and skip the OSRM call when
     * [AppMode.GPX] is active.
     */
    fun loadGpxFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _gpxLoadState.value = GpxLoadState.Loading
            val result = withContext(Dispatchers.IO) {
                try {
                    val stream = context.contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("Cannot open stream for URI: $uri")
                    gpxParser.parse(stream)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }

            result.fold(
                    onSuccess = { route ->
                        repository.emitRoute(route)
                        _gpxLoadState.value = GpxLoadState.Success(route)
                    },
                    onFailure = { error ->
                        _gpxLoadState.value =
                                GpxLoadState.Error(error.message ?: "Failed to parse GPX file.")
                    },
            )
        }
    }

    /** Resets the GPX load state so the user can pick a different file. */
    fun clearGpxRoute() {
        _gpxLoadState.value = GpxLoadState.Idle
        if (repository.state.value is SimulationState.Idle) {
            repository.emitRoute(null)
        }
    }

    // ── Simulation state ──────────────────────────────────────────────────────

    val simulationState: StateFlow<SimulationState> = repository.state

    val isBusy: StateFlow<Boolean> =
            repository
                    .state
                    .map { it is SimulationState.Running || it is SimulationState.FetchingRoute }
                    .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val route: StateFlow<Route?> = repository.route
    val lastUsedConfig: StateFlow<SimulationConfig?> = repository.lastUsedConfig
    val favoriteSimulations: StateFlow<List<FavoriteSimulationEntity>> =
        repository.favoriteSimulations.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val _uiEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val uiEvents = _uiEvents.asSharedFlow()

    // ── Map pin coordinates ───────────────────────────────────────────────────

    private val _startLat = MutableStateFlow(DefaultCoordinates.START_LAT)
    val startLat: StateFlow<Double> = _startLat.asStateFlow()

    private val _startLng = MutableStateFlow(DefaultCoordinates.START_LNG)
    val startLng: StateFlow<Double> = _startLng.asStateFlow()

    private val _endLat = MutableStateFlow(DefaultCoordinates.END_LAT)
    val endLat: StateFlow<Double> = _endLat.asStateFlow()

    private val _endLng = MutableStateFlow(DefaultCoordinates.END_LNG)
    val endLng: StateFlow<Double> = _endLng.asStateFlow()

    fun setStartLat(lat: Double) {
        _startLat.value = lat
    }
    fun setStartLng(lng: Double) {
        _startLng.value = lng
    }

    fun setEndLat(lat: Double) {
        _endLat.value = lat
    }

    fun setEndLng(lng: Double) {
        _endLng.value = lng
    }

    fun buildCurrentConfig(
        profile: MovementProfile = selectedProfile.value,
        waypointPauseSec: Double = 0.0,
        speedRatio: Double = 1.0,
        frequencyHz: Int = SimulationService.DEFAULT_FREQUENCY,
    ): SimulationConfig {
        val activeRoute = route.value
        val routeId = activeRoute?.id ?: lastUsedConfig.value?.routeId
        val routeWaypoints = when {
            selectedMode.value == AppMode.WAYPOINTS -> waypoints.value
            activeRoute != null -> activeRoute.waypoints
            else -> emptyList()
        }

        return SimulationConfig(
            profileName = profile.name,
            startLat = startLat.value,
            startLng = startLng.value,
            endLat = endLat.value,
            endLng = endLng.value,
            routeId = routeId,
            appMode = selectedMode.value,
            waypoints = routeWaypoints,
            waypointPauseSec = waypointPauseSec,
            speedRatio = speedRatio,
            frequencyHz = frequencyHz,
            repeatPolicy = repeatPolicy.value,
            repeatCount = repeatCount.value,
        )
    }

    private fun applyConfig(config: SimulationConfig) {
        val profile = MovementProfile.BUILT_IN[config.profileName]
            ?: profiles.value.firstOrNull { it.name == config.profileName }
        if (profile != null) selectProfile(profile)
        _startLat.value = config.startLat
        _startLng.value = config.startLng
        _endLat.value = config.endLat
        _endLng.value = config.endLng
        _waypoints.value = config.waypoints
        repository.emitConfig(config)
        setAppMode(config.appMode)
        previewConfigRoute(config)
    }

    fun applyRouteForSimulation(route: Route) {
        val start = route.waypoints.firstOrNull() ?: return
        val end = route.waypoints.lastOrNull() ?: return
        selectProfile(selectedProfile.value)
        _startLat.value = start.lat
        _startLng.value = start.lng
        _endLat.value = end.lat
        _endLng.value = end.lng
        _waypoints.value = route.waypoints
        repository.emitRoute(route)
        repository.emitConfig(
            buildCurrentConfig().copy(
                routeId = route.id,
                appMode = AppMode.GPX,
                waypoints = route.waypoints,
            )
        )
        _gpxLoadState.value = GpxLoadState.Success(route)
        setAppMode(AppMode.GPX)
    }

    private fun previewConfigRoute(config: SimulationConfig) {
        viewModelScope.launch {
            val previewRoute = when {
                config.routeId != null -> routeRepository.getById(config.routeId)
                config.waypoints.size >= 2 -> Route(
                    id = config.routeId ?: "config-route",
                    name = "Saved Route",
                    waypoints = config.waypoints,
                )
                else -> null
            }

            if (repository.state.value is SimulationState.Idle) {
                repository.emitRoute(previewRoute)
            }
            if (config.appMode == AppMode.GPX) {
                _gpxLoadState.value = previewRoute?.let { GpxLoadState.Success(it) }
                    ?: GpxLoadState.Idle
            }
        }
    }

    /**
     * Applies a previously executed simulation configuration to speed up replay.
     */
    fun applyReplayConfig(history: SimulationHistoryEntity) {
        applyConfig(history.toSimulationConfig())
    }

    fun saveCurrentAsFavorite(name: String? = null) {
        viewModelScope.launch {
            val trimmedName = name?.trim().orEmpty()
            val favoriteName = if (trimmedName.isNotBlank()) trimmedName else "Favorite ${System.currentTimeMillis()}"
            repository.saveFavorite(
                name = favoriteName,
                config = buildCurrentConfig(),
            )
            _uiEvents.emit("Favorito salvo: $favoriteName")
        }
    }

    fun applyFavoriteById(id: String) {
        viewModelScope.launch {
            val favorite = favoriteSimulations.value.firstOrNull { it.id == id }
            if (favorite == null) {
                _uiEvents.emit("Favorito não encontrado.")
                return@launch
            }
            when (val resolved = repository.resolveFavoriteConfig(favorite, repository.lastUsedConfig.value)) {
                is SimulationRepository.FavoriteResolution.Valid -> {
                    applyConfig(resolved.config)
                    _uiEvents.emit("Favorito aplicado: ${resolved.favorite.name}")
                }
                is SimulationRepository.FavoriteResolution.Invalid -> {
                    resolved.fallbackConfig?.let { applyConfig(it) }
                    _uiEvents.emit("Favorito inválido. ${resolved.reason}")
                }
            }
        }
    }

    private val _startPlaced = MutableStateFlow(false)
    val startPlaced: StateFlow<Boolean> = _startPlaced.asStateFlow()

    // ── Waypoints (Multi-Stop) ────────────────────────────────────────────────

    private val _waypoints = MutableStateFlow<List<Waypoint>>(emptyList())
    val waypoints: StateFlow<List<Waypoint>> = _waypoints.asStateFlow()

    fun addWaypoint(waypoint: Waypoint) {
        _waypoints.value = _waypoints.value + waypoint
        clearRouteIfIdle()
    }


    fun clearWaypoints() {
        _waypoints.value = emptyList()
        clearRouteIfIdle()
    }

    fun removeWaypoint(index: Int) {
        val current = _waypoints.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _waypoints.value = current
            clearRouteIfIdle()
        }
    }

    private val classicModeReady: StateFlow<Boolean> =
        combine(_startLat, _startLng, _endLat, _endLng) { startLat, startLng, endLat, endLng ->
            GeoMath.haversineMeters(startLat, startLng, endLat, endLng) > 5.0
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val gpxModeReady: StateFlow<Boolean> =
        combine(repository.route, _gpxLoadState) { route, gpxState ->
            (route?.waypoints?.size ?: 0) >= 2 || gpxState is GpxLoadState.Success
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val canStartCurrentMode: StateFlow<Boolean> =
        combine(_selectedMode, classicModeReady, _waypoints, gpxModeReady) { mode, classicReady, waypoints, gpxReady ->
            when (mode) {
                AppMode.CLASSIC -> classicReady
                AppMode.JOYSTICK -> true
                AppMode.WAYPOINTS -> waypoints.size >= 2
                AppMode.GPX -> gpxReady
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private fun clearRouteIfIdle() {
        if (repository.state.value is SimulationState.Idle) {
            repository.emitRoute(null)
        }
    }

    // ── Map interaction ───────────────────────────────────────────────────────

    fun onMapLongPress(lat: Double, lng: Double) {
        if (_selectedMode.value == AppMode.WAYPOINTS) {
            addWaypoint(Waypoint(lat, lng))
            return
        }

        when {
            !_startPlaced.value -> {
                _startLat.value = lat
                _startLng.value = lng
                _startPlaced.value = true
            }
            else -> {
                _endLat.value = lat
                _endLng.value = lng
                _startPlaced.value = false
            }
        }
        clearRouteIfIdle()
    }
}
