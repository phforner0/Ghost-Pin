package com.ghostpin.app.service

import android.util.Log
import com.ghostpin.app.data.RouteRepository
import com.ghostpin.app.data.SimulationRepository
import com.ghostpin.app.routing.OsrmRouteProvider
import com.ghostpin.core.model.AppMode
import com.ghostpin.core.model.MovementProfile
import com.ghostpin.core.model.Route
import com.ghostpin.core.model.Waypoint
import com.ghostpin.core.security.LogSanitizer
import kotlinx.coroutines.flow.first

internal data class SimulationRouteRequest(
    val profile: MovementProfile,
    val startLat: Double,
    val startLng: Double,
    val endLat: Double,
    val endLng: Double,
    val appMode: AppMode,
    val waypoints: List<Waypoint>,
    val resumeState: SimulationState.Paused?,
    val cachedRoute: Route?,
    val persistedRouteId: String?,
    val cachedConfigWaypoints: List<Waypoint>,
)

internal sealed class SimulationRouteResult {
    data class Success(val route: Route) : SimulationRouteResult()
    data class Error(val message: String) : SimulationRouteResult()
    data class Joystick(val startLat: Double, val startLng: Double) : SimulationRouteResult()
}

internal suspend fun resolveSimulationRoute(
    request: SimulationRouteRequest,
    routeRepository: RouteRepository,
    simulationRepository: SimulationRepository,
    osrmRouteProvider: OsrmRouteProvider,
    loggerTag: String,
): SimulationRouteResult {
    val persistedRoute = request.persistedRouteId?.let { routeRepository.getById(it) }
    if (request.persistedRouteId != null && persistedRoute == null) {
        return SimulationRouteResult.Error("Saved route not found for routeId '${request.persistedRouteId}'.")
    }

    return when {
        request.resumeState != null && request.cachedRoute != null -> SimulationRouteResult.Success(request.cachedRoute)

        persistedRoute != null -> {
            simulationRepository.emitRoute(persistedRoute)
            SimulationRouteResult.Success(persistedRoute)
        }

        request.appMode == AppMode.GPX -> {
            val preloaded = request.cachedRoute
                ?: simulationRepository.route.first { it != null }
                ?: request.cachedConfigWaypoints.takeIf { it.size >= 2 }?.let { savedWaypoints ->
                    Route(
                        id = request.persistedRouteId ?: "gpx-saved-route",
                        name = "Saved GPX Route",
                        waypoints = savedWaypoints,
                    )
                }

            if (preloaded == null) {
                SimulationRouteResult.Error("No GPX route loaded. Please select a .gpx file first.")
            } else {
                Log.d(loggerTag, LogSanitizer.sanitizeString(
                    "GPX mode — using pre-loaded route (${preloaded.waypoints.size} pts), skipping OSRM."
                ))
                SimulationRouteResult.Success(preloaded)
            }
        }

        request.appMode == AppMode.JOYSTICK -> SimulationRouteResult.Joystick(request.startLat, request.startLng)

        request.appMode == AppMode.WAYPOINTS && request.waypoints.size >= 2 -> {
            val newRoute = osrmRouteProvider.fetchMultiRoute(request.waypoints, request.profile).getOrElse { error ->
                Log.w(loggerTag, LogSanitizer.sanitizeString(
                    "OSRM multi-fetch failed — straight-line fallback: ${error.message}"
                ))
                osrmRouteProvider.fallbackMultiRoute(request.waypoints)
            }
            simulationRepository.emitRoute(newRoute)
            SimulationRouteResult.Success(newRoute)
        }

        else -> {
            val newRoute = osrmRouteProvider.fetchRoute(
                request.startLat,
                request.startLng,
                request.endLat,
                request.endLng,
                request.profile,
            ).getOrElse { error ->
                Log.w(loggerTag, LogSanitizer.sanitizeString(
                    "OSRM fetch failed — straight-line fallback: ${error.message}"
                ))
                osrmRouteProvider.fallbackRoute(request.startLat, request.startLng, request.endLat, request.endLng)
            }
            simulationRepository.emitRoute(newRoute)
            SimulationRouteResult.Success(newRoute)
        }
    }
}
