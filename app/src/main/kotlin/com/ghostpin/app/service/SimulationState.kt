package com.ghostpin.app.service

import com.ghostpin.core.model.MockLocation

/**
 * Represents the current state of the simulation service.
 */
sealed class SimulationState {
    /** Service is idle — no simulation running. */
    data object Idle : SimulationState()

    /** Simulation is actively running. */
    data class Running(
        val currentLocation: MockLocation,
        val profileName: String,
        val progressPercent: Float,
        val elapsedTimeSec: Long,
        val frameCount: Long,
    ) : SimulationState()

    /** Simulation is paused. */
    data class Paused(
        val lastLocation: MockLocation,
        val profileName: String,
        val progressPercent: Float,
        val elapsedTimeSec: Long,
    ) : SimulationState()

    /** Simulation encountered an error. */
    data class Error(val message: String) : SimulationState()
}
