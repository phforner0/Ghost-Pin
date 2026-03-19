package com.ghostpin.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostpin.app.service.SimulationState
import com.ghostpin.app.ui.theme.GhostPinColors
import com.ghostpin.app.ui.theme.GhostPinTypography
import com.ghostpin.core.model.AppMode
import com.ghostpin.core.model.MovementProfile
import kotlinx.coroutines.launch

// ── Theme ────────────────────────────────────────────────────────────────────

@Composable
fun GhostPinTheme(content: @Composable () -> Unit) {
    MaterialTheme(
            colorScheme =
                    darkColorScheme(
                            primary = GhostPinColors.Primary,
                            onPrimary = GhostPinColors.OnPrimary,
                            primaryContainer = GhostPinColors.PrimaryDark,
                            secondary = GhostPinColors.TextSecondary,
                            surface = GhostPinColors.Background,
                            onSurface = GhostPinColors.TextPrimary,
                            background = GhostPinColors.BackgroundDeep,
                            onBackground = GhostPinColors.TextPrimary,
                            error = GhostPinColors.Error,
                    ),
            typography = GhostPinTypography,
            content = content,
    )
}

// ── Main Screen ──────────────────────────────────────────────────────────────

/**
 * Root composable that collects all ViewModels StateFlows and distributes them as plain values to
 * child composables.
 *
 * Fix (🟡): Child composables (InteractiveMap, SimulationStatusCard, ProfileSelector) now receive
 * data + callbacks instead of the ViewModel directly. This makes them independently previewable,
 * testable, and reusable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GhostPinScreen(
        viewModel: SimulationViewModel,
        permissionMessage: String?,
        onPermissionMessageDismissed: () -> Unit,
        onStartSimulation: (MovementProfile, Double) -> Unit,
        onStopSimulation: () -> Unit,
        onPickGpxFile: () -> Unit = {},
        onNavigateToRouteEditor: () -> Unit = {},
        onNavigateToHistory: () -> Unit = {},
) {
    val selectedProfile by viewModel.selectedProfile.collectAsState()
    val selectedMode by viewModel.selectedMode.collectAsState()
    val simulationState by viewModel.simulationState.collectAsState()
    val isBusy by viewModel.isBusy.collectAsState()
    val startLat by viewModel.startLat.collectAsState()
    val startLng by viewModel.startLng.collectAsState()
    val endLat by viewModel.endLat.collectAsState()
    val endLng by viewModel.endLng.collectAsState()
    val route by viewModel.route.collectAsState()
    val startPlaced by viewModel.startPlaced.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val deviceLocation by viewModel.deviceLocation.collectAsState()

    // Show Snackbar when permissions are denied
    LaunchedEffect(permissionMessage) {
        if (permissionMessage != null) {
            coroutineScope.launch {
                val result = snackbarHostState.showSnackbar(
                        message = permissionMessage,
                        actionLabel = "Open settings",
                        duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    context.startActivity(
                        Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }
                onPermissionMessageDismissed()
            }
        }
    }

    // Return map to the actual physical location whenever simulation is fully stopped
    LaunchedEffect(simulationState) {
        if (simulationState is SimulationState.Idle) {
            viewModel.initializeLocation(context)
        }
    }

    Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                        title = {
                            Text("GhostPin", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        },
                        colors =
                                TopAppBarDefaults.topAppBarColors(
                                        containerColor = Color.Transparent,
                                        titleContentColor = Color(0xFF80CBC4),
                                ),
                        actions = {
                            IconButton(onClick = onNavigateToHistory) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = "History",
                                    tint = Color(0xFF80CBC4),
                                )
                            }
                            IconButton(onClick = onNavigateToRouteEditor) {
                                Icon(
                                    imageVector = Icons.Default.EditNote,
                                    contentDescription = "Route Editor",
                                    tint = Color(0xFF80CBC4),
                                )
                            }
                        },
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                        onClick = {
                            if (isBusy) onStopSimulation() else onStartSimulation(selectedProfile, 0.0)
                        },
                        containerColor = if (isBusy) Color(0xFFCF6679) else Color(0xFF80CBC4),
                        contentColor = Color(0xFF003734),
                        icon = {
                            Icon(
                                    imageVector =
                                            if (isBusy) Icons.Default.Stop
                                            else Icons.Default.PlayArrow,
                                    contentDescription = if (isBusy) "Stop simulation" else "Start simulation",
                            )
                        },
                        text = {
                            Text(if (isBusy) "Stop" else "Start", fontWeight = FontWeight.Bold)
                        },
                )
            },
            bottomBar = {
                NavigationBar(
                        containerColor = Color(0xFF1E1E2E),
                        contentColor = Color(0xFF80CBC4)
                ) {
                    AppMode.entries.forEach { mode ->
                        NavigationBarItem(
                                selected = selectedMode == mode,
                                onClick = { viewModel.setAppMode(mode) },
                                icon = {
                                    when (mode) {
                                        AppMode.CLASSIC ->
                                                Icon(
                                                        Icons.Default.LocationOn,
                                                        contentDescription = "Classic mode"
                                                )
                                        AppMode.JOYSTICK ->
                                                Icon(
                                                        Icons.Default.Gamepad,
                                                        contentDescription = "Joystick mode"
                                                )
                                        AppMode.WAYPOINTS ->
                                                Icon(Icons.Default.Map, contentDescription = "Waypoints mode")
                                        AppMode.GPX ->
                                                Icon(
                                                        Icons.Default.Folder,
                                                        contentDescription = "GPX mode"
                                                )
                                    }
                                },
                                label = { Text(mode.displayName, fontSize = 12.sp) },
                                colors =
                                        NavigationBarItemDefaults.colors(
                                                selectedIconColor = Color(0xFF003734),
                                                selectedTextColor = Color(0xFF80CBC4),
                                                indicatorColor = Color(0xFF80CBC4),
                                                unselectedIconColor = Color(0xFFB0BEC5),
                                                unselectedTextColor = Color(0xFFB0BEC5)
                                        )
                        )
                    }
                }
            },
            containerColor = Color(0xFF0A0A0A),
    ) { paddingValues ->
        Column(
                modifier =
                        Modifier.fillMaxSize()
                                .padding(paddingValues)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val waypoints by viewModel.waypoints.collectAsState()
            val geoSuggestions by viewModel.geoSuggestions.collectAsState()
            val isSearching by viewModel.isSearching.collectAsState()
            val routeEtaText by viewModel.routeEtaText.collectAsState()

            // Map with overlays — state hoisted; no viewModel reference inside
            InteractiveMap(
                    startLat = startLat,
                    startLng = startLng,
                    endLat = endLat,
                    endLng = endLng,
                    waypoints = waypoints,
                    appMode = selectedMode,
                    route = route,
                    startPlaced = startPlaced,
                    simulationState = simulationState,
                    deviceLocation = deviceLocation,
                    onMapLongPress = viewModel::onMapLongPress,
                    modifier = Modifier.fillMaxWidth().weight(1f),
            )

            SimulationStatusCard(state = simulationState, etaText = routeEtaText)

            // OSRM fetch progress indicator
            if (simulationState is SimulationState.FetchingRoute) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF80CBC4),
                    trackColor = Color(0xFF1E1E2E),
                )
            }

            // Animated mode panel transitions
            AnimatedContent(
                targetState = selectedMode,
                transitionSpec = {
                    (fadeIn() + slideInVertically { it / 4 }) togetherWith
                            (fadeOut() + slideOutVertically { -it / 4 })
                },
                label = "mode_panel",
            ) { mode ->
                when (mode) {
                    AppMode.CLASSIC -> {
                        ClassicModePanel(
                                profiles = viewModel.profiles,
                                selectedProfile = selectedProfile,
                                enabled = !isBusy,
                                onSelect = viewModel::selectProfile,
                        )
                    }
                    AppMode.JOYSTICK -> JoystickModePanel()
                    AppMode.WAYPOINTS -> {
                        WaypointsModePanel(
                            waypoints = waypoints,
                            onRemoveWaypoint = viewModel::removeWaypoint,
                            onClearWaypoints = viewModel::clearWaypoints,
                            profiles = viewModel.profiles,
                            selectedProfile = selectedProfile,
                            enabled = !isBusy,
                            onSelectProfile = viewModel::selectProfile,
                            geoSuggestions = geoSuggestions,
                            isSearching = isSearching,
                            onSearchAddress = viewModel::searchAddress,
                            onSelectSuggestion = viewModel::addWaypointFromGeoResult,
                            onClearSuggestions = viewModel::clearSuggestions,
                            onStart = { pauseSec -> onStartSimulation(selectedProfile, pauseSec) }
                        )
                    }
                    AppMode.GPX -> {
                        val gpxLoadState by viewModel.gpxLoadState.collectAsState()
                        GpxModePanel(
                                gpxLoadState = gpxLoadState,
                                onPickFile = onPickGpxFile,
                                onClearRoute = viewModel::clearGpxRoute,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp)) // FAB clearance inside scaffold
        }
    }
}
