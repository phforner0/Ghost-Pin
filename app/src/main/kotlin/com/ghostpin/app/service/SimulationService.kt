package com.ghostpin.app.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.ghostpin.app.BuildConfig
import com.ghostpin.app.GhostPinApp
import com.ghostpin.app.R
import com.ghostpin.app.data.RouteRepository
import com.ghostpin.app.data.SimulationConfig
import com.ghostpin.app.data.SimulationRepository
import com.ghostpin.app.data.db.ProfileDao
import com.ghostpin.app.location.LocationInjector
import com.ghostpin.app.routing.OsrmRouteProvider
import com.ghostpin.app.routing.RouteFileParser
import com.ghostpin.app.routing.RouteImportValidator
import com.ghostpin.app.ui.MainActivity
import com.ghostpin.app.widget.GhostPinWidget
import com.ghostpin.core.math.GeoMath
import com.ghostpin.core.model.AppMode
import com.ghostpin.core.model.MockLocation
import com.ghostpin.core.model.MovementProfile
import com.ghostpin.core.model.Route
import com.ghostpin.core.model.distanceMeters
import com.ghostpin.core.security.LogSanitizer
import com.ghostpin.engine.interpolation.RepeatPolicy
import com.ghostpin.engine.interpolation.RepeatTraversalController
import com.ghostpin.engine.interpolation.RepeatTraversalState
import com.ghostpin.engine.interpolation.RouteInterpolator
import com.ghostpin.engine.interpolation.SpeedController
import com.ghostpin.engine.noise.LayeredNoiseModel
import com.ghostpin.engine.validation.TrajectoryValidator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import kotlin.math.roundToLong

/**
 * Background service that drives the mock GPS location simulation.
 *
 * Sprint 6 additions:
 *  - [EXTRA_MODE] bundle argument — accepts the current [AppMode].
 *  - GPX mode: skips OSRM entirely and uses the route already loaded
 *    in [SimulationRepository] by the ViewModel's file picker flow.
 *  - JOYSTICK mode: sends [FloatingBubbleService.EXTRA_AUTO_JOYSTICK] = true
 *    so the joystick overlay opens immediately without a manual toggle.
 *
 * Previous fixes carried forward:
 *  - Null-intent guard → START_NOT_STICKY.
 *  - Coordinate validation (NaN / Infinity / out-of-range rejection).
 *  - Single timestamp capture per frame for elapsedRealtimeNanos consistency.
 *  - [estimateAltitude] returns neutral 0.0 instead of city-specific constant.
 */
@AndroidEntryPoint
class SimulationService : LifecycleService() {
    @Inject lateinit var mockLocationInjector: LocationInjector

    @Inject lateinit var osrmRouteProvider: OsrmRouteProvider

    @Inject lateinit var routeRepository: RouteRepository

    @Inject lateinit var repository: SimulationRepository

    @Inject lateinit var profileDao: ProfileDao

    @Inject lateinit var trajectoryValidator: TrajectoryValidator

    @Inject lateinit var routeFileParser: RouteFileParser

    private var simulationJob: Job? = null
    private var pendingWaypointSkip: Int = 0
    private var activeHistoryId: String? = null
    private var activeHistoryStartedAtMs: Long? = null
    private var activeHistoryRouteDistanceMeters: Double = 0.0
    private var isStopping = false

