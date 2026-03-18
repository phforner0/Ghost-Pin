package com.ghostpin.app.ui

import android.content.Context
import android.location.LocationManager
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostpin.app.data.SimulationRepository
import com.ghostpin.app.routing.GeocodingProvider
import com.ghostpin.app.routing.GpxParser
import com.ghostpin.app.service.SimulationState
import com.ghostpin.core.model.AppMode
import com.ghostpin.core.model.DefaultCoordinates
import com.ghostpin.core.model.MovementProfile
import com.ghostpin.core.model.Route
import com.ghostpin.core.model.Waypoint
import com.ghostpin.core.model.estimateDuration
import com.ghostpin.core.model.formatDuration
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    // ── Route ETA (Task 27) ─────────────────────────────────────────

    /**
     * Human-readable ETA string for the currently loaded route + selected profile.
     * Null when no route is available. Recomputes automatically on route/profile change.
     * Example values: "5m 30s", "1h 12m".
     */
    val routeEtaText: StateFlow<String?> =
        combine(repository.currentRoute, _selectedProfile) { route, profile ->
            route?.let { formatDuration(it.estimateDuration(profile)) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ── Real device location (one-shot, for map cold-start centering) ─────────

    private val _deviceLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    /**
     * Emits the real device coordinates once on first resolution. Observed by [InteractiveMap] to
     * call [MapController.moveTo] before the user places any pins. Null until [initializeLocation]
     * succeeds.
     */
    val deviceLocation: StateFlow<Pair<Double, Double>?> = _deviceLocation.asStateFlow()

    fun initializeLocation(context: Context) {
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
                _startLat.value = realLocation.latitude
                _startLng.value = realLocation.longitude
                _endLat.value = realLocation.latitude
                _endLng.value = realLocation.longitude
                // Signal the map to center — only set once (null → value)
                if (_deviceLocation.value == null) {
                    _deviceLocation.value = realLocation.latitude to realLocation.longitude
                }
            }
        } catch (e: SecurityException) {
            // Permission not granted — silently fall back to defaults
        }
    }

    // ── Profile ───────────────────────────────────────────────────────────────

    val profiles: List<MovementProfile> =
            listOf(
                    MovementProfile.PEDESTRIAN,
                    MovementProfile.BICYCLE,
                    MovementProfile.CAR,
                    MovementProfile.URBAN_VEHICLE,
                    MovementProfile.DRONE,
            )

    private val _selectedProfile = MutableStateFlow(MovementProfile.PEDESTRIAN)
    val selectedProfile: StateFlow<MovementProfile> = _selectedProfile.asStateFlow()

    fun selectProfile(profile: MovementProfile) {
        _selectedProfile.value = profile
    }

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
