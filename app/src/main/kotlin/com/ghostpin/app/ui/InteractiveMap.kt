package com.ghostpin.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ghostpin.app.service.SimulationState
import com.ghostpin.core.model.AppMode
import com.ghostpin.core.model.Route
import com.ghostpin.core.model.Waypoint
import org.maplibre.android.maps.MapView

// ── Map composable ────────────────────────────────────────────────────────────

/**
 * Renders the MapLibre map with route, pins and overlay indicators.
 *
 * Accepts plain data parameters instead of the ViewModel reference.
 */
@Composable
fun InteractiveMap(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double,
        waypoints: List<Waypoint>,
        appMode: AppMode,
        route: Route?,
        startPlaced: Boolean,
        simulationState: SimulationState,
        previewPlayhead: Waypoint? = null,
        deviceLocation: Pair<Double, Double>?,
        lowMemorySignal: Int = 0,
        onMapLongPress: (Double, Double) -> Unit,
        modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember { MapView(context) }
    var mapController by remember { mutableStateOf<MapController?>(null) }
    var hasCenteredOnDeviceLocation by remember { mutableStateOf(false) }

    // Manage MapView lifecycle (destroy only once, on disposal).
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapController = null
            mapView.onDestroy()
        }
    }

    // Center map on real device location once (avoid aggressive zoom reset on every GPS update).
    LaunchedEffect(deviceLocation, simulationState) {
        if (simulationState !is SimulationState.Idle) return@LaunchedEffect
        if (hasCenteredOnDeviceLocation) return@LaunchedEffect

        val loc = deviceLocation ?: return@LaunchedEffect
        val controller = mapController ?: return@LaunchedEffect
        controller.moveTo(loc.first, loc.second)
        hasCenteredOnDeviceLocation = true
    }

    val routeVisualState = when (simulationState) {
        is SimulationState.Idle -> "idle"
        is SimulationState.FetchingRoute -> "fetching"
        is SimulationState.Error -> "error"
        else -> "active"
    }

    // Update static route/pins only when the route model or route-related state changes.
    LaunchedEffect(routeVisualState, startLat, startLng, endLat, endLng, waypoints, appMode, route, previewPlayhead) {
        val controller = mapController ?: return@LaunchedEffect
        when (routeVisualState) {
            "idle", "fetching" -> {
                controller.clearPosition()
                val fetched = route
                if (fetched != null) controller.updateRoute(fetched)
                else if (appMode == AppMode.WAYPOINTS) controller.updateWaypoints(waypoints)
                else controller.updateRoute(startLat, startLng, endLat, endLng)
            }
            "error" -> controller.clearPosition()
            else -> {
                val fetched = route
                if (fetched != null) controller.updateRoute(fetched)
                else if (appMode == AppMode.WAYPOINTS) controller.updateWaypoints(waypoints)
                else controller.updateRoute(startLat, startLng, endLat, endLng)
            }
        }
        controller.updatePreviewPlayhead(previewPlayhead)
    }

    // Update the simulated position independently so route redraws do not fight the camera.
    LaunchedEffect(simulationState) {
        val controller = mapController ?: return@LaunchedEffect
        when (simulationState) {
            is SimulationState.Running -> controller.updatePosition(simulationState.currentLocation)
            is SimulationState.Paused -> controller.updatePosition(simulationState.lastLocation)
            else -> Unit
        }
    }

    // Hint text: tells user what the next long-press will do
    val hintText: String? =
            when {
                simulationState is SimulationState.Running -> null
                simulationState is SimulationState.FetchingRoute -> null
                !startPlaced -> "Long-press to set Start"
                else -> "Long-press to set End"
            }

    Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
    ) {
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                    factory = {
                        mapView.apply {
                            getMapAsync { mapLibreMap ->
                                mapController =
                                        MapController(mapLibreMap) { latLng ->
                                            onMapLongPress(latLng.latitude, latLng.longitude)
                                        }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
            )

            // Hint overlay (bottom-center)
            Column(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            ) {
                AnimatedVisibility(
                        visible = hintText != null,
                        enter = fadeIn(),
                        exit = fadeOut(),
                ) {
                    if (hintText != null) {
                        Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xCC1A1A2E),
                        ) {
                            Text(
                                    text = hintText,
                                    color = Color(0xFF80CBC4),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier =
                                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }

            // Loading overlay (top-end) — shown while OSRM fetches the route
            Column(
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            ) {
                AnimatedVisibility(
                        visible = simulationState is SimulationState.FetchingRoute,
                        enter = fadeIn(),
                        exit = fadeOut(),
                ) {
                    Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xCC1A1A2E),
                    ) {
                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = Color(0xFF80CBC4),
                                    strokeWidth = 2.dp,
                            )
                            Text("Fetching route…", color = Color(0xFFB0BEC5), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
