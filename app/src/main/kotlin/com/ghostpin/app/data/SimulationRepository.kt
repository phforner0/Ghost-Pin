package com.ghostpin.app.data

import com.ghostpin.app.data.db.FavoriteSimulationDao
import com.ghostpin.app.data.db.FavoriteSimulationEntity
import com.ghostpin.app.data.db.ProfileDao
import com.ghostpin.app.data.db.RouteDao
import com.ghostpin.app.data.db.SimulationHistoryDao
import com.ghostpin.app.data.db.SimulationHistoryEntity
import com.ghostpin.app.service.SimulationState
import com.ghostpin.core.model.AppMode
import com.ghostpin.core.model.JoystickState
import com.ghostpin.core.model.Route
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

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
class SimulationRepository
    @Inject
    constructor(
        private val simulationHistoryDao: SimulationHistoryDao,
        private val favoriteSimulationDao: FavoriteSimulationDao,
        private val routeDao: RouteDao,
        private val profileDao: ProfileDao,
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
        val favoriteSimulations: Flow<List<FavoriteSimulationEntity>> = favoriteSimulationDao.observeAll()

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
            _isManualMode.value = false
            _joystickState.value = JoystickState(0f, 0f)
        }

        suspend fun startHistory(config: SimulationConfig): String {
            val id = UUID.randomUUID().toString()
            simulationHistoryDao.insert(
                SimulationHistoryEntity(
                    id = id,
                    profileName = config.profileName,
                    profileIdOrName = config.profileLookupKey,
                    routeId = config.routeId,
                    startLat = config.startLat,
                    startLng = config.startLng,
                    endLat = config.endLat,
                    endLng = config.endLng,
                    appMode = config.appMode.name,
                    waypointsJson = config.serializedWaypoints(),
                    waypointPauseSec = config.waypointPauseSec,
                    speedRatio = config.speedRatio,
                    frequencyHz = config.frequencyHz,
                    repeatPolicy = config.repeatPolicy.name,
                    repeatCount = config.repeatCount,
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

        suspend fun listHistoryPaged(
            page: Int,
            pageSize: Int
        ): List<SimulationHistoryEntity> = simulationHistoryDao.listPaged(limit = pageSize, offset = page * pageSize)

        suspend fun getHistoryById(id: String): SimulationHistoryEntity? = simulationHistoryDao.getById(id)

        suspend fun deleteHistoryById(id: String) = simulationHistoryDao.deleteById(id)

        suspend fun clearHistory() = simulationHistoryDao.clearHistory()

        suspend fun saveFavorite(
            name: String,
            config: SimulationConfig,
        ): String {
            val now = System.currentTimeMillis()
            val id = UUID.randomUUID().toString()
            favoriteSimulationDao.upsert(
                FavoriteSimulationEntity(
                    id = id,
                    name = name,
                    profileName = config.profileName,
                    profileIdOrName = config.profileLookupKey,
                    routeId = config.routeId,
                    startLat = config.startLat,
                    startLng = config.startLng,
                    endLat = config.endLat,
                    endLng = config.endLng,
                    appMode = config.appMode.name,
                    waypointsJson = config.serializedWaypoints(),
                    waypointPauseSec = config.waypointPauseSec,
                    speedRatio = config.speedRatio,
                    frequencyHz = config.frequencyHz,
                    repeatPolicy = config.repeatPolicy.name,
                    repeatCount = config.repeatCount,
                    createdAtMs = now,
                    updatedAtMs = now,
                )
            )
            return id
        }

        suspend fun listRecentFavorites(): List<FavoriteSimulationEntity> = favoriteSimulationDao.listRecent()

        suspend fun resolveFavoriteConfig(
            favorite: FavoriteSimulationEntity,
            fallback: SimulationConfig? = _lastUsedConfig.value,
        ): FavoriteResolution =
            when (val validation = validateConfig(favorite.toSimulationConfig(), fallback)) {
                is ConfigValidation.Valid -> FavoriteResolution.Valid(validation.config, favorite)
                is ConfigValidation.Invalid ->
                    FavoriteResolution.Invalid(
                        reason = "${validation.reason} for favorite '${favorite.name}'.",
                        fallbackConfig = validation.fallbackConfig,
                    )
            }

        suspend fun applyMostRecentFavorite(fallback: SimulationConfig? = _lastUsedConfig.value,): FavoriteResolution {
            val favorite =
                favoriteSimulationDao.getMostRecent()
                    ?: return FavoriteResolution.Invalid(
                        reason = "No favorites saved yet.",
                        fallbackConfig = fallback,
                    )
            return resolveFavoriteConfig(favorite, fallback)
        }

        suspend fun validateConfig(
            config: SimulationConfig,
            fallback: SimulationConfig? = _lastUsedConfig.value,
        ): ConfigValidation {
            val persistedRoute = config.routeId?.let { routeDao.getById(it) }
            val hasRoute = config.routeId == null || persistedRoute != null
            val hasProfile =
                com.ghostpin.core.model.MovementProfile.BUILT_IN
                    .containsKey(config.profileLookupKey) ||
                    profileDao.getById(config.profileLookupKey) != null ||
                    profileDao.getByName(config.profileLookupKey) != null ||
                    profileDao.getByName(config.profileName) != null
            val hasWaypointPlan =
                when (config.appMode) {
                    AppMode.WAYPOINTS -> config.waypoints.size >= 2 || persistedRoute != null
                    AppMode.GPX -> config.waypoints.size >= 2 || persistedRoute != null || route.value != null
                    else -> true
                }

            if (!hasRoute || !hasProfile || !hasWaypointPlan) {
                val reason =
                    buildString {
                        if (!hasRoute) append("Route not found")
                        if (!hasRoute && (!hasProfile || !hasWaypointPlan)) append("; ")
                        if (!hasProfile) append("Profile not found")
                        if (!hasProfile && !hasWaypointPlan) append("; ")
                        if (!hasWaypointPlan) append("Mode configuration incomplete")
                    }
                return ConfigValidation.Invalid(reason = reason, fallbackConfig = fallback)
            }

            val hydratedWaypoints =
                when {
                    config.waypoints.isNotEmpty() -> config.waypoints
                    persistedRoute != null ->
                        RouteRepositoryHydration.deserializeWaypoints(
                            persistedRoute.waypointsJson
                        )
                    else -> emptyList()
                }
            val hydratedConfig =
                if (persistedRoute != null && hydratedWaypoints.size >= 2) {
                    config.copy(
                        startLat = hydratedWaypoints.first().lat,
                        startLng = hydratedWaypoints.first().lng,
                        endLat = hydratedWaypoints.last().lat,
                        endLng = hydratedWaypoints.last().lng,
                        waypoints = hydratedWaypoints,
                    )
                } else {
                    config
                }
            return ConfigValidation.Valid(hydratedConfig)
        }

        sealed class ConfigValidation {
            data class Valid(
                val config: SimulationConfig
            ) : ConfigValidation()

            data class Invalid(
                val reason: String,
                val fallbackConfig: SimulationConfig?,
            ) : ConfigValidation()
        }

        sealed class FavoriteResolution {
            data class Valid(
                val config: SimulationConfig,
                val favorite: FavoriteSimulationEntity,
            ) : FavoriteResolution()

            data class Invalid(
                val reason: String,
                val fallbackConfig: SimulationConfig?,
            ) : FavoriteResolution()
        }

        private object RouteRepositoryHydration {
            fun deserializeWaypoints(serialized: String): List<com.ghostpin.core.model.Waypoint> =
                SimulationConfig.deserializeWaypoints(serialized)
        }
    }
