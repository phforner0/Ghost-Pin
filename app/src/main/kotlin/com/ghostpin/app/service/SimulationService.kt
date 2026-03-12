package com.ghostpin.app.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.ghostpin.core.security.LogSanitizer
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.ghostpin.app.BuildConfig
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import com.ghostpin.app.GhostPinApp
import com.ghostpin.app.R
import com.ghostpin.app.data.SimulationRepository
import com.ghostpin.app.location.MockLocationInjector
import com.ghostpin.app.routing.OsrmRouteProvider
import com.ghostpin.app.ui.MainActivity
import com.ghostpin.core.model.DefaultCoordinates
import com.ghostpin.core.model.MockLocation
import com.ghostpin.core.model.MovementProfile
import com.ghostpin.core.model.Route
import com.ghostpin.engine.interpolation.KalmanFilter1D
import com.ghostpin.engine.interpolation.RouteInterpolator
import com.ghostpin.engine.interpolation.SpeedController
import com.ghostpin.engine.noise.LayeredNoiseModel
import com.ghostpin.engine.validation.TrajectoryValidator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service running the GPS simulation loop.
 *
 * Changes from Sprint 2:
 *  - [SimulationRepository] replaces companion object MutableStateFlow — state is now
 *    injectable and testable without static globals.
 *  - START_NOT_STICKY: prevents phantom simulation on OS-triggered restart with null intent.
 *  - [TrajectoryValidator] is now called before the simulation loop begins.
 *  - [frequencyHz] is clamped to [MIN_FREQUENCY]..[MAX_FREQUENCY] to prevent division by zero.
 *  - Input coordinates are validated before use (NaN, Infinity, out-of-range).
 *  - [noiseModel] force-unwrap (!!) replaced by a local val — eliminates NPE race condition.
 *  - Timestamp captured once per frame for elapsedRealtimeNanos + timestampMs consistency.
 *  - [estimateAltitude] returns neutral 0.0 instead of Taubaté-specific 760m.
 */
@AndroidEntryPoint
class SimulationService : LifecycleService() {

    @Inject lateinit var mockLocationInjector: MockLocationInjector
    @Inject lateinit var osrmRouteProvider: OsrmRouteProvider
    @Inject lateinit var repository: SimulationRepository
    @Inject lateinit var trajectoryValidator: TrajectoryValidator

    private var simulationJob: Job? = null

    companion object {
        private const val TAG = "SimulationService"

        const val EXTRA_PROFILE_NAME  = "profile_name"
        const val EXTRA_START_LAT     = "start_lat"
        const val EXTRA_START_LNG     = "start_lng"
        const val EXTRA_END_LAT       = "end_lat"
        const val EXTRA_END_LNG       = "end_lng"
        const val EXTRA_FREQUENCY_HZ  = "frequency_hz"
        const val EXTRA_SPEED_RATIO   = "speed_ratio"
        const val EXTRA_ROUTE_ID      = "route_id"

        const val ACTION_START        = "com.ghostpin.action.START"
        const val ACTION_STOP         = "com.ghostpin.action.STOP"
        const val ACTION_PAUSE        = "com.ghostpin.action.PAUSE"
        const val ACTION_SET_ROUTE    = "com.ghostpin.action.SET_ROUTE"
        const val ACTION_SET_PROFILE  = "com.ghostpin.action.SET_PROFILE"

        const val NOTIFICATION_ID     = 1001
        const val DEFAULT_FREQUENCY   = 5   // Hz — smooth map animation

        private const val MIN_FREQUENCY = 1  // Hz — minimum to prevent division by zero
        private const val MAX_FREQUENCY = 60 // Hz — upper bound to avoid excessive CPU use
    }

    // ── Service lifecycle ────────────────────────────────────────────────────

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        // Fix (🔴): When the OS restarts a START_STICKY service after being killed,
        // the intent is null. We must NOT silently start a phantom simulation with
        // hardcoded coordinates. Return START_NOT_STICKY so the service is never
        // restarted automatically — it must always be started explicitly by the UI.
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

        if (!BuildConfig.MOCK_PROVIDER_ENABLED) {
            repository.emitState(SimulationState.Error(
                "Mock provider not available in this build. " +
                "Enable Developer Options → Mock location app."
            ))
            stopSelf()
            return START_NOT_STICKY
        }

        // --- At this point, the action is ACTION_START (either explicit or implicit empty action)
        val startLatRaw = intent.getDoubleExtra(EXTRA_START_LAT, Double.NaN)
        val isResume = repository.state.value is SimulationState.Paused && startLatRaw.isNaN()

        val frequencyHz = intent.getIntExtra(EXTRA_FREQUENCY_HZ, DEFAULT_FREQUENCY)
            .coerceIn(MIN_FREQUENCY, MAX_FREQUENCY)

