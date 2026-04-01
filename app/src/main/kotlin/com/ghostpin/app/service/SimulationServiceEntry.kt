package com.ghostpin.app.service

import android.content.Intent
import android.net.Uri
import com.ghostpin.app.data.SimulationConfig
import com.ghostpin.app.data.SimulationRepository
import com.ghostpin.core.model.AppMode
import com.ghostpin.core.model.DefaultCoordinates
import com.ghostpin.core.model.MovementProfile
import com.ghostpin.engine.interpolation.RepeatPolicy

internal sealed interface ImmediateServiceAction {
    data object NullIntentStop : ImmediateServiceAction

    data object StopSimulation : ImmediateServiceAction

    data object PauseSimulation : ImmediateServiceAction

    data class SetProfile(
        val profileName: String?,
    ) : ImmediateServiceAction

    data class SetRoute(
        val uri: Uri?,
    ) : ImmediateServiceAction

    data class SkipWaypoint(
        val delta: Int,
    ) : ImmediateServiceAction

    data object StartLastFavorite : ImmediateServiceAction

    data object StartLastConfig : ImmediateServiceAction

    data class StartSimulation(
        val intent: Intent,
    ) : ImmediateServiceAction
}

internal sealed interface ShortcutStartDecision {
    data class Start(
        val config: SimulationConfig,
    ) : ShortcutStartDecision

    data class ErrorAndStop(
        val message: String,
    ) : ShortcutStartDecision
}

internal fun classifyImmediateServiceAction(intent: Intent?): ImmediateServiceAction {
    if (intent == null) return ImmediateServiceAction.NullIntentStop

    return when (intent.action) {
        SimulationService.ACTION_STOP -> ImmediateServiceAction.StopSimulation
        SimulationService.ACTION_PAUSE -> ImmediateServiceAction.PauseSimulation
        SimulationService.ACTION_SET_PROFILE ->
            ImmediateServiceAction.SetProfile(
                intent.getStringExtra(SimulationService.EXTRA_PROFILE_NAME)
            )
        SimulationService.ACTION_SET_ROUTE -> ImmediateServiceAction.SetRoute(intent.data)
        SimulationService.ACTION_SKIP_NEXT_WAYPOINT -> ImmediateServiceAction.SkipWaypoint(delta = 1)
        SimulationService.ACTION_SKIP_PREV_WAYPOINT -> ImmediateServiceAction.SkipWaypoint(delta = -1)
        SimulationService.ACTION_START_LAST_FAVORITE -> ImmediateServiceAction.StartLastFavorite
        SimulationService.ACTION_START_LAST_CONFIG -> ImmediateServiceAction.StartLastConfig
        else -> ImmediateServiceAction.StartSimulation(intent)
    }
}

internal fun buildUpdatedProfileConfig(
    current: SimulationConfig?,
    resolvedProfile: MovementProfile,
    profileLookupKey: String,
    defaultFrequency: Int,
): SimulationConfig =
    SimulationConfig(
        profileName = resolvedProfile.name,
        profileLookupKey = profileLookupKey,
        startLat = current?.startLat ?: DefaultCoordinates.START_LAT,
        startLng = current?.startLng ?: DefaultCoordinates.START_LNG,
        endLat = current?.endLat ?: DefaultCoordinates.END_LAT,
        endLng = current?.endLng ?: DefaultCoordinates.END_LNG,
        routeId = current?.routeId,
        appMode = current?.appMode ?: AppMode.CLASSIC,
        waypoints = current?.waypoints ?: emptyList(),
        waypointPauseSec = current?.waypointPauseSec ?: 0.0,
        speedRatio = current?.speedRatio ?: 1.0,
        frequencyHz = current?.frequencyHz ?: defaultFrequency,
        repeatPolicy = current?.repeatPolicy ?: RepeatPolicy.NONE,
        repeatCount = current?.repeatCount ?: 1,
    )

internal fun resolveFavoriteShortcutDecision(resolution: SimulationRepository.FavoriteResolution,): ShortcutStartDecision =
    when (resolution) {
        is SimulationRepository.FavoriteResolution.Valid -> ShortcutStartDecision.Start(resolution.config)
        is SimulationRepository.FavoriteResolution.Invalid -> ShortcutStartDecision.ErrorAndStop(resolution.reason)
    }

internal fun resolveLastConfigShortcutDecision(
    currentConfig: SimulationConfig?,
    validation: SimulationRepository.ConfigValidation?,
): ShortcutStartDecision {
    if (currentConfig == null) {
        return ShortcutStartDecision.ErrorAndStop("No recent simulation configuration available.")
    }

    return when (validation) {
        is SimulationRepository.ConfigValidation.Valid -> ShortcutStartDecision.Start(validation.config)
        is SimulationRepository.ConfigValidation.Invalid -> ShortcutStartDecision.ErrorAndStop(validation.reason)
        null -> ShortcutStartDecision.ErrorAndStop("No recent simulation configuration available.")
    }
}