    companion object {
        private const val TAG = "SimulationService"

        const val EXTRA_PROFILE_NAME = "profile_name"
        const val EXTRA_PROFILE_LOOKUP_KEY = "profile_lookup_key"
        const val EXTRA_START_LAT = "start_lat"
        const val EXTRA_START_LNG = "start_lng"
        const val EXTRA_END_LAT = "end_lat"
        const val EXTRA_END_LNG = "end_lng"
        const val EXTRA_FREQUENCY_HZ = "frequency_hz"
        const val EXTRA_SPEED_RATIO = "speed_ratio"
        const val EXTRA_ROUTE_ID = "extra_route_id"

        // Sprint 6
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_WAYPOINTS_LAT = "extra_waypoints_lat"
        const val EXTRA_WAYPOINTS_LNG = "extra_waypoints_lng"
        const val EXTRA_WAYPOINT_PAUSE_SEC = "extra_waypoint_pause_sec"
        const val EXTRA_REPEAT_POLICY = "extra_repeat_policy"
        const val EXTRA_REPEAT_COUNT = "extra_repeat_count"

        const val ACTION_START = "com.ghostpin.ACTION_START"
        const val ACTION_STOP = "com.ghostpin.ACTION_STOP"
        const val ACTION_PAUSE = "com.ghostpin.ACTION_PAUSE"
        const val ACTION_SET_ROUTE = "com.ghostpin.ACTION_SET_ROUTE"
        const val ACTION_SET_PROFILE = "com.ghostpin.ACTION_SET_PROFILE"
        const val ACTION_SKIP_NEXT_WAYPOINT = "com.ghostpin.ACTION_SKIP_NEXT_WAYPOINT"
        const val ACTION_SKIP_PREV_WAYPOINT = "com.ghostpin.ACTION_SKIP_PREV_WAYPOINT"
        const val ACTION_START_LAST_FAVORITE = "com.ghostpin.ACTION_START_LAST_FAVORITE"
        const val ACTION_START_LAST_CONFIG = "com.ghostpin.ACTION_START_LAST_CONFIG"

        const val NOTIFICATION_ID = 1001
        const val DEFAULT_FREQUENCY = 5 // Hz — smooth map animation

        private const val MIN_FREQUENCY = 1 // Hz
        private const val MAX_FREQUENCY = 60 // Hz

        fun createStartIntent(
            context: Context,
            config: SimulationConfig
        ): Intent =
            Intent(context, SimulationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PROFILE_NAME, config.profileName)
                putExtra(EXTRA_PROFILE_LOOKUP_KEY, config.profileLookupKey)
                putExtra(EXTRA_START_LAT, config.startLat)
                putExtra(EXTRA_START_LNG, config.startLng)
                putExtra(EXTRA_END_LAT, config.endLat)
                putExtra(EXTRA_END_LNG, config.endLng)
                putExtra(EXTRA_FREQUENCY_HZ, config.frequencyHz)
                putExtra(EXTRA_SPEED_RATIO, config.speedRatio)
                putExtra(EXTRA_MODE, config.appMode.name)
                putExtra(EXTRA_WAYPOINTS_LAT, config.waypoints.map { it.lat }.toDoubleArray())
                putExtra(EXTRA_WAYPOINTS_LNG, config.waypoints.map { it.lng }.toDoubleArray())
                putExtra(EXTRA_WAYPOINT_PAUSE_SEC, config.waypointPauseSec)
                putExtra(EXTRA_REPEAT_POLICY, config.repeatPolicy.name)
                putExtra(EXTRA_REPEAT_COUNT, config.repeatCount)
                config.routeId?.let { putExtra(EXTRA_ROUTE_ID, it) }
            }
    }

    // ── Service lifecycle ────────────────────────────────────────────────────

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        super.onStartCommand(intent, flags, startId)

        runBlocking {
            repository.hydratePersistedStateIfNeeded()
        }

        when (val action = classifyImmediateServiceAction(intent)) {
            ImmediateServiceAction.NullIntentStop -> {
                Log.w(TAG, LogSanitizer.sanitizeString("Received null intent — stopping without starting simulation."))
                stopSelf()
                return START_NOT_STICKY
            }

            ImmediateServiceAction.StopSimulation -> {
                stopSimulation()
                return START_NOT_STICKY
            }

            ImmediateServiceAction.PauseSimulation -> {
                pauseSimulation()
                return START_NOT_STICKY
            }

            is ImmediateServiceAction.SetProfile -> {
                val resolvedProfile = resolveProfile(action.profileName)
                if (resolvedProfile != null) {
                    repository.emitConfig(
                        buildUpdatedProfileConfig(
                            current = repository.lastUsedConfig.value,
                            resolvedProfile = resolvedProfile,
                            profileLookupKey = action.profileName ?: resolvedProfile.name,
                            defaultFrequency = DEFAULT_FREQUENCY,
                        )
                    )
                } else {
                    emitStateAndRefresh(SimulationState.Error("Invalid profile for ACTION_SET_PROFILE"))
                }
                return START_NOT_STICKY
            }

            is ImmediateServiceAction.SetRoute -> {
                val uri =
                    RouteImportValidator.validateUri(action.uri).getOrElse {
                        emitStateAndRefresh(SimulationState.Error(it.message ?: "Missing route URI for ACTION_SET_ROUTE"))
                        return START_NOT_STICKY
                    }
                RouteImportValidator
                    .persistReadGrantIfNeeded(contentResolver, uri, intent?.flags ?: 0)
                    .onFailure { error ->
                        Log.w(TAG, LogSanitizer.sanitizeString("Could not persist route URI grant: ${error.message}"))
                    }

                lifecycleScope.launch {
                    runCatching {
                        contentResolver.openInputStream(uri)?.use { input ->
                            val displayName = RouteImportValidator.resolveDisplayName(contentResolver, uri)
                            routeFileParser.parse(input, displayName).getOrThrow()
                        } ?: error("Cannot open route URI.")
                    }.onSuccess { route ->
                        repository.emitRoute(route)
                    }.onFailure { e ->
                        emitStateAndRefresh(SimulationState.Error(e.message ?: "Failed to parse route file"))
                    }
                }
                return START_NOT_STICKY
            }

            is ImmediateServiceAction.SkipWaypoint -> {
                pendingWaypointSkip = action.delta
                return START_NOT_STICKY
            }

            ImmediateServiceAction.StartLastFavorite -> {
                lifecycleScope.launch {
                    when (
                        val decision =
                            resolveFavoriteShortcutDecision(
                                repository.applyMostRecentFavorite(repository.getLastUsedConfigOrPersisted())
                            )
                    ) {
                        is ShortcutStartDecision.Start -> {
                            startForegroundService(createStartIntent(this@SimulationService, decision.config))
                        }
                        is ShortcutStartDecision.ErrorAndStop -> {
                            emitStateAndRefresh(SimulationState.Error(decision.message))
                            stopSelf()
                        }
                    }
                }
                return START_NOT_STICKY
            }

            ImmediateServiceAction.StartLastConfig -> {
                lifecycleScope.launch {
                    val currentConfig = repository.getLastUsedConfigOrPersisted()
                    val validation = currentConfig?.let { repository.validateConfig(it, fallback = null) }
                    when (val decision = resolveLastConfigShortcutDecision(currentConfig, validation)) {
                        is ShortcutStartDecision.Start -> {
                            startForegroundService(createStartIntent(this@SimulationService, decision.config))
                        }
                        is ShortcutStartDecision.ErrorAndStop -> {
                            emitStateAndRefresh(SimulationState.Error(decision.message))
                            stopSelf()
                        }
                    }
                }
                return START_NOT_STICKY
            }

            is ImmediateServiceAction.StartSimulation -> {
                // continue below with full start parsing
            }
        }

        if (!BuildConfig.MOCK_PROVIDER_ENABLED) {
            emitStateAndRefresh(
                SimulationState.Error(
                    "Mock provider not available in this build. " +
                        "Enable Developer Options → Mock location app."
                )
            )
            stopSelf()
            return START_NOT_STICKY
        }

        val startRequest =
            parseSimulationStartRequest(
                intent = intent!!,
                repository = repository,
                resolveProfile = ::resolveProfile,
                defaultFrequency = DEFAULT_FREQUENCY,
                minFrequency = MIN_FREQUENCY,
                maxFrequency = MAX_FREQUENCY,
            ).getOrElse { error ->
                emitStateAndRefresh(SimulationState.Error(error.message ?: "Invalid simulation request."))
                stopSelf()
                return START_NOT_STICKY
            }

        // ── Start overlay bubble ──────────────────────────────────────────────
        if (android.provider.Settings.canDrawOverlays(this)) {
            // For JOYSTICK mode pass the auto-open flag so the overlay
            // shows the joystick immediately without a manual toggle.
            val bubbleIntent =
                if (startRequest.appMode == AppMode.JOYSTICK) {
                    FloatingBubbleService.showJoystickIntent(this)
                } else {
                    FloatingBubbleService.showIntent(this)
                }
            startService(bubbleIntent)
        }

        if (startRequest.isResume) {
            val pausedState = repository.state.value as SimulationState.Paused
            val pausedSnapshot = repository.pausedSnapshot.value
            activeHistoryId = pausedSnapshot?.activeHistoryId
            activeHistoryStartedAtMs = pausedSnapshot?.activeHistoryStartedAtMs
            val profile =
                resolveProfile(repository.lastUsedConfig.value?.profileLookupKey ?: pausedState.profileName)
                    ?: MovementProfile.PEDESTRIAN
            startForeground(NOTIFICATION_ID, buildNotification(profile.name))
            startSimulation(
                profile = profile,
                startLat = 0.0,
                startLng = 0.0,
                endLat = 0.0,
                endLng = 0.0,
                frequencyHz = startRequest.frequencyHz,
                speedRatio = startRequest.speedRatio,
                appMode = startRequest.appMode,
                waypointPauseSec = startRequest.waypointPauseSec,
                resumeState = pausedState,
                repeatPolicy = startRequest.repeatPolicy,
                repeatCount = startRequest.repeatCount,
            )
            return START_NOT_STICKY
        }

        repository.emitConfig(
            startRequest.toSimulationConfig(routeId = intent.getStringExtra(EXTRA_ROUTE_ID))
        )
        startForeground(NOTIFICATION_ID, buildNotification(startRequest.profile.name))
        startSimulation(
            profile = startRequest.profile,
            startLat = startRequest.startLat,
            startLng = startRequest.startLng,
            endLat = startRequest.endLat,
            endLng = startRequest.endLng,
            frequencyHz = startRequest.frequencyHz,
            speedRatio = startRequest.speedRatio,
            appMode = startRequest.appMode,
            waypointPauseSec = startRequest.waypointPauseSec,
            resumeState = null,
            waypoints = startRequest.waypoints,
            repeatPolicy = startRequest.repeatPolicy,
            repeatCount = startRequest.repeatCount,
        )

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopService(Intent(this, FloatingBubbleService::class.java))
        if (activeHistoryId != null) {
            simulationJob?.cancel()
            simulationJob = null
            runCatching {
                runBlocking {
                    finishActiveHistory(
                        resultStatus = "INTERRUPTED",
                        distanceMeters = estimateCoveredDistanceMeters(),
                    )
                }
            }.onFailure { error ->
                Log.e(TAG, LogSanitizer.sanitizeString("Failed to finalize history during service destruction"), error)
            }
            runCatching { mockLocationInjector.unregisterProvider() }
            runCatching { runBlocking { repository.clearPausedSnapshot() } }
            repository.reset()
            refreshCompanionSurfaces(SimulationState.Idle)
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        super.onDestroy()
    }

    // ── Simulation loop ──────────────────────────────────────────────────────

    private fun startSimulation(
        profile: MovementProfile,
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double,
        frequencyHz: Int,
        speedRatio: Double = 1.0,
        appMode: AppMode = AppMode.CLASSIC,
        waypointPauseSec: Double = 0.0,
        resumeState: SimulationState.Paused? = null,
        waypoints: List<com.ghostpin.core.model.Waypoint> = emptyList(),
        repeatPolicy: RepeatPolicy = RepeatPolicy.NONE,
        repeatCount: Int = 1,
    ) {
        simulationJob?.cancel()
        isStopping = false
        runBlocking { repository.clearPausedSnapshot() }
        if (resumeState == null && appMode != AppMode.GPX) {
            repository.emitRoute(null)
        }

        val intervalMs = 1000L / frequencyHz
        val deltaTimeSec = 1.0 / frequencyHz

        simulationJob =
            lifecycleScope.launch(Dispatchers.Default) {
                try {
                    if (resumeState == null) {
                        activeHistoryStartedAtMs = System.currentTimeMillis()
                        val launchConfig =
                            repository.lastUsedConfig.value ?: SimulationConfig(
                                profileName = profile.name,
                                startLat = startLat,
                                startLng = startLng,
                                endLat = endLat,
                                endLng = endLng,
                                routeId = null,
                                appMode = appMode,
                                waypoints = waypoints,
                                waypointPauseSec = waypointPauseSec,
                                speedRatio = speedRatio,
                                frequencyHz = frequencyHz,
                                repeatPolicy = repeatPolicy,
                                repeatCount = repeatCount,
                            )
                        activeHistoryId = repository.startHistory(launchConfig)
                    }

                    // ── 1. Obtain route ──────────────────────────────────────────
                    val config = repository.lastUsedConfig.value
                    val routeRequest =
                        SimulationRouteRequest(
                            profile = profile,
                            startLat = startLat,
                            startLng = startLng,
                            endLat = endLat,
                            endLng = endLng,
                            appMode = appMode,
                            waypoints = waypoints,
                            resumeState = resumeState,
                            cachedRoute = repository.route.value,
                            persistedRouteId = config?.routeId,
                            cachedConfigWaypoints = config?.waypoints ?: emptyList(),
                        )

                    val route: Route =
                        when (
                            val resolved =
                                resolveSimulationRoute(
                                    request = routeRequest,
                                    routeRepository = routeRepository,
                                    simulationRepository = repository,
                                    osrmRouteProvider = osrmRouteProvider,
                                    loggerTag = TAG,
                                )
                        ) {
                            is SimulationRouteResult.Success -> resolved.route
                            is SimulationRouteResult.Error -> {
                                emitStateAndRefresh(SimulationState.Error(resolved.message))
                                stopSelf()
                                return@launch
                            }
                            is SimulationRouteResult.Joystick -> {
                                emitStateAndRefresh(
                                    SimulationState.Running(
                                        currentLocation =
                                            MockLocation(
                                                resolved.startLat,
                                                resolved.startLng,
                                                0.0,
                                                0f,
                                                0f
                                            ),
                                        profileName = profile.name,
                                        progressPercent = 0f,
                                        elapsedTimeSec = 0L,
                                        frameCount = 0L,
                                    )
                                )
                                runJoystickLoop(profile, resolved.startLat, resolved.startLng, intervalMs)
                                return@launch
                            }
                        }

                    // ── 2. Validate route ────────────────────────────────────────
                    if (resumeState == null && appMode != AppMode.GPX) {
                        repository.emitRoute(route)
                    }

                    val validation = trajectoryValidator.validate(route, profile)
                    if (!validation.isValid) {
                        val message =
                            validation.warnings
                                .joinToString(separator = "; ")
                                .ifBlank { "Route failed engine validation." }
                        emitStateAndRefresh(SimulationState.Error(message))
                        stopSelf()
                        return@launch
                    }

                    // ── 3. Interpolation loop ────────────────────────────────────
                    mockLocationInjector.registerProvider()
                    val speedController = SpeedController(profile, initialRatio = speedRatio)
                    val noiseModel = LayeredNoiseModel.fromProfile(profile)
                    val interpolator = RouteInterpolator(route)
                    speedController.reset()
                    noiseModel.reset()

                    val controller = RepeatTraversalController(repeatPolicy, repeatCount)
                    var traversal =
                        RepeatTraversalState(
                            progress =
                                resumeState?.lapProgressPercent?.toDouble() ?: resumeState?.progressPercent?.toDouble()
                                    ?: 0.0,
                            direction = resumeState?.direction ?: 1,
                            currentLap = resumeState?.currentLap ?: 1,
                        )
                    var frameCount = 0L
                    val elapsedOffsetMs = (resumeState?.elapsedTimeSec ?: 0L) * 1000L
                    val loopStartedAtMs = SystemClock.elapsedRealtime()

                    val totalDist = interpolator.totalDistanceMeters.takeIf { it > 0 } ?: 1.0
                    val nominalLapCount =
                        if (repeatPolicy == RepeatPolicy.LOOP_N ||
                            repeatPolicy == RepeatPolicy.PING_PONG_N
                        ) {
                            repeatCount.toDouble()
                        } else {
                            1.0
                        }
                    activeHistoryRouteDistanceMeters = route.distanceMeters.coerceAtLeast(0.0) * nominalLapCount
                    var lastWaypointIndex = 0
                    val triggeredLoopSegments = mutableSetOf<Int>()

                    while (!traversal.completed) {
                        val frameStart = System.currentTimeMillis()

                        if (pendingWaypointSkip != 0) {
                            traversal =
                                traversal.copy(
                                    progress = skipToAdjacentWaypoint(route, traversal.progress, pendingWaypointSkip)
                                )
                            pendingWaypointSkip = 0
                        }

                        val distanceAlongRoute = traversal.progress * totalDist
                        val frame = interpolator.positionAt(distanceAlongRoute)
                        val segmentIndex = currentSegmentIndex(route, traversal.progress)
                        val runtimeBehavior =
                            resolveSegmentRuntimeBehavior(
                                route = route,
                                segmentIndex = segmentIndex,
                                profile = profile,
                                baseSpeedRatio = speedRatio,
                                defaultWaypointPauseSec = waypointPauseSec,
                                repeatPolicy = repeatPolicy,
                                triggeredLoopSegments = triggeredLoopSegments,
                            )
                        speedController.targetRatio = runtimeBehavior.targetRatio
                        val distToNextWaypoint =
                            distanceToNextWaypointMeters(interpolator, traversal.progress, traversal.direction)
                        val distPerFrame = speedController.advance(deltaTimeSec, distToNextWaypoint)
                        val frameTimestampMs = System.currentTimeMillis()
                        val frameElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()

                        val rawLoc =
                            MockLocation(
                                lat = frame.lat,
                                lng = frame.lng,
                                altitude = interpolateAltitude(interpolator, route, distanceAlongRoute),
                                bearing =
                                    if (traversal.direction >=
                                        0
                                    ) {
                                        frame.bearing
                                    } else {
                                        ((frame.bearing + 180f) % 360f)
                                    },
                                speed = speedController.currentSpeedMs.toFloat(),
                                timestampMs = frameTimestampMs,
                                elapsedRealtimeNanos = frameElapsedRealtimeNanos,
                            )
                        val loc = noiseModel.applyToLocation(rawLoc, deltaTimeSec)

                        mockLocationInjector.inject(loc)
                        val elapsedSec = ((SystemClock.elapsedRealtime() - loopStartedAtMs) + elapsedOffsetMs) / 1000L

                        val runningState =
                            SimulationState.Running(
                                currentLocation = loc,
                                profileName = profile.name,
                                progressPercent =
                                    if (repeatPolicy == RepeatPolicy.LOOP_N ||
                                        repeatPolicy == RepeatPolicy.PING_PONG_N
                                    ) {
                                        (((traversal.currentLap - 1) + traversal.progress) / repeatCount.toDouble())
                                            .coerceIn(
                                                0.0,
                                                1.0
                                            ).toFloat()
                                    } else {
                                        traversal.progress.toFloat()
                                    },
                                lapProgressPercent = traversal.progress.toFloat(),
                                currentLap = traversal.currentLap,
                                totalLapsLabel = controller.totalLapsLabel(),
                                direction = traversal.direction,
                                elapsedTimeSec = elapsedSec,
                                frameCount = frameCount,
                            )
                        emitStateAndRefresh(runningState, refreshSurfaces = frameCount == 0L)

                        traversal = controller.advance(traversal, distPerFrame / totalDist)
                        if (runtimeBehavior.shouldRestartFromStart) {
                            val waypointIndex = currentWaypointIndex(route, traversal.progress)
                            if (waypointIndex > lastWaypointIndex) {
                                triggeredLoopSegments += segmentIndex
                                traversal = traversal.copy(progress = 0.0)
                                lastWaypointIndex = 0
                            }
                        } else if (runtimeBehavior.pauseSec > 0.0) {
                            val waypointIndex = currentWaypointIndex(route, traversal.progress)
                            if (waypointIndex > lastWaypointIndex) {
                                lastWaypointIndex = waypointIndex
                                delay((runtimeBehavior.pauseSec * 1000.0).toLong())
                            }
                        }
                        frameCount++

                        val elapsed = System.currentTimeMillis() - frameStart
                        delay((intervalMs - elapsed).coerceAtLeast(0L))
                    }

                    // Simulation completed normally
                    finishActiveHistory(
                        resultStatus = "COMPLETED",
                        distanceMeters = activeHistoryRouteDistanceMeters,
                    )
                    repository.reset()
                    refreshCompanionSurfaces(SimulationState.Idle)
                    noiseModel.reset()
                    mockLocationInjector.unregisterProvider()
                    repository.clearPausedSnapshot()
                    simulationJob = null
                    stopSelf()
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.e(TAG, LogSanitizer.sanitizeString("Simulation error"), e)
                    finishActiveHistory(
                        resultStatus = "ERROR",
                        distanceMeters = estimateCoveredDistanceMeters(),
                    )
                    emitStateAndRefresh(SimulationState.Error(e.message ?: "Unknown simulation error"))
                    repository.emitRoute(null)
                    repository.clearPausedSnapshot()
                    simulationJob = null
                    stopSelf()
                }
            }
    }

    // ── Joystick manual loop ─────────────────────────────────────────────────

    /**
     * Replaces the interpolation loop when [AppMode.JOYSTICK] is active.
     *
     * Instead of advancing along a precomputed route, each frame reads
     * the live [JoystickState] from the repository and displaces the
     * current position by (angle, magnitude × maxSpeed × Δt).
     */
    private suspend fun runJoystickLoop(
        profile: MovementProfile,
        initialLat: Double,
        initialLng: Double,
        intervalMs: Long,
    ) {
        mockLocationInjector.registerProvider()
        val noiseModel = LayeredNoiseModel.fromProfile(profile)
        noiseModel.reset()

        var lat = initialLat
        var lng = initialLng
        var frameCount = 0L

        val deltaTimeSec = intervalMs / 1000.0
        val loopStartedAtMs = SystemClock.elapsedRealtime()

        try {
            while (true) {
                val frameStart = System.currentTimeMillis()
                val joystick = repository.joystickState.value

                if (joystick.magnitude > 0.01f) {
                    val speedMs = profile.maxSpeedMs * joystick.magnitude
                    val distDelta = speedMs * deltaTimeSec

                    // Convert bearing to displacement in degrees (approximate)
                    val bearingRad = Math.toRadians(joystick.angle.toDouble())
                    val dLat = (distDelta / 111_320.0) * Math.cos(bearingRad)
                    val cosLat = Math.cos(Math.toRadians(lat)).coerceAtLeast(1e-6)
                    val dLng = (distDelta / (111_320.0 * cosLat)) * Math.sin(bearingRad)

                    lat = (lat + dLat).coerceIn(-90.0, 90.0)
                    lng = (lng + dLng).coerceIn(-180.0, 180.0)
                }

                val frameTimestampMs = System.currentTimeMillis()
                val frameElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()

                val rawLoc =
                    MockLocation(
                        lat = lat,
                        lng = lng,
                        altitude = 0.0,
                        bearing = joystick.angle,
                        speed = (profile.maxSpeedMs * joystick.magnitude).toFloat(),
                        timestampMs = frameTimestampMs,
                        elapsedRealtimeNanos = frameElapsedRealtimeNanos,
                    )
                val loc = noiseModel.applyToLocation(rawLoc, deltaTimeSec)
                mockLocationInjector.inject(loc)

                val elapsedSec = (SystemClock.elapsedRealtime() - loopStartedAtMs) / 1000L

                emitStateAndRefresh(
                    SimulationState.Running(
                        currentLocation = loc,
                        profileName = profile.name,
                        progressPercent = 0f, // indefinite in joystick mode
                        elapsedTimeSec = elapsedSec,
                        frameCount = frameCount,
                    ),
                    refreshSurfaces = frameCount == 0L
                )

                frameCount++

                val elapsed = System.currentTimeMillis() - frameStart
                delay((intervalMs - elapsed).coerceAtLeast(0L))
            }
        } finally {
            noiseModel.reset()
            mockLocationInjector.unregisterProvider()
        }
    }

    // ── Pause / stop ─────────────────────────────────────────────────────────

    private fun pauseSimulation() {
        val currentState = repository.state.value
        if (currentState is SimulationState.Running) {
            simulationJob?.cancel()
            simulationJob = null
            val pausedState =
                SimulationState.Paused(
                    lastLocation = currentState.currentLocation,
                    profileName = currentState.profileName,
                    progressPercent = currentState.progressPercent,
                    lapProgressPercent = currentState.lapProgressPercent,
                    currentLap = currentState.currentLap,
                    totalLapsLabel = currentState.totalLapsLabel,
                    direction = currentState.direction,
                    elapsedTimeSec = currentState.elapsedTimeSec,
                )
            emitStateAndRefresh(pausedState)
            lifecycleScope.launch {
                repository.persistPausedSnapshot(pausedState, activeHistoryId, activeHistoryStartedAtMs)
            }
        }
    }

    private fun stopSimulation() {
        if (isStopping) return
        isStopping = true

        simulationJob?.cancel()
        simulationJob = null
        if (activeHistoryId != null) {
            runCatching {
                runBlocking {
                    finishActiveHistory(
                        resultStatus = "INTERRUPTED",
                        distanceMeters = estimateCoveredDistanceMeters(),
                    )
                }
            }.onFailure { error ->
                Log.e(TAG, LogSanitizer.sanitizeString("Failed to finalize interrupted history"), error)
            }
        }
        runCatching { mockLocationInjector.unregisterProvider() }
        runCatching { runBlocking { repository.clearPausedSnapshot() } }
        repository.reset()
        refreshCompanionSurfaces(SimulationState.Idle)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun finishActiveHistory(
        resultStatus: String,
        distanceMeters: Double,
    ) {
        val id = activeHistoryId ?: return
        val startedAt = activeHistoryStartedAtMs ?: System.currentTimeMillis()
        val durationMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
        repository.finishHistory(
            id = id,
            durationMs = durationMs,
            distanceMeters = distanceMeters.coerceAtLeast(0.0),
            resultStatus = resultStatus,
        )
        activeHistoryId = null
        activeHistoryStartedAtMs = null
        activeHistoryRouteDistanceMeters = 0.0
    }

    private fun estimateCoveredDistanceMeters(): Double {
        val progress =
            when (val state = repository.state.value) {
                is SimulationState.Running -> state.progressPercent.toDouble()
                is SimulationState.Paused -> state.progressPercent.toDouble()
                else -> 0.0
            }.coerceIn(0.0, 1.0)

        return (activeHistoryRouteDistanceMeters * progress).roundToLong().toDouble()
    }

    private fun emitStateAndRefresh(
        state: SimulationState,
        refreshSurfaces: Boolean = true,
    ) {
        repository.emitState(state)
        if (refreshSurfaces) refreshCompanionSurfaces(state)
    }

    private fun refreshCompanionSurfaces(state: SimulationState) {
        GhostPinWidget.updateAll(this, state)
        GhostPinQsTile.requestUpdate(this)
    }

    private fun resolveProfile(profileIdOrName: String?): MovementProfile? {
        if (profileIdOrName.isNullOrBlank()) return null
        MovementProfile.BUILT_IN[profileIdOrName]?.let { return it }

        return runBlocking {
            profileDao.getById(profileIdOrName)?.toDomain()
                ?: profileDao.getByName(profileIdOrName)?.toDomain()
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun distanceToNextWaypointMeters(
        interpolator: RouteInterpolator,
        progress: Double,
        direction: Int,
    ): Double {
        val segmentIndex = currentSegmentIndex(interpolator.route, progress)
        val distanceAlongRoute = progress.coerceIn(0.0, 1.0) * interpolator.totalDistanceMeters

        return if (direction >= 0) {
            val nextWaypointIndex = (segmentIndex + 1).coerceAtMost(interpolator.route.waypoints.lastIndex)
            (interpolator.distanceToWaypoint(nextWaypointIndex) - distanceAlongRoute).coerceAtLeast(0.0)
        } else {
            val previousWaypointIndex = segmentIndex.coerceAtLeast(0)
            (distanceAlongRoute - interpolator.distanceToWaypoint(previousWaypointIndex)).coerceAtLeast(0.0)
        }
    }

    private fun currentSegmentIndex(
        route: Route,
        progress: Double
    ): Int {
        if (route.waypoints.size < 2) return 0
        return (currentWaypointIndex(route, progress) - 1).coerceIn(0, route.waypoints.lastIndex - 1)
    }

    private fun interpolateAltitude(
        interpolator: RouteInterpolator,
        route: Route,
        distanceMeters: Double,
    ): Double {
        if (route.waypoints.size < 2) return route.waypoints.firstOrNull()?.altitude ?: 0.0

        val clamped = distanceMeters.coerceIn(0.0, interpolator.totalDistanceMeters)
        val segmentIndex =
            currentSegmentIndex(
                route,
                if (interpolator.totalDistanceMeters >
                    0.0
                ) {
                    clamped / interpolator.totalDistanceMeters
                } else {
                    0.0
                }
            )
        val segStart = interpolator.cumulativeDistances[segmentIndex]
        val segEnd =
            interpolator.cumulativeDistances[
                (segmentIndex + 1).coerceAtMost(
                    interpolator.cumulativeDistances.lastIndex
                )
            ]
        val segLength = (segEnd - segStart).coerceAtLeast(1e-6)
        val t = ((clamped - segStart) / segLength).coerceIn(0.0, 1.0)
        val a = route.waypoints[segmentIndex]
        val b = route.waypoints[(segmentIndex + 1).coerceAtMost(route.waypoints.lastIndex)]
        return a.altitude + (b.altitude - a.altitude) * t
    }

    private fun currentWaypointIndex(
        route: Route,
        progress: Double
    ): Int {
        if (route.waypoints.size < 2) return 0
        val totalDist = route.distanceMeters.coerceAtLeast(1.0)
        val target = progress.coerceIn(0.0, 1.0) * totalDist
        var covered = 0.0
        for (i in 0 until route.waypoints.size - 1) {
            val a = route.waypoints[i]
            val b = route.waypoints[i + 1]
            val segDist = GeoMath.haversineMeters(a.lat, a.lng, b.lat, b.lng)
            if (covered + segDist >= target) return i + 1
            covered += segDist
        }
        return route.waypoints.lastIndex
    }

    private fun skipToAdjacentWaypoint(
        route: Route,
        progress: Double,
        direction: Int
    ): Double {
        if (route.waypoints.size < 2) return progress

        val totalDist = route.distanceMeters.coerceAtLeast(1.0)
        val target = progress.coerceIn(0.0, 1.0) * totalDist
        var covered = 0.0
        var nextWaypointIndex = route.waypoints.lastIndex

        for (i in 0 until route.waypoints.size - 1) {
            val a = route.waypoints[i]
            val b = route.waypoints[i + 1]
            val segDist = GeoMath.haversineMeters(a.lat, a.lng, b.lat, b.lng)
            if (covered + segDist >= target) {
                nextWaypointIndex = i + 1
                break
            }
            covered += segDist
        }

        val desiredIndex =
            if (direction > 0) {
                (nextWaypointIndex + 1).coerceAtMost(route.waypoints.lastIndex)
            } else {
                (nextWaypointIndex - 1).coerceAtLeast(0)
            }

        var desiredDistance = 0.0
        for (i in 0 until desiredIndex) {
            val a = route.waypoints[i]
            val b = route.waypoints[i + 1]
            desiredDistance += GeoMath.haversineMeters(a.lat, a.lng, b.lat, b.lng)
        }

        return (desiredDistance / totalDist).coerceIn(0.0, 1.0)
    }

    private fun buildNotification(profileName: String): Notification {
        val openIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val stopIntent =
            PendingIntent.getService(
                this,
                1,
                Intent(this, SimulationService::class.java).apply { action = ACTION_STOP },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        return NotificationCompat
            .Builder(this, GhostPinApp.CHANNEL_SIMULATION)
            .setContentTitle(getString(R.string.notification_simulation_title))
            .setContentText(getString(R.string.notification_simulation_text, profileName))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.action_stop), stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun Double.isValidLat(): Boolean = !isNaN() && !isInfinite() && this in -90.0..90.0

    private fun Double.isValidLng(): Boolean = !isNaN() && !isInfinite() && this in -180.0..180.0
}