        if (isResume) {
            val pausedState = repository.state.value as SimulationState.Paused
            val profile = MovementProfile.BUILT_IN[pausedState.profileName] ?: MovementProfile.PEDESTRIAN
            
            // Start overlay service if permission is granted
            if (android.provider.Settings.canDrawOverlays(this)) {
                startService(FloatingBubbleService.showIntent(this))
            }
            startForeground(NOTIFICATION_ID, buildNotification(profile.name))
            startSimulation(profile, 0.0, 0.0, 0.0, 0.0, frequencyHz, resumeState = pausedState)
            return START_NOT_STICKY
        }

        val profileName = intent.getStringExtra(EXTRA_PROFILE_NAME) ?: "Pedestrian"

        // Fix (🟡): Validate all coordinate inputs — reject NaN, Infinity, or out-of-range
        // values that could reach the noise model or OSRM URL builder.
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

        // Start overlay service if permission is granted
        if (android.provider.Settings.canDrawOverlays(this)) {
            startService(FloatingBubbleService.showIntent(this))
        }

        startForeground(NOTIFICATION_ID, buildNotification(profile.name))
        startSimulation(profile, startLat, startLng, endLat, endLng, frequencyHz, resumeState = null)

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // Stop overlay service when simulation stops
        val overlayIntent = Intent(this, FloatingBubbleService::class.java)
        stopService(overlayIntent)

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
        resumeState: SimulationState.Paused? = null,
    ) {
        simulationJob?.cancel()
        if (resumeState == null) {
            repository.emitRoute(null)
        }

        // Fix: frequency is already validated above — division is safe
        val intervalMs   = 1000L / frequencyHz
        val deltaTimeSec = 1.0 / frequencyHz

        simulationJob = lifecycleScope.launch {
            try {
                // ── 1. Fetch street route ────────────────────────────────────
                val route = if (resumeState != null && repository.route.value != null) {
                    repository.route.value!!
                } else {
                    repository.emitState(SimulationState.FetchingRoute(profile.name))
                    val newRoute = osrmRouteProvider
                        .fetchRoute(startLat, startLng, endLat, endLng, profile)
                        .getOrElse { error ->
                            Log.w(TAG, LogSanitizer.sanitizeString("OSRM fetch failed — using straight-line fallback: ${error.message}"))
                            osrmRouteProvider.fallbackRoute(startLat, startLng, endLat, endLng)
                        }
                    repository.emitRoute(newRoute)

                    // ── 2. Pre-simulation trajectory validation ──────────────────
                    val validation = trajectoryValidator.validate(newRoute, profile)
                    if (!validation.isValid) {
                        Log.w(TAG, LogSanitizer.sanitizeString("Trajectory validation warnings: ${validation.warnings.joinToString("; ")}"))
                        // Warnings are non-fatal — simulation proceeds but issues are logged
                    }
                    newRoute
                }

                // ── 3. Build interpolation stack ─────────────────────────────
                val interpolator = RouteInterpolator(route)
                val speedCtrl    = SpeedController(profile)
                val kalmanLat    = KalmanFilter1D()
                val kalmanLng    = KalmanFilter1D()

                // Fix (🔴): Assign to a local val before the loop.
                val activeNoiseModel = LayeredNoiseModel.fromProfile(profile)

                // ── 4. Register mock provider ────────────────────────────────
                mockLocationInjector.registerProvider()

                var distanceTravelled = if (resumeState != null) (resumeState.progressPercent / 100.0) * interpolator.totalDistanceMeters else 0.0
                var frameCount        = 0L
                val timeOffsetMs      = if (resumeState != null) resumeState.elapsedTimeSec * 1000L else 0L
                val startTimeMs       = System.currentTimeMillis() - timeOffsetMs

                // ── 5. Main simulation loop ──────────────────────────────────
                // Add a variable to track the manual position
                var currentManualLat = if (resumeState != null) resumeState.lastLocation.lat else startLat
                var currentManualLng = if (resumeState != null) resumeState.lastLocation.lng else startLng

                while (isActive && (repository.isManualMode.value || distanceTravelled <= interpolator.totalDistanceMeters)) {

                    val frameTimeMs  = System.currentTimeMillis()
                    val frameNanos   = SystemClock.elapsedRealtimeNanos()

                    val isManual = repository.isManualMode.value
                    
                    val rawLocation = if (isManual) {
                        val joyState = repository.joystickState.value
                        val metersThisFrame = profile.maxSpeedMs * joyState.magnitude * deltaTimeSec
                        
                        // Advance position manually using great-circle approximation
                        if (joyState.magnitude > 0.01f) {
                            val R = 6378137.0 // Earth radius in meters
                            val latRad = Math.toRadians(currentManualLat)
                            val lngRad = Math.toRadians(currentManualLng)
                            val bearingRad = Math.toRadians(joyState.angle.toDouble())

                            val newLatRad = asin(sin(latRad) * cos(metersThisFrame / R) +
                                    cos(latRad) * sin(metersThisFrame / R) * cos(bearingRad))
                            val newLngRad = lngRad + atan2(sin(bearingRad) * sin(metersThisFrame / R) * cos(latRad),
                                    cos(metersThisFrame / R) - sin(latRad) * sin(newLatRad))

                            currentManualLat = Math.toDegrees(newLatRad)
                            currentManualLng = Math.toDegrees(newLngRad)
                        }

                        val smoothLat = kalmanLat.update(currentManualLat, deltaTimeSec)
                        val smoothLng = kalmanLng.update(currentManualLng, deltaTimeSec)

                        MockLocation(
                            lat                  = smoothLat,
                            lng                  = smoothLng,
                            altitude             = estimateAltitude(route, 0.0), // No route progress in manual
                            speed                = (profile.maxSpeedMs * joyState.magnitude).toFloat(),
                            bearing              = joyState.angle,
                            accuracy             = 10f,
                            elapsedRealtimeNanos = frameNanos,
                            timestampMs          = frameTimeMs,
                        )
                    } else {
                        val frame = interpolator.positionAt(distanceTravelled)
                        val distToEnd = (interpolator.totalDistanceMeters - distanceTravelled).coerceAtLeast(0.0)
                        val metersThisFrame = speedCtrl.advance(deltaTimeSec, distToEnd)
                        
                        // Update manual coords so switching from auto to manual starts where we left off
                        currentManualLat = frame.lat
                        currentManualLng = frame.lng

                        val smoothLat = kalmanLat.update(frame.lat, deltaTimeSec)
                        val smoothLng = kalmanLng.update(frame.lng, deltaTimeSec)

                        distanceTravelled += metersThisFrame

                        MockLocation(
                            lat                  = smoothLat,
                            lng                  = smoothLng,
                            altitude             = estimateAltitude(route, frame.progress),
                            speed                = speedCtrl.currentSpeedMs.toFloat(),
                            bearing              = frame.bearing,
                            accuracy             = 10f,
                            elapsedRealtimeNanos = frameNanos,
                            timestampMs          = frameTimeMs,
                        )
                    }

                    val noisyLocation = activeNoiseModel.applyToLocation(rawLocation, deltaTimeSec)

                    mockLocationInjector.inject(noisyLocation)

                    val progress = if (isManual) -1f else ((distanceTravelled / interpolator.totalDistanceMeters) * 100.0).toFloat().coerceIn(0f, 100f)

                    repository.emitState(SimulationState.Running(
                        currentLocation  = noisyLocation,
                        profileName      = profile.name,
                        progressPercent  = progress,
                        elapsedTimeSec   = (frameTimeMs - startTimeMs) / 1000L,
                        frameCount       = frameCount,
                    ))

                    frameCount++
                    delay(intervalMs)
                }

                // ── 6. Normal completion ─────────────────────────────────────
                repository.reset()
                stopSelf()

            } catch (e: Exception) {
                // CancellationException = cancelamento normal (usuário parou, service destruído).
                // Relançar permite que o framework de coroutines faça o cleanup correto
                // e evita o log E/SimulationService: "Simulation error" espúrio.
                if (e is kotlinx.coroutines.CancellationException) throw e
            
                Log.e(TAG, "Simulation error", e)
                repository.emitState(SimulationState.Error(
                    e.message ?: "Unknown simulation error"
                ))
                repository.emitRoute(null)
                stopSelf()
            }
        }
    }

    private fun pauseSimulation() {
        val currentState = repository.state.value
        if (currentState is SimulationState.Running) {
            simulationJob?.cancel()
            simulationJob = null
            
            repository.emitState(SimulationState.Paused(
                lastLocation    = currentState.currentLocation,
                profileName     = currentState.profileName,
                progressPercent = currentState.progressPercent,
                elapsedTimeSec  = currentState.elapsedTimeSec,
            ))
        }
    }

    private fun stopSimulation() {
        simulationJob?.cancel()
        simulationJob = null

        runCatching { mockLocationInjector.unregisterProvider() }

        repository.reset()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Estimates altitude along the route.
     *
     * Fix (🟢): Returns a neutral 0.0 instead of the Taubaté/SP plateau constant (760 m)
     * which was incorrect for any other city. Replace with Open-Elevation API or SRTM
     * tile lookup in a future sprint to provide accurate altitude interpolation.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun estimateAltitude(route: Route, progress: Double): Double = 0.0

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

    // ── Coordinate validation extensions ─────────────────────────────────────

    private fun Double.isValidLat(): Boolean =
        !isNaN() && !isInfinite() && this in -90.0..90.0

    private fun Double.isValidLng(): Boolean =
        !isNaN() && !isInfinite() && this in -180.0..180.0
}
