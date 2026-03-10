package com.ghostpin.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostpin.app.data.SimulationRepository
import com.ghostpin.app.service.SimulationState
import com.ghostpin.core.model.DefaultCoordinates
import com.ghostpin.core.model.MovementProfile
import com.ghostpin.core.model.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel managing simulation UI state.
 *
 * Changes from Sprint 2:
 *  - Fix (🟠): No longer directly coupled to [SimulationService] companion object.
 *    State flows through [SimulationRepository] — an injectable singleton that
 *    decouples the ViewModel from the Service implementation.
 *  - Fix (🟡): [isBusy] is now a [StateFlow] computed here rather than inline
 *    in the Composable. Business logic belongs in the ViewModel, not the UI layer.
 *  - Coordinates initialised from [DefaultCoordinates] instead of inline magic numbers.
 *  - The redundant init block observing the service companion has been removed.
 */
@HiltViewModel
class SimulationViewModel @Inject constructor(
    private val repository: SimulationRepository,
) : ViewModel() {

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

    // ── Simulation state (from repository) ────────────────────────────────────

    /**
     * The current simulation state — delegates directly to [SimulationRepository.state].
     * The Service writes to the repository; the UI reads from here.
     */
    val simulationState: StateFlow<SimulationState> = repository.state

    /**
     * Fix (🟡): isBusy is computed in the ViewModel, not in the Composable.
     *
     * The Composable should only decide *how* to display "busy" state,
     * not *what* constitutes being busy. Using [SharingStarted.Eagerly] ensures
     * the derived flow is immediately ready when the first subscriber collects it.
     */
    val isBusy: StateFlow<Boolean> = repository.state
        .map { it is SimulationState.Running || it is SimulationState.FetchingRoute }
        .stateIn(
            scope          = viewModelScope,
            started        = SharingStarted.Eagerly,
            initialValue   = false,
        )

    // ── Route (from repository) ───────────────────────────────────────────────

    /** The fetched OSRM route. Null when idle or not yet fetched. */
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

    /**
     * True when the start pin has been placed and the next long-press should
     * place the end pin. Drives the hint text at the bottom of the map.
     */
    private val _startPlaced = MutableStateFlow(false)
    val startPlaced: StateFlow<Boolean> = _startPlaced.asStateFlow()

    // ── Map interaction ───────────────────────────────────────────────────────

    /**
     * Handle a long-press on the map.
     *
     * Toggle logic:
     *   1st press → set Start pin; mark start as placed
     *   2nd press → set End pin; ready for next round
     *   3rd press → reset Start; cycle restarts
     *
     * Placing new pins clears any previously fetched route so the map
     * refreshes with a straight preview line until the simulation starts again.
     */
    fun onMapLongPress(lat: Double, lng: Double) {
        if (!_startPlaced.value) {
            _startLat.value    = lat
            _startLng.value    = lng
            // Reset end to same position — prevents stale route line being drawn
            _endLat.value      = lat
            _endLng.value      = lng
            _startPlaced.value = true
            repository.emitRoute(null)
        } else {
            _endLat.value      = lat
            _endLng.value      = lng
            _startPlaced.value = false
            repository.emitRoute(null)
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
