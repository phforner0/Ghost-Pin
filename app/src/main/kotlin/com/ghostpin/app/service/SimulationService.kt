package com.ghostpin.app.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.ghostpin.app.BuildConfig
import com.ghostpin.app.GhostPinApp
import com.ghostpin.app.R
import com.ghostpin.app.location.MockLocationInjector
import com.ghostpin.app.routing.OsrmRouteProvider
import com.ghostpin.app.ui.MainActivity
import com.ghostpin.core.model.MockLocation
import com.ghostpin.core.model.MovementProfile
import com.ghostpin.core.model.Route
import com.ghostpin.engine.interpolation.KalmanFilter1D
import com.ghostpin.engine.interpolation.RouteInterpolator
import com.ghostpin.engine.interpolation.SpeedController
import com.ghostpin.engine.noise.LayeredNoiseModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service running the GPS simulation loop.
 *
 * Sprint 2 changes vs. original:
 *  - State moved to companion [sharedState] so the ViewModel can observe it without binding
 *  - OSRM route fetch before loop starts (with straight-line fallback)
 *  - [RouteInterpolator] (Catmull-Rom / linear) replaces hardcoded linear lerp
 *  - [SpeedController] for realistic acceleration; speed is profile-correct, not 50 % fixed
 *  - [KalmanFilter1D] smooths interpolated positions before noise is applied
 *  - Bearing is computed from the actual travel direction, not hardcoded 180 °
 *  - Default frequency raised to 5 Hz for smooth map animation
 *  - [sharedRoute] companion exposes the fetched route so the map can draw the full polyline
 */
@AndroidEntryPoint
class SimulationService : LifecycleService() {

    @Inject lateinit var mockLocationInjector: MockLocationInjector
    @Inject lateinit var osrmRouteProvider: OsrmRouteProvider

    private var noiseModel:    LayeredNoiseModel? = null
    private var simulationJob: Job?               = null

    // ── Companion: shared state observable by ViewModel without binding ──────
    companion object {
        const val EXTRA_PROFILE_NAME  = "profile_name"
        const val EXTRA_START_LAT     = "start_lat"
        const val EXTRA_START_LNG     = "start_lng"
        const val EXTRA_END_LAT       = "end_lat"
        const val EXTRA_END_LNG       = "end_lng"
        const val EXTRA_FREQUENCY_HZ  = "frequency_hz"
        const val ACTION_STOP         = "com.ghostpin.action.STOP"
        const val NOTIFICATION_ID     = 1001
        const val DEFAULT_FREQUENCY   = 5  // Hz — smooth map animation

        private val _sharedState = MutableStateFlow<SimulationState>(SimulationState.Idle)
        /** The current simulation state. Observed by [com.ghostpin.app.ui.SimulationViewModel]. */
        val sharedState: StateFlow<SimulationState> = _sharedState.asStateFlow()

        private val _sharedRoute = MutableStateFlow<Route?>(null)
        /**
         * The fetched (OSRM or fallback) route. Exposed so the map can draw the full
         * street-snapped polyline even before/after the simulation loop runs.
         */
        val sharedRoute: StateFlow<Route?> = _sharedRoute.asStateFlow()
    }

