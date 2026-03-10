package com.ghostpin.app.ui

import androidx.lifecycle.ViewModel
import com.ghostpin.app.service.SimulationState
import com.ghostpin.core.model.MovementProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * ViewModel managing simulation UI state.
 * Handles profile selection, coordinate input, and service state observation.
 */
@HiltViewModel
class SimulationViewModel @Inject constructor() : ViewModel() {

    private val _selectedProfile = MutableStateFlow(MovementProfile.PEDESTRIAN)
    val selectedProfile: StateFlow<MovementProfile> = _selectedProfile.asStateFlow()

    private val _simulationState = MutableStateFlow<SimulationState>(SimulationState.Idle)
    val simulationState: StateFlow<SimulationState> = _simulationState.asStateFlow()

    private val _startLat = MutableStateFlow(-23.5505)
    val startLat: StateFlow<Double> = _startLat.asStateFlow()

    private val _startLng = MutableStateFlow(-46.6333)
    val startLng: StateFlow<Double> = _startLng.asStateFlow()

    private val _endLat = MutableStateFlow(-23.5510)
    val endLat: StateFlow<Double> = _endLat.asStateFlow()

    private val _endLng = MutableStateFlow(-46.6340)
    val endLng: StateFlow<Double> = _endLng.asStateFlow()

    val profiles: List<MovementProfile> = listOf(
        MovementProfile.PEDESTRIAN,
        MovementProfile.BICYCLE,
        MovementProfile.CAR,
        MovementProfile.URBAN_VEHICLE,
        MovementProfile.DRONE,
    )

    fun selectProfile(profile: MovementProfile) {
        _selectedProfile.value = profile
    }

    fun updateSimulationState(state: SimulationState) {
        _simulationState.value = state
    }

    fun setStartCoords(lat: Double, lng: Double) {
        _startLat.value = lat
        _startLng.value = lng
    }

    fun setEndCoords(lat: Double, lng: Double) {
        _endLat.value = lat
        _endLng.value = lng
    }
}
