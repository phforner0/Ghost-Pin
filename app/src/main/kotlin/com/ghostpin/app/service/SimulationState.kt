package com.ghostpin.app.service

import com.ghostpin.core.model.MockLocation

/**
 * Represents the current state of the [SimulationService].
 */
sealed class SimulationState {

    /** Service is idle — no simulation running. */
    data object Idle : SimulationState()

    /**
     * Service is fetching the street route from OSRM before starting.
     * The UI should show a loading indicator and disable the Start button.
     */
    data class FetchingRoute(
        val profileName: String,
    ) : SimulationState()

    /** Simulation is actively running. */
    data class Running(
        val currentLocation: MockLocation,
        val profileName: String,
        val progressPercent: Float,
        val lapProgressPercent: Float = progressPercent,
        val currentLap: Int = 1,
        val totalLapsLabel: String = "1",
        val direction: Int = 1,
        val elapsedTimeSec: Long,
        val frameCount: Long,
    ) : SimulationState()

    /** Simulation is paused. */
    data class Paused(
        val lastLocation: MockLocation,
        val profileName: String,
        val progressPercent: Float,
        val lapProgressPercent: Float = progressPercent,
        val currentLap: Int = 1,
        val totalLapsLabel: String = "1",
        val direction: Int = 1,
        val elapsedTimeSec: Long,
    ) : SimulationState()

    /** Simulation encountered an error. */
    data class Error(val message: String) : SimulationState()
}