    // ── Service lifecycle ────────────────────────────────────────────────────

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent?.action == ACTION_STOP) {
            stopSimulation()
            return START_NOT_STICKY
        }

        if (!BuildConfig.MOCK_PROVIDER_ENABLED) {
            _sharedState.value = SimulationState.Error(
                "Mock provider not available in this build. Enable Developer Options → Mock location app."
            )
            stopSelf()
            return START_NOT_STICKY
        }

        val profileName  = intent?.getStringExtra(EXTRA_PROFILE_NAME)         ?: "Pedestrian"
        val startLat     = intent?.getDoubleExtra(EXTRA_START_LAT,  -23.5505) ?: -23.5505
        val startLng     = intent?.getDoubleExtra(EXTRA_START_LNG,  -46.6333) ?: -46.6333
        val endLat       = intent?.getDoubleExtra(EXTRA_END_LAT,    -23.5510) ?: -23.5510
        val endLng       = intent?.getDoubleExtra(EXTRA_END_LNG,    -46.6340) ?: -46.6340
        val frequencyHz  = intent?.getIntExtra   (EXTRA_FREQUENCY_HZ, DEFAULT_FREQUENCY) ?: DEFAULT_FREQUENCY

        val profile = MovementProfile.BUILT_IN[profileName] ?: MovementProfile.PEDESTRIAN

        startForeground(NOTIFICATION_ID, buildNotification(profile.name))
        startSimulation(profile, startLat, startLng, endLat, endLng, frequencyHz)

        return START_STICKY
    }

    override fun onDestroy() {
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
    ) {
        simulationJob?.cancel()
        noiseModel?.reset()
        _sharedRoute.value = null

        val intervalMs   = 1000L / frequencyHz
        val deltaTimeSec = 1.0 / frequencyHz

        simulationJob = lifecycleScope.launch {
            try {
                // ── 1. Fetch street route ────────────────────────────────────
                _sharedState.value = SimulationState.FetchingRoute(profile.name)

                val route = osrmRouteProvider
                    .fetchRoute(startLat, startLng, endLat, endLng, profile)
                    .getOrElse {
                        // Network failure → graceful fallback to straight line
                        osrmRouteProvider.fallbackRoute(startLat, startLng, endLat, endLng)
                    }

                _sharedRoute.value = route

                // ── 2. Build interpolation stack ─────────────────────────────
                val interpolator   = RouteInterpolator(route)
                val speedCtrl      = SpeedController(profile)
                val kalmanLat      = KalmanFilter1D()
                val kalmanLng      = KalmanFilter1D()
                noiseModel         = LayeredNoiseModel.fromProfile(profile)

                // ── 3. Register mock provider ────────────────────────────────
                mockLocationInjector.registerProvider()

                var distanceTravelled = 0.0
                var frameCount        = 0L
                val startTimeMs       = System.currentTimeMillis()

                // ── 4. Main simulation loop ──────────────────────────────────
                while (isActive && distanceTravelled <= interpolator.totalDistanceMeters) {

                    val frame = interpolator.positionAt(distanceTravelled)

                    // Distance remaining to end of route (for final slowdown)
                    val distToEnd = (interpolator.totalDistanceMeters - distanceTravelled)
                        .coerceAtLeast(0.0)

                    // Advance speed controller and get metres to move this frame
                    val metersThisFrame = speedCtrl.advance(deltaTimeSec, distToEnd)

                    // Kalman-smooth the interpolated position
                    val smoothLat = kalmanLat.update(frame.lat, deltaTimeSec)
                    val smoothLng = kalmanLng.update(frame.lng, deltaTimeSec)

                    // Build the clean (pre-noise) location
                    val rawLocation = MockLocation(
                        lat                  = smoothLat,
                        lng                  = smoothLng,
                        altitude             = estimateAltitude(route, frame.progress),
                        speed                = speedCtrl.currentSpeedMs.toFloat(),
                        bearing              = frame.bearing,
                        accuracy             = 10f,
                        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
                        timestampMs          = System.currentTimeMillis(),
                    )

                    // Apply full noise pipeline (OU + multipath + tunnel + coherence)
                    val noisyLocation = noiseModel!!.applyToLocation(rawLocation, deltaTimeSec)

                    // Inject into Android LocationManager
                    mockLocationInjector.inject(noisyLocation)

                    // Emit state update
                    _sharedState.value = SimulationState.Running(
                        currentLocation  = noisyLocation,
                        profileName      = profile.name,
                        progressPercent  = (frame.progress * 100.0).toFloat(),
                        elapsedTimeSec   = (System.currentTimeMillis() - startTimeMs) / 1000L,
                        frameCount       = frameCount,
                    )

                    distanceTravelled += metersThisFrame
                    frameCount++
                    delay(intervalMs)
                }

                // ── 5. Normal completion ─────────────────────────────────────
                _sharedState.value = SimulationState.Idle
                _sharedRoute.value = null
                stopSelf()

            } catch (e: Exception) {
                _sharedState.value = SimulationState.Error(
                    e.message ?: "Unknown simulation error"
                )
                _sharedRoute.value = null
                stopSelf()
            }
        }
    }

    private fun stopSimulation() {
        simulationJob?.cancel()
        simulationJob = null
        noiseModel?.reset()
        noiseModel = null

        runCatching { mockLocationInjector.unregisterProvider() }

        _sharedState.value = SimulationState.Idle
        _sharedRoute.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Estimate altitude along the route using a simple flat assumption
     * (760 m — Taubaté/SP plateau default). Can be replaced with elevation
     * data in a future sprint.
     */
    private fun estimateAltitude(
        @Suppress("UNUSED_PARAMETER") route: Route,
        @Suppress("UNUSED_PARAMETER") progress: Double,
    ): Double = 760.0

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
}
