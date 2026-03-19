package com.ghostpin.app.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.ghostpin.app.BuildConfig
import com.ghostpin.app.GhostPinApp
import com.ghostpin.app.R
import com.ghostpin.app.data.SimulationRepository
import com.ghostpin.app.routing.OsrmRouteProvider
import com.ghostpin.app.routing.RouteFileParser
import com.ghostpin.app.ui.MainActivity
import com.ghostpin.app.widget.GhostPinWidget
import com.ghostpin.core.security.LogSanitizer
import com.ghostpin.app.location.MockLocationInjector
import com.ghostpin.app.data.SimulationConfig
import com.ghostpin.engine.validation.TrajectoryValidator
import com.ghostpin.core.model.AppMode
import com.ghostpin.core.model.DefaultCoordinates
import com.ghostpin.core.model.MockLocation
import com.ghostpin.core.model.MovementProfile
import com.ghostpin.core.model.Route
import com.ghostpin.core.model.distanceMeters
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.*

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

    @Inject lateinit var mockLocationInjector: MockLocationInjector
    @Inject lateinit var osrmRouteProvider: OsrmRouteProvider
    @Inject lateinit var repository: SimulationRepository
    @Inject lateinit var trajectoryValidator: TrajectoryValidator
    @Inject lateinit var routeFileParser: RouteFileParser

    private var simulationJob: Job? = null
    private var pendingWaypointSkip: Int = 0

    companion object {
        private const val TAG = "SimulationService"

        const val EXTRA_PROFILE_NAME  = "profile_name"
        const val EXTRA_START_LAT     = "start_lat"
        const val EXTRA_START_LNG     = "start_lng"
        const val EXTRA_END_LAT       = "end_lat"
        const val EXTRA_END_LNG       = "end_lng"
        const val EXTRA_FREQUENCY_HZ  = "frequency_hz"
        const val EXTRA_SPEED_RATIO   = "speed_ratio"
        const val EXTRA_ROUTE_ID      = "extra_route_id"
        // Sprint 6
        const val EXTRA_MODE          = "extra_mode"
        const val EXTRA_WAYPOINTS_LAT = "extra_waypoints_lat"
        const val EXTRA_WAYPOINTS_LNG = "extra_waypoints_lng"
        const val EXTRA_WAYPOINT_PAUSE_SEC = "extra_waypoint_pause_sec"

        const val ACTION_START        = "com.ghostpin.ACTION_START"
        const val ACTION_STOP         = "com.ghostpin.ACTION_STOP"
        const val ACTION_PAUSE        = "com.ghostpin.ACTION_PAUSE"
        const val ACTION_SET_ROUTE    = "com.ghostpin.ACTION_SET_ROUTE"
        const val ACTION_SET_PROFILE  = "com.ghostpin.ACTION_SET_PROFILE"
        const val ACTION_SKIP_NEXT_WAYPOINT = "com.ghostpin.ACTION_SKIP_NEXT_WAYPOINT"
        const val ACTION_SKIP_PREV_WAYPOINT = "com.ghostpin.ACTION_SKIP_PREV_WAYPOINT"

        const val NOTIFICATION_ID     = 1001
        const val DEFAULT_FREQUENCY   = 5   // Hz — smooth map animation

        private const val MIN_FREQUENCY = 1  // Hz
        private const val MAX_FREQUENCY = 60 // Hz
    }

    // ── Service lifecycle ────────────────────────────────────────────────────

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent == null) {
            Log.w(TAG, "Received null intent — stopping without starting simulation.")
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent.action == ACTION_STOP) {
            stopSimulation()
            return START_NOT_STICKY
        }

        if (intent.action == ACTION_PAUSE) {
            pauseSimulation()
            return START_NOT_STICKY
        }

        if (intent.action == ACTION_SET_PROFILE) {
            val profileName = intent.getStringExtra(EXTRA_PROFILE_NAME)
            if (!profileName.isNullOrBlank() && MovementProfile.BUILT_IN.containsKey(profileName)) {
                val current = repository.lastUsedConfig.value
                repository.emitConfig(
                    SimulationConfig(
                        profileName = profileName,
                        startLat = current?.startLat ?: DefaultCoordinates.START_LAT,
                        startLng = current?.startLng ?: DefaultCoordinates.START_LNG,
                        routeId = current?.routeId,
                    )
                )
            } else {
                repository.emitState(SimulationState.Error("Invalid profile for ACTION_SET_PROFILE"))
            }
            return START_NOT_STICKY
        }

        if (intent.action == ACTION_SET_ROUTE) {
            val uri = intent.data
            if (uri == null) {
                repository.emitState(SimulationState.Error("Missing route URI for ACTION_SET_ROUTE"))
                return START_NOT_STICKY
            }

            lifecycleScope.launch {
                runCatching {
                    contentResolver.openInputStream(uri)?.use { input ->
                        val content = input.bufferedReader().use { it.readText() }
                        routeFileParser.parse(content).getOrThrow()
                    } ?: error("Cannot open URI: $uri")
                }.onSuccess { route ->
                    repository.emitRoute(route)
                }.onFailure { e ->
                    repository.emitState(SimulationState.Error(e.message ?: "Failed to parse route file"))
                }
            }
            return START_NOT_STICKY
        }

        if (intent.action == ACTION_SKIP_NEXT_WAYPOINT) {
            pendingWaypointSkip = 1
            return START_NOT_STICKY
        }

        if (intent.action == ACTION_SKIP_PREV_WAYPOINT) {
            pendingWaypointSkip = -1
            return START_NOT_STICKY
        }

        if (!BuildConfig.MOCK_PROVIDER_ENABLED) {
            repository.emitState(SimulationState.Error(
                "Mock provider not available in this build. " +
                "Enable Developer Options → Mock location app."
            ))
            stopSelf()
            return START_NOT_STICKY
        }

        // ── Parse operating mode ─────────────────────────────────────────────
        val modeName  = intent.getStringExtra(EXTRA_MODE) ?: AppMode.CLASSIC.name
        val appMode   = runCatching { AppMode.valueOf(modeName) }.getOrDefault(AppMode.CLASSIC)

        // ── Resume detection ──────────────────────────────────────────────────
        val startLatRaw = intent.getDoubleExtra(EXTRA_START_LAT, Double.NaN)
        val isResume    = repository.state.value is SimulationState.Paused && startLatRaw.isNaN()

        val frequencyHz = intent.getIntExtra(EXTRA_FREQUENCY_HZ, DEFAULT_FREQUENCY)
            .coerceIn(MIN_FREQUENCY, MAX_FREQUENCY)
        val speedRatio = intent.getDoubleExtra(EXTRA_SPEED_RATIO, 1.0).coerceIn(0.0, 1.0)
        val waypointPauseSec = intent.getDoubleExtra(EXTRA_WAYPOINT_PAUSE_SEC, 0.0).coerceIn(0.0, 30.0)

        // ── Start overlay bubble ──────────────────────────────────────────────
        if (android.provider.Settings.canDrawOverlays(this)) {
            // For JOYSTICK mode pass the auto-open flag so the overlay
            // shows the joystick immediately without a manual toggle.
            val bubbleIntent = if (appMode == AppMode.JOYSTICK) {
                FloatingBubbleService.showJoystickIntent(this)
            } else {
                FloatingBubbleService.showIntent(this)
            }
            startService(bubbleIntent)
        }

        if (isResume) {
            val pausedState = repository.state.value as SimulationState.Paused
            val profile     = MovementProfile.BUILT_IN[pausedState.profileName] ?: MovementProfile.PEDESTRIAN
            startForeground(NOTIFICATION_ID, buildNotification(profile.name))
            startSimulation(
                profile     = profile,
                startLat    = 0.0,
                startLng    = 0.0,
                endLat      = 0.0,
                endLng      = 0.0,
                frequencyHz = frequencyHz,
                speedRatio  = speedRatio,
                appMode     = appMode,
                waypointPauseSec = waypointPauseSec,
                resumeState = pausedState,
            )
            return START_NOT_STICKY
        }

        // ── Normal start ──────────────────────────────────────────────────────
        val profileName = intent.getStringExtra(EXTRA_PROFILE_NAME) ?: "Pedestrian"

        val startLat = startLatRaw
            .takeIf { it.isValidLat() } ?: DefaultCoordinates.START_LAT.also {
                Log.w(TAG, "Invalid startLat received — falling back to default.")
            }
        val startLng = intent.getDoubleExtra(EXTRA_START_LNG, DefaultCoordinates.START_LNG)
            .takeIf { it.isValidLng() } ?: DefaultCoordinates.START_LNG.also {
                Log.w(TAG, "Invalid startLng received — falling back to default.")
            }
        val endLat = intent.getDoubleExtra(EXTRA_END_LAT, DefaultCoordinates.END_LAT)
            .takeIf { it.isValidLat() } ?: DefaultCoordinates.END_LAT.also {
                Log.w(TAG, "Invalid endLat received — falling back to default.")
            }
        val endLng = intent.getDoubleExtra(EXTRA_END_LNG, DefaultCoordinates.END_LNG)
            .takeIf { it.isValidLng() } ?: DefaultCoordinates.END_LNG.also {
                Log.w(TAG, "Invalid endLng received — falling back to default.")
            }

        val profile = MovementProfile.BUILT_IN[profileName] ?: MovementProfile.PEDESTRIAN.also {
            Log.w(TAG, LogSanitizer.sanitizeString("Unknown profile '$profileName' — defaulting to Pedestrian."))
        }

        val waypointsLat = intent.getDoubleArrayExtra(EXTRA_WAYPOINTS_LAT)
        val waypointsLng = intent.getDoubleArrayExtra(EXTRA_WAYPOINTS_LNG)
        val waypointsList = if (waypointsLat != null && waypointsLng != null && waypointsLat.size == waypointsLng.size) {
            waypointsLat.zip(waypointsLng)
                .filter { (lat, lng) -> lat.isValidLat() && lng.isValidLng() }
                .map { com.ghostpin.core.model.Waypoint(it.first, it.second) }
        } else {
            emptyList()
        }

        if (appMode == AppMode.WAYPOINTS && waypointsList.size < 2) {
            repository.emitState(SimulationState.Error("Add at least 2 waypoints to start multi-stop mode."))
            stopSelf()
            return START_NOT_STICKY
        }

        repository.emitConfig(SimulationConfig(profile.name, startLat, startLng, routeId = intent.getStringExtra(EXTRA_ROUTE_ID)))
        startForeground(NOTIFICATION_ID, buildNotification(profile.name))
        startSimulation(profile, startLat, startLng, endLat, endLng, frequencyHz, speedRatio, appMode, waypointPauseSec = waypointPauseSec, resumeState = null, waypoints = waypointsList)

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopService(Intent(this, FloatingBubbleService::class.java))
        stopSimulation()
        super.onDestroy()
    }

    // ── Simulation loop ──────────────────────────────────────────────────────

    private fun startSimulation(
        profile:     MovementProfile,
        startLat:    Double,
        startLng:    Double,
        endLat:      Double,
        endLng:      Double,
        frequencyHz: Int,
        speedRatio:  Double = 1.0,
        appMode:     AppMode = AppMode.CLASSIC,
        waypointPauseSec: Double = 0.0,
        resumeState: SimulationState.Paused? = null,
        waypoints:   List<com.ghostpin.core.model.Waypoint> = emptyList(),
    ) {
        simulationJob?.cancel()
        if (resumeState == null) repository.emitRoute(null)

        val intervalMs   = 1000L / frequencyHz
        val deltaTimeSec = 1.0 / frequencyHz

        simulationJob = lifecycleScope.launch {
            try {
                // ── 1. Obtain route ──────────────────────────────────────────
                val route: Route = when {
                    // Resume: reuse the existing route already cached in repo
                    resumeState != null && repository.route.value != null -> {
                        repository.route.value!!
                    }

                    // GPX mode: route was pre-loaded by the ViewModel's file picker.
                    // Wait briefly in case the coroutine hasn't committed it yet.
                    appMode == AppMode.GPX -> {
                        val preloaded = repository.route.value
                            ?: repository.route.first { it != null }
                        if (preloaded == null) {
                            repository.emitState(SimulationState.Error(
                                "No GPX route loaded. Please select a .gpx file first."
                            ))
                            stopSelf()
                            return@launch
                        }
                        Log.d(TAG, "GPX mode — using pre-loaded route (${preloaded.waypoints.size} pts), skipping OSRM.")
                        preloaded
                    }

                    // Joystick mode: no route needed; we park at start position.
                    appMode == AppMode.JOYSTICK -> {
                        repository.emitState(SimulationState.Running(
                            currentLocation = MockLocation(startLat, startLng, 0.0, 0f, 0f),
                            profileName     = profile.name,
                            progressPercent = 0f,
                            elapsedTimeSec  = 0L,
                            frameCount      = 0L,
                        ))
                        runJoystickLoop(profile, startLat, startLng, intervalMs)
                        return@launch
                    }

                    // Waypoints Mode -> fetch using multiple points
                    appMode == AppMode.WAYPOINTS && waypoints.size >= 2 -> {
                        repository.emitState(SimulationState.FetchingRoute(profile.name))
                        val newRoute = osrmRouteProvider
                            .fetchMultiRoute(waypoints, profile)
                            .getOrElse { error ->
                                Log.w(TAG, LogSanitizer.sanitizeString(
                                    "OSRM multi-fetch failed — straight-line fallback: ${error.message}"
                                ))
                                osrmRouteProvider.fallbackMultiRoute(waypoints)
                            }
                        repository.emitRoute(newRoute)
                        newRoute
                    }

                    // Classic Mode: fetch from OSRM (or fallback)
                    else -> {
                        repository.emitState(SimulationState.FetchingRoute(profile.name))
                        val newRoute = osrmRouteProvider
                            .fetchRoute(startLat, startLng, endLat, endLng, profile)
                            .getOrElse { error ->
                                Log.w(TAG, LogSanitizer.sanitizeString(
                                    "OSRM fetch failed — straight-line fallback: ${error.message}"
                                ))
                                osrmRouteProvider.fallbackRoute(startLat, startLng, endLat, endLng)
                            }
                        repository.emitRoute(newRoute)
                        newRoute
                    }
                }

                // ── 2. Validate route ────────────────────────────────────────
                if (resumeState == null && appMode != AppMode.GPX) {
                    repository.emitRoute(route)
                }

                // ── 3. Interpolation loop ────────────────────────────────────
                mockLocationInjector.registerProvider()

                val startProgress = resumeState?.progressPercent?.toDouble() ?: 0.0
                var progress      = startProgress
                var elapsedSec    = resumeState?.elapsedTimeSec ?: 0L
                var frameCount    = 0L

                val totalDist = route.distanceMeters.takeIf { it > 0 } ?: 1.0
                val speedMs   = profile.maxSpeedMs * speedRatio
                var lastWaypointIndex = 0

                while (progress < 1.0) {
                    val frameStart = System.currentTimeMillis()

                    if (pendingWaypointSkip != 0) {
                        progress = skipToAdjacentWaypoint(route, progress, pendingWaypointSkip)
                        pendingWaypointSkip = 0
                    }

                    if (appMode == AppMode.WAYPOINTS && waypointPauseSec > 0.0) {
                        val idx = currentWaypointIndex(route, progress)
                        if (idx > lastWaypointIndex) {
                            lastWaypointIndex = idx
                            delay((waypointPauseSec * 1000.0).toLong())
                        }
                    }

                    val (lat, lng) = interpolate(route, progress)
                    val altitude   = estimateAltitude(route, progress)
                    val bearing    = computeBearing(route, progress)

                    val loc = MockLocation(
                        lat       = lat,
                        lng       = lng,
                        altitude  = altitude,
                        bearing   = bearing,
                        speed     = speedMs.toFloat(),
                    )

                    mockLocationInjector.inject(loc)

                    val runningState = SimulationState.Running(
                        currentLocation = loc,
                        profileName     = profile.name,
                        progressPercent = progress.toFloat(),
                        elapsedTimeSec  = elapsedSec,
                        frameCount      = frameCount,
                    )
                    repository.emitState(runningState)
                    GhostPinWidget.updateAll(this@SimulationService, runningState)

                    val distPerFrame = speedMs * deltaTimeSec
                    progress  = (progress + distPerFrame / totalDist).coerceAtMost(1.0)
                    elapsedSec++
                    frameCount++

                    val elapsed = System.currentTimeMillis() - frameStart
                    delay((intervalMs - elapsed).coerceAtLeast(0L))
                }

                // Simulation completed normally
                repository.reset()
                GhostPinWidget.updateAll(this@SimulationService, SimulationState.Idle)
                mockLocationInjector.unregisterProvider()
                stopSelf()

            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "Simulation error", e)
                repository.emitState(SimulationState.Error(e.message ?: "Unknown simulation error"))
                repository.emitRoute(null)
                GhostPinWidget.updateAll(this@SimulationService, SimulationState.Idle)
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
        profile:     MovementProfile,
        initialLat:  Double,
        initialLng:  Double,
        intervalMs:  Long,
    ) {
        mockLocationInjector.registerProvider()

        var lat        = initialLat
        var lng        = initialLng
        var frameCount = 0L
        var elapsedSec = 0L

        val deltaTimeSec = intervalMs / 1000.0

        try {
            while (true) {
                val frameStart = System.currentTimeMillis()
                val joystick   = repository.joystickState.value

                if (joystick.magnitude > 0.01f) {
                    val speedMs    = profile.maxSpeedMs * joystick.magnitude
                    val distDelta  = speedMs * deltaTimeSec

                    // Convert bearing to displacement in degrees (approximate)
                    val bearingRad = Math.toRadians(joystick.angle.toDouble())
                    val dLat       = (distDelta / 111_320.0) * Math.cos(bearingRad)
                    val cosLat     = Math.cos(Math.toRadians(lat)).coerceAtLeast(1e-6)
                    val dLng       = (distDelta / (111_320.0 * cosLat)) * Math.sin(bearingRad)

                    lat = (lat + dLat).coerceIn(-90.0, 90.0)
                    lng = (lng + dLng).coerceIn(-180.0, 180.0)
                }

                val loc = MockLocation(
                    lat      = lat,
                    lng      = lng,
                    altitude = 0.0,
                    bearing  = joystick.angle,
                    speed    = (profile.maxSpeedMs * joystick.magnitude).toFloat(),
                )
                mockLocationInjector.inject(loc)

                repository.emitState(SimulationState.Running(
                    currentLocation = loc,
                    profileName     = profile.name,
                    progressPercent = 0f, // indefinite in joystick mode
                    elapsedTimeSec  = elapsedSec,
                    frameCount      = frameCount,
                ))
                GhostPinWidget.updateAll(this@SimulationService, repository.state.value)

                frameCount++
                elapsedSec++

                val elapsed = System.currentTimeMillis() - frameStart
                delay((intervalMs - elapsed).coerceAtLeast(0L))
            }
        } finally {
            mockLocationInjector.unregisterProvider()
        }
    }

    // ── Pause / stop ─────────────────────────────────────────────────────────

    private fun pauseSimulation() {
        val currentState = repository.state.value
        if (currentState is SimulationState.Running) {
            simulationJob?.cancel()
            simulationJob = null
            val pausedState = SimulationState.Paused(
                lastLocation    = currentState.currentLocation,
                profileName     = currentState.profileName,
                progressPercent = currentState.progressPercent,
                elapsedTimeSec  = currentState.elapsedTimeSec,
            )
            repository.emitState(pausedState)
            GhostPinWidget.updateAll(this, pausedState)
        }
    }

    private fun stopSimulation() {
        simulationJob?.cancel()
        simulationJob = null
        runCatching { mockLocationInjector.unregisterProvider() }
        repository.reset()
        GhostPinWidget.updateAll(this, SimulationState.Idle)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Linearly interpolates a (lat, lng) position along [route] at [progress] ∈ [0, 1]. */
    private fun interpolate(route: Route, progress: Double): Pair<Double, Double> {
        if (route.waypoints.isEmpty()) return Pair(0.0, 0.0)
        if (route.waypoints.size == 1) return Pair(route.waypoints[0].lat, route.waypoints[0].lng)

        val totalDist = route.distanceMeters.coerceAtLeast(1.0)
        val target    = progress * totalDist
        var covered   = 0.0

        for (i in 0 until route.waypoints.size - 1) {
            val a = route.waypoints[i]
            val b = route.waypoints[i + 1]
            val segDist = haversineMeters(a.lat, a.lng, b.lat, b.lng)
            if (covered + segDist >= target) {
                val t = ((target - covered) / segDist.coerceAtLeast(1.0)).coerceIn(0.0, 1.0)
                return Pair(a.lat + t * (b.lat - a.lat), a.lng + t * (b.lng - a.lng))
            }
            covered += segDist
        }
        val last = route.waypoints.last()
        return Pair(last.lat, last.lng)
    }

    /** Bearing from current position toward next waypoint, in degrees. */
    private fun computeBearing(route: Route, progress: Double): Float {
        if (route.waypoints.size < 2) return 0f
        val totalDist = route.distanceMeters.coerceAtLeast(1.0)
        val target    = progress * totalDist
        var covered   = 0.0

        for (i in 0 until route.waypoints.size - 1) {
            val a = route.waypoints[i]
            val b = route.waypoints[i + 1]
            val segDist = haversineMeters(a.lat, a.lng, b.lat, b.lng)
            if (covered + segDist >= target) {
                val dLng = Math.toRadians(b.lng - a.lng)
                val aLat = Math.toRadians(a.lat)
                val bLat = Math.toRadians(b.lat)
                val y    = Math.sin(dLng) * Math.cos(bLat)
                val x    = Math.cos(aLat) * Math.sin(bLat) - Math.sin(aLat) * Math.cos(bLat) * Math.cos(dLng)
                return ((Math.toDegrees(Math.atan2(y, x)) + 360) % 360).toFloat()
            }
            covered += segDist
        }
        return 0f
    }



    private fun currentWaypointIndex(route: Route, progress: Double): Int {
        if (route.waypoints.size < 2) return 0
        val totalDist = route.distanceMeters.coerceAtLeast(1.0)
        val target = progress.coerceIn(0.0, 1.0) * totalDist
        var covered = 0.0
        for (i in 0 until route.waypoints.size - 1) {
            val a = route.waypoints[i]
            val b = route.waypoints[i + 1]
            val segDist = haversineMeters(a.lat, a.lng, b.lat, b.lng)
            if (covered + segDist >= target) return i + 1
            covered += segDist
        }
        return route.waypoints.lastIndex
    }

    private fun skipToAdjacentWaypoint(route: Route, progress: Double, direction: Int): Double {
        if (route.waypoints.size < 2) return progress

        val totalDist = route.distanceMeters.coerceAtLeast(1.0)
        val target = progress.coerceIn(0.0, 1.0) * totalDist
        var covered = 0.0
        var nextWaypointIndex = route.waypoints.lastIndex

        for (i in 0 until route.waypoints.size - 1) {
            val a = route.waypoints[i]
            val b = route.waypoints[i + 1]
            val segDist = haversineMeters(a.lat, a.lng, b.lat, b.lng)
            if (covered + segDist >= target) {
                nextWaypointIndex = i + 1
                break
            }
            covered += segDist
        }

        val desiredIndex = if (direction > 0) {
            (nextWaypointIndex + 1).coerceAtMost(route.waypoints.lastIndex)
        } else {
            (nextWaypointIndex - 1).coerceAtLeast(0)
        }

        var desiredDistance = 0.0
        for (i in 0 until desiredIndex) {
            val a = route.waypoints[i]
            val b = route.waypoints[i + 1]
            desiredDistance += haversineMeters(a.lat, a.lng, b.lat, b.lng)
        }

        return (desiredDistance / totalDist).coerceIn(0.0, 1.0)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun estimateAltitude(route: Route, progress: Double): Double = 0.0

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R    = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a    = Math.sin(dLat / 2).let { it * it } +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2).let { it * it }
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    private fun buildNotification(profileName: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, SimulationService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, GhostPinApp.CHANNEL_SIMULATION)
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
