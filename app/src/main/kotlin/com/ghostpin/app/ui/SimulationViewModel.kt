package com.ghostpin.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostpin.app.service.SimulationService
import com.ghostpin.app.service.SimulationState
import com.ghostpin.core.model.MovementProfile
import com.ghostpin.core.model.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel managing simulation UI state.
 *
 * Sprint 2 changes:
 *  - Observes [SimulationService.sharedState] in init (fixes Bug #1 — state never reached UI)
 *  - Observes [SimulationService.sharedRoute] so the map can draw the full OSRM polyline
 *  - [onMapLongPress] replaces the hardcoded +0.005 with a proper start → end toggle (Bug #6)
 *  - [startPlaced] tracks whether the user has placed the start pin
 *  - Removed the now-redundant [updateSimulationState] method that conflicted with the observer
 */
@HiltViewModel
class SimulationViewModel @Inject constructor() : ViewModel() {

    // ── Profiles ─────────────────────────────────────────────────────────────

    val profiles: List<MovementProfile> = listOf(
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

    // ── Simulation state (mirrored from service) ──────────────────────────────

    private val _simulationState = MutableStateFlow<SimulationState>(SimulationState.Idle)
    val simulationState: StateFlow<SimulationState> = _simulationState.asStateFlow()

    // ── Route (set by service after OSRM fetch) ───────────────────────────────

    private val _route = MutableStateFlow<Route?>(null)
    /** The fetched OSRM route. Null when idle or not yet fetched. */
    val route: StateFlow<Route?> = _route.asStateFlow()

    // ── Map pin coordinates ───────────────────────────────────────────────────

    private val _startLat = MutableStateFlow(-23.5505)
    val startLat: StateFlow<Double> = _startLat.asStateFlow()

    private val _startLng = MutableStateFlow(-46.6333)
    val startLng: StateFlow<Double> = _startLng.asStateFlow()

    private val _endLat = MutableStateFlow(-23.5510)
    val endLat: StateFlow<Double> = _endLat.asStateFlow()

    private val _endLng = MutableStateFlow(-46.6340)
    val endLng: StateFlow<Double> = _endLng.asStateFlow()

    /**
     * True when the start pin has been placed and the next long-press should place the end pin.
     * Drives the hint text at the bottom of the map.
     */
    private val _startPlaced = MutableStateFlow(false)
    val startPlaced: StateFlow<Boolean> = _startPlaced.asStateFlow()

    // ── Service observers ─────────────────────────────────────────────────────

    init {
        viewModelScope.launch {
            SimulationService.sharedState.collect { _simulationState.value = it }
        }
        viewModelScope.launch {
            SimulationService.sharedRoute.collect { _route.value = it }
        }
    }

    // ── Map interaction ───────────────────────────────────────────────────────

    /**
     * Handle a long-press on the map.
     *
     * Toggle logic:
     *   1st press → set Start pin; mark start as placed
     *   2nd press → set End pin; ready for next round
     *   3rd press → reset Start; cycle restarts
     *
     * Placing new pins clears any previously fetched OSRM route so the map
     * refreshes with a straight preview line until the simulation is started again.
     */
    fun onMapLongPress(lat: Double, lng: Double) {
        if (!_startPlaced.value) {
            _startLat.value   = lat
            _startLng.value   = lng
            // Reset end to same position — prevents a stale route line being drawn
            _endLat.value     = lat
            _endLng.value     = lng
            _startPlaced.value = true
            _route.value      = null
        } else {
            _endLat.value      = lat
            _endLng.value      = lng
            _startPlaced.value = false
            _route.value       = null
        }
    }

    /** Programmatic coordinate setters (kept for backward-compat with MainActivity). */
    fun setStartCoords(lat: Double, lng: Double) {
        _startLat.value = lat
        _startLng.value = lng
    }

    fun setEndCoords(lat: Double, lng: Double) {
        _endLat.value = lat
        _endLng.value = lng
    }
}
