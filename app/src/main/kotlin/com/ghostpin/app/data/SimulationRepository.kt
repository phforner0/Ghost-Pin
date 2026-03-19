package com.ghostpin.app.data

import com.ghostpin.app.data.db.SimulationHistoryDao
import com.ghostpin.app.data.db.SimulationHistoryEntity
import com.ghostpin.app.service.SimulationState
import com.ghostpin.core.model.Route
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import com.ghostpin.core.model.JoystickState
import java.util.UUID

/**
 * Central source of truth for simulation state, shared between
 * [com.ghostpin.app.service.SimulationService] and
 * [com.ghostpin.app.ui.SimulationViewModel] via Hilt injection.
 *
 * Replaces the anti-pattern of companion object MutableStateFlow in the Service,
 * which prevented proper lifecycle management and unit testing.
 *
 * Both the Service and the ViewModel receive the same singleton instance,
 * so state flows naturally without direct class coupling:
 *
 *   SimulationService ──emitState()──▶ SimulationRepository ──state──▶ SimulationViewModel
 */
@Singleton
class SimulationRepository @Inject constructor(
    private val simulationHistoryDao: SimulationHistoryDao,
) {

    private val _state = MutableStateFlow<SimulationState>(SimulationState.Idle)

    /** The current simulation state. Observed by [com.ghostpin.app.ui.SimulationViewModel]. */
    val state: StateFlow<SimulationState> = _state.asStateFlow()

    private val _route = MutableStateFlow<Route?>(null)

    /**
     * The fetched (OSRM or fallback) route. Exposed so the map can draw the full
     * street-snapped polyline even before/after the simulation loop runs.
     */
    val route: StateFlow<Route?> = _route.asStateFlow()

    private val _lastUsedConfig = MutableStateFlow<SimulationConfig?>(null)

    /**
     * Last-used simulation config. Persisted in memory for quick-start
     * surfaces (QS Tile, Widget, AutomationReceiver).
     */
    val lastUsedConfig: StateFlow<SimulationConfig?> = _lastUsedConfig.asStateFlow()

    // ── Sprint 5: Joystick & Overlay State ───────────────────────────────────

    private val _isManualMode = MutableStateFlow(false)
    val isManualMode: StateFlow<Boolean> = _isManualMode.asStateFlow()

    private val _joystickState = MutableStateFlow(JoystickState(0f, 0f))
    val joystickState: StateFlow<JoystickState> = _joystickState.asStateFlow()

    /** Publish a new simulation state to all observers. */
    fun emitState(state: SimulationState) {
        _state.value = state
    }


    /** Publish a new route (or null to clear) to all observers. */
    fun emitRoute(route: Route?) {
        _route.value = route
    }

    /** Save the last-used simulation config for quick-start. */
    fun emitConfig(config: SimulationConfig) {
        _lastUsedConfig.value = config
    }

    fun emitOptionalConfig(config: SimulationConfig?) {
        _lastUsedConfig.value = config
    }

    /** Update joystick overlay state (magnitude, angle) */
    fun updateJoystickState(joystickState: JoystickState) {
        _joystickState.value = joystickState
    }

    /** Enable or disable manual (joystick) navigation mode */
    fun setManualMode(isManual: Boolean) {
        _isManualMode.value = isManual
    }

    /** Reset both state and route to their idle defaults. */
    fun reset() {
        _state.value = SimulationState.Idle
        _route.value = null
    }

    suspend fun startHistory(profileIdOrName: String, routeId: String?): String {
        val id = UUID.randomUUID().toString()
        simulationHistoryDao.insert(
            SimulationHistoryEntity(
                id = id,
                profileIdOrName = profileIdOrName,
                routeId = routeId,
                startedAtMs = System.currentTimeMillis(),
                endedAtMs = null,
                durationMs = null,
                avgSpeedMs = null,
                distanceMeters = 0.0,
                resultStatus = "RUNNING",
            )
        )
        return id
    }

    suspend fun finishHistory(
        id: String,
        durationMs: Long,
        distanceMeters: Double,
        resultStatus: String,
    ) {
        val endedAtMs = System.currentTimeMillis()
        val avgSpeed = if (durationMs > 0) distanceMeters / (durationMs / 1000.0) else 0.0
        simulationHistoryDao.closeById(
            id = id,
            endedAtMs = endedAtMs,
            durationMs = durationMs,
            avgSpeedMs = avgSpeed,
            distanceMeters = distanceMeters,
            resultStatus = resultStatus,
        )
    }

    suspend fun listHistoryPaged(page: Int, pageSize: Int): List<SimulationHistoryEntity> =
        simulationHistoryDao.listPaged(limit = pageSize, offset = page * pageSize)

    suspend fun getHistoryById(id: String): SimulationHistoryEntity? = simulationHistoryDao.getById(id)

    suspend fun deleteHistoryById(id: String) = simulationHistoryDao.deleteById(id)

    suspend fun clearHistory() = simulationHistoryDao.clearHistory()
}
