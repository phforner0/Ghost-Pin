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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
        private val simulationConfigStore: SimulationConfigStore = NoOpSimulationConfigStore,
        private val pausedSimulationStore: PausedSimulationStore = NoOpPausedSimulationStore,
    ) {
        private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val hydrationMutex = Mutex()

        @Volatile private var hydrated = false

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
        private val _pausedSnapshot = MutableStateFlow<PausedSimulationSnapshot?>(null)
        val pausedSnapshot: StateFlow<PausedSimulationSnapshot?> = _pausedSnapshot.asStateFlow()

        init {
            repositoryScope.launch {
                hydratePersistedStateIfNeeded()
            }
        }

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
            repositoryScope.launch {
                simulationConfigStore.writeLastUsedConfig(sanitizeConfigForPersistence(config))
            }
        }

        fun emitOptionalConfig(config: SimulationConfig?) {
            _lastUsedConfig.value = config
            repositoryScope.launch {
                simulationConfigStore.writeLastUsedConfig(config?.let { sanitizeConfigForPersistence(it) })
            }
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
            _pausedSnapshot.value = null
            repositoryScope.launch { pausedSimulationStore.writeSnapshot(null) }
        }

        suspend fun hydratePersistedStateIfNeeded() {
            if (hydrated) return

            hydrationMutex.withLock {
                if (hydrated) return

                val persistedConfig = simulationConfigStore.readLastUsedConfig()?.let { sanitizeConfigForPersistence(it) }
                if (persistedConfig != null) {
                    _lastUsedConfig.value = persistedConfig
                    if (_route.value == null && persistedConfig.waypoints.size >= 2) {
                        _route.value =
                            Route(
                                id = persistedConfig.routeId ?: "persisted-config-route",
                                name = "Persisted Route",
                                waypoints = persistedConfig.waypoints,
                            )
                    }
                }

                val pausedSnapshot = pausedSimulationStore.readSnapshot()
                _pausedSnapshot.value = pausedSnapshot
                if (pausedSnapshot != null && _state.value is SimulationState.Idle) {
                    _state.value = pausedSnapshot.pausedState
                    _isManualMode.value = pausedSnapshot.config.appMode == AppMode.JOYSTICK
                    if (_lastUsedConfig.value == null) {
                        _lastUsedConfig.value = pausedSnapshot.config
                    }
                    if (_route.value == null && pausedSnapshot.config.waypoints.size >= 2) {
                        _route.value =
                            Route(
                                id = pausedSnapshot.config.routeId ?: "paused-snapshot-route",
                                name = "Paused Session Route",
                                waypoints = pausedSnapshot.config.waypoints,
                            )
                    }
                }

                closeOrphanRunningHistory(excludeId = pausedSnapshot?.activeHistoryId)
                hydrated = true
            }
        }

        suspend fun getLastUsedConfigOrPersisted(): SimulationConfig? {
            val inMemory = _lastUsedConfig.value
            if (inMemory != null) return inMemory

            val persisted = simulationConfigStore.readLastUsedConfig()?.let { sanitizeConfigForPersistence(it) }
            if (persisted != null) {
                _lastUsedConfig.value = persisted
                if (_route.value == null && persisted.waypoints.size >= 2) {
                    _route.value =
                        Route(
                            id = persisted.routeId ?: "persisted-config-route",
                            name = "Persisted Route",
                            waypoints = persisted.waypoints,
                        )
                }
            }
            return persisted
        }

        suspend fun persistPausedSnapshot(
            pausedState: SimulationState.Paused,
            activeHistoryId: String?,
            activeHistoryStartedAtMs: Long?,
        ) {
            val config = _lastUsedConfig.value ?: return
            val snapshot =
                PausedSimulationSnapshot(
                    config = sanitizeConfigForPersistence(config),
                    pausedState = pausedState,
                    activeHistoryId = activeHistoryId,
                    activeHistoryStartedAtMs = activeHistoryStartedAtMs,
                )
            _pausedSnapshot.value = snapshot
            pausedSimulationStore.writeSnapshot(snapshot)
        }

        suspend fun clearPausedSnapshot() {
            _pausedSnapshot.value = null
            pausedSimulationStore.writeSnapshot(null)
        }

        suspend fun getPausedSnapshotOrPersisted(): PausedSimulationSnapshot? {
            val inMemory = _pausedSnapshot.value
            if (inMemory != null) return inMemory

            val persisted = pausedSimulationStore.readSnapshot()
            if (persisted != null) {
                _pausedSnapshot.value = persisted
            }
            return persisted
        }

        private suspend fun closeOrphanRunningHistory(excludeId: String?) {
            simulationHistoryDao.interruptRunningSessions(
                endedAtMs = System.currentTimeMillis(),
                excludeId = excludeId,
            )
        }

        private suspend fun sanitizeConfigForPersistence(config: SimulationConfig): SimulationConfig {
            val persistedRouteId = config.routeId?.takeIf { routeDao.getById(it) != null }
            return if (persistedRouteId == config.routeId) {
                config
            } else {
                config.copy(routeId = null)
            }
        }

        suspend fun startHistory(config: SimulationConfig): String {
            val persistedConfig = sanitizeConfigForPersistence(config)
            val id = UUID.randomUUID().toString()
            simulationHistoryDao.insert(
                SimulationHistoryEntity(
                    id = id,
                    profileName = persistedConfig.profileName,
                    profileIdOrName = persistedConfig.profileLookupKey,
                    routeId = persistedConfig.routeId,
                    startLat = persistedConfig.startLat,
                    startLng = persistedConfig.startLng,
                    endLat = persistedConfig.endLat,
                    endLng = persistedConfig.endLng,
                    appMode = persistedConfig.appMode.name,
                    waypointsJson = persistedConfig.serializedWaypoints(),
                    waypointPauseSec = persistedConfig.waypointPauseSec,
                    speedRatio = persistedConfig.speedRatio,
                    frequencyHz = persistedConfig.frequencyHz,
                    repeatPolicy = persistedConfig.repeatPolicy.name,
                    repeatCount = persistedConfig.repeatCount,
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
            val persistedConfig = sanitizeConfigForPersistence(config)
            val now = System.currentTimeMillis()
            val id = UUID.randomUUID().toString()
            favoriteSimulationDao.upsert(
                FavoriteSimulationEntity(
                    id = id,
                    name = name,
                    profileName = persistedConfig.profileName,
                    profileIdOrName = persistedConfig.profileLookupKey,
                    routeId = persistedConfig.routeId,
                    startLat = persistedConfig.startLat,
                    startLng = persistedConfig.startLng,
                    endLat = persistedConfig.endLat,
                    endLng = persistedConfig.endLng,
                    appMode = persistedConfig.appMode.name,
                    waypointsJson = persistedConfig.serializedWaypoints(),
                    waypointPauseSec = persistedConfig.waypointPauseSec,
                    speedRatio = persistedConfig.speedRatio,
                    frequencyHz = persistedConfig.frequencyHz,
                    repeatPolicy = persistedConfig.repeatPolicy.name,
                    repeatCount = persistedConfig.repeatCount,
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
