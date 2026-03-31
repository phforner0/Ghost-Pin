package com.ghostpin.app.service

import android.content.Intent
import com.ghostpin.app.data.SimulationConfig
import com.ghostpin.core.model.AppMode
import com.ghostpin.core.model.DefaultCoordinates
import com.ghostpin.core.model.MovementProfile
import com.ghostpin.core.model.Waypoint
import com.ghostpin.engine.interpolation.RepeatPolicy

internal data class SimulationStartRequest(
    val profile: MovementProfile,
    val startLat: Double,
    val startLng: Double,
    val endLat: Double,
    val endLng: Double,
    val frequencyHz: Int,
    val speedRatio: Double,
    val appMode: AppMode,
    val waypointPauseSec: Double,
    val repeatPolicy: RepeatPolicy,
    val repeatCount: Int,
    val waypoints: List<Waypoint>,
    val isResume: Boolean,
) {
    fun toSimulationConfig(routeId: String?): SimulationConfig = SimulationConfig(
        profileName = profile.name,
        startLat = startLat,
        startLng = startLng,
        endLat = endLat,
        endLng = endLng,
        routeId = routeId,
        appMode = appMode,
        waypoints = waypoints,
        waypointPauseSec = waypointPauseSec,
        speedRatio = speedRatio,
        frequencyHz = frequencyHz,
        repeatPolicy = repeatPolicy,
        repeatCount = repeatCount,
    )
}

internal fun parseSimulationStartRequest(
    intent: Intent,
    repository: com.ghostpin.app.data.SimulationRepository,
    resolveProfile: (String?) -> MovementProfile?,
    defaultFrequency: Int,
    minFrequency: Int,
    maxFrequency: Int,
): Result<SimulationStartRequest> {
    val modeName = intent.getStringExtra(SimulationService.EXTRA_MODE) ?: AppMode.CLASSIC.name
    val requestedMode = runCatching { AppMode.valueOf(modeName) }.getOrDefault(AppMode.CLASSIC)

    val startLatRaw = intent.getDoubleExtra(SimulationService.EXTRA_START_LAT, Double.NaN)
    val isResume = repository.state.value is SimulationState.Paused && startLatRaw.isNaN()
    val appMode = if (isResume && repository.isManualMode.value) AppMode.JOYSTICK else requestedMode

    val frequencyHz = intent.getIntExtra(SimulationService.EXTRA_FREQUENCY_HZ, defaultFrequency)
        .coerceIn(minFrequency, maxFrequency)
    val speedRatio = intent.getDoubleExtra(SimulationService.EXTRA_SPEED_RATIO, 1.0).coerceIn(0.0, 1.0)
    if (speedRatio <= 0.0) {
        return Result.failure(IllegalArgumentException("Speed ratio must be greater than 0 to start simulation."))
    }

    val waypointPauseSec = intent.getDoubleExtra(SimulationService.EXTRA_WAYPOINT_PAUSE_SEC, 0.0).coerceIn(0.0, 30.0)
    val repeatPolicy = runCatching {
        val raw = intent.getStringExtra(SimulationService.EXTRA_REPEAT_POLICY)
            ?: repository.lastUsedConfig.value?.repeatPolicy?.name
            ?: RepeatPolicy.NONE.name
        RepeatPolicy.valueOf(raw)
    }.getOrDefault(RepeatPolicy.NONE)
    val repeatCount = intent.getIntExtra(
        SimulationService.EXTRA_REPEAT_COUNT,
        repository.lastUsedConfig.value?.repeatCount ?: 1,
    ).coerceAtLeast(1)

    val profileName = intent.getStringExtra(SimulationService.EXTRA_PROFILE_NAME) ?: MovementProfile.PEDESTRIAN.name
    val profile = resolveProfile(profileName) ?: MovementProfile.PEDESTRIAN

    val startLat = startLatRaw.takeIf { it.isValidLat() } ?: DefaultCoordinates.START_LAT
    val startLng = intent.getDoubleExtra(SimulationService.EXTRA_START_LNG, DefaultCoordinates.START_LNG)
        .takeIf { it.isValidLng() } ?: DefaultCoordinates.START_LNG
    val endLat = intent.getDoubleExtra(SimulationService.EXTRA_END_LAT, DefaultCoordinates.END_LAT)
        .takeIf { it.isValidLat() } ?: DefaultCoordinates.END_LAT
    val endLng = intent.getDoubleExtra(SimulationService.EXTRA_END_LNG, DefaultCoordinates.END_LNG)
        .takeIf { it.isValidLng() } ?: DefaultCoordinates.END_LNG

    val waypointsLat = intent.getDoubleArrayExtra(SimulationService.EXTRA_WAYPOINTS_LAT)
    val waypointsLng = intent.getDoubleArrayExtra(SimulationService.EXTRA_WAYPOINTS_LNG)
    val waypoints = if (waypointsLat != null && waypointsLng != null && waypointsLat.size == waypointsLng.size) {
        waypointsLat.zip(waypointsLng)
            .filter { (lat, lng) -> lat.isValidLat() && lng.isValidLng() }
            .map { Waypoint(it.first, it.second) }
    } else {
        emptyList()
    }

    if (appMode == AppMode.WAYPOINTS && waypoints.size < 2) {
        return Result.failure(IllegalArgumentException("Add at least 2 waypoints to start multi-stop mode."))
    }

    return Result.success(
        SimulationStartRequest(
            profile = profile,
            startLat = startLat,
            startLng = startLng,
            endLat = endLat,
            endLng = endLng,
            frequencyHz = frequencyHz,
            speedRatio = speedRatio,
            appMode = appMode,
            waypointPauseSec = waypointPauseSec,
            repeatPolicy = repeatPolicy,
            repeatCount = repeatCount,
            waypoints = waypoints,
            isResume = isResume,
        )
    )
}

private fun Double.isValidLat(): Boolean = !isNaN() && !isInfinite() && this in -90.0..90.0
private fun Double.isValidLng(): Boolean = !isNaN() && !isInfinite() && this in -180.0..180.0
