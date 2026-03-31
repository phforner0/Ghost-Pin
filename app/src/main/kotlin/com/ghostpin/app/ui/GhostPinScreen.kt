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
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostpin.app.service.SimulationState
import com.ghostpin.app.ui.theme.GhostPinColors
import com.ghostpin.app.ui.theme.GhostPinTypography
import com.ghostpin.app.ui.theme.panelBackground
import com.ghostpin.app.ui.theme.statusError
import com.ghostpin.app.ui.theme.statusSuccess
import com.ghostpin.app.ui.theme.statusWarning
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
                onPrimaryContainer = GhostPinColors.Primary,
                secondary = GhostPinColors.TextSecondary,
                tertiary = GhostPinColors.Warning,
                surface = GhostPinColors.Background,
                surfaceVariant = GhostPinColors.SurfaceVariant,
                onSurface = GhostPinColors.TextPrimary,
                background = GhostPinColors.BackgroundDeep,
                onBackground = GhostPinColors.TextPrimary,
                error = GhostPinColors.Error,
                errorContainer = GhostPinColors.ErrorContainer,
                onErrorContainer = GhostPinColors.Error,
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
    lowMemorySignal: Int = 0,
    onPermissionMessageDismissed: () -> Unit,
    onStartSimulation: (MovementProfile, Double) -> Unit,
    onStopSimulation: () -> Unit,
    onPickGpxFile: () -> Unit = {},
    onNavigateToRouteEditor: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onNavigateToSchedule: () -> Unit = {},
    onNavigateToProfiles: () -> Unit = {},
) {
    val selectedProfile by viewModel.selectedProfile.collectAsState()
    val selectedMode by viewModel.selectedMode.collectAsState()
    val canStartCurrentMode by viewModel.canStartCurrentMode.collectAsState()
    val isBusy by viewModel.isBusy.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    val sheetPeekHeight =
        when {
            configuration.screenWidthDp < 600 -> 156.dp // compacto
            configuration.screenWidthDp < 840 -> 192.dp // normal
            else -> 232.dp // tablet
        }

    // Show Snackbar when permissions are denied
    LaunchedEffect(permissionMessage) {
        if (permissionMessage != null) {
            coroutineScope.launch {
                val result =
                    snackbarHostState.showSnackbar(
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

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val canStartFromFab =
        when (selectedMode) {
            AppMode.WAYPOINTS -> false
            else -> canStartCurrentMode
        }
    val showGlobalFab = isBusy || selectedMode != AppMode.WAYPOINTS

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets =
            WindowInsets.safeDrawing.only(
                WindowInsetsSides.Top + WindowInsetsSides.Horizontal
            ),
        topBar = {
            TopAppBar(
                title = {
                    Text("GhostPin", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0f),
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    ),
                actions = {
                    GhostPinTopBarActions(
                        viewModel = viewModel,
                        onNavigateToHistory = onNavigateToHistory,
                        onNavigateToSchedule = onNavigateToSchedule,
                        onNavigateToRouteEditor = onNavigateToRouteEditor,
                        onNavigateToProfiles = onNavigateToProfiles,
                    )
                },
            )
        },
        floatingActionButton = {
            if (showGlobalFab) {
                val fabEnabled = isBusy || canStartFromFab
                ExtendedFloatingActionButton(
                    text = {
                        Text(if (isBusy) "Stop" else "Start", fontWeight = FontWeight.Bold)
                    },
                    icon = {
                        Icon(
                            imageVector = if (isBusy) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isBusy) "Stop simulation" else "Start simulation",
                        )
                    },
                    onClick = {
                        if (fabEnabled) {
                            if (isBusy) onStopSimulation() else onStartSimulation(selectedProfile, 0.0)
                        }
                    },
                    expanded = true,
                    containerColor =
                        if (fabEnabled) {
                            if (isBusy) MaterialTheme.colorScheme.statusError else MaterialTheme.colorScheme.statusSuccess
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    contentColor =
                        if (fabEnabled) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.panelBackground,
                contentColor = MaterialTheme.colorScheme.primary
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
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        BottomSheetScaffold(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            sheetPeekHeight = sheetPeekHeight,
            sheetDragHandle = { BottomSheetDefaults.DragHandle() },
            sheetContainerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            sheetContent = {
                GhostPinSheetContent(
                    viewModel = viewModel,
                    selectedMode = selectedMode,
                    enabled = !isBusy,
                    onStartSimulation = onStartSimulation,
                    onPickGpxFile = onPickGpxFile,
                )
            },
        ) { innerPadding ->
            GhostPinMapViewport(
                viewModel = viewModel,
                lowMemorySignal = lowMemorySignal,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun GhostPinTopBarActions(
    viewModel: SimulationViewModel,
    onNavigateToHistory: () -> Unit,
    onNavigateToSchedule: () -> Unit,
    onNavigateToRouteEditor: () -> Unit,
    onNavigateToProfiles: () -> Unit,
) {
    val favoriteSimulations by viewModel.favoriteSimulations.collectAsState()
    var favoritesExpanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { favoritesExpanded = true }) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Favorites",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        DropdownMenu(
            expanded = favoritesExpanded,
            onDismissRequest = { favoritesExpanded = false },
        ) {
            favoriteSimulations.forEach { favorite ->
                DropdownMenuItem(
                    text = { Text(favorite.name) },
                    onClick = {
                        favoritesExpanded = false
                        viewModel.applyFavoriteById(favorite.id)
                    }
                )
            }
            if (favoriteSimulations.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("Nenhum favorito salvo") },
                    onClick = { favoritesExpanded = false },
                )
            }
        }
    }
    IconButton(onClick = onNavigateToHistory) {
        Icon(Icons.Default.History, contentDescription = "History", tint = MaterialTheme.colorScheme.primary)
    }
    IconButton(onClick = onNavigateToSchedule) {
        Icon(Icons.Default.Schedule, contentDescription = "Schedule", tint = MaterialTheme.colorScheme.primary)
    }
    IconButton(onClick = onNavigateToRouteEditor) {
        Icon(Icons.Default.EditNote, contentDescription = "Route Editor", tint = MaterialTheme.colorScheme.primary)
    }
    IconButton(onClick = onNavigateToProfiles) {
        Icon(Icons.Default.Tune, contentDescription = "Profiles", tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun GhostPinSheetContent(
    viewModel: SimulationViewModel,
    selectedMode: AppMode,
    enabled: Boolean,
    onStartSimulation: (MovementProfile, Double) -> Unit,
    onPickGpxFile: () -> Unit,
) {
    val scaffoldInsets = ScaffoldDefaults.contentWindowInsets.asPaddingValues()
    val profiles by viewModel.profiles.collectAsState()
    val selectedProfile by viewModel.selectedProfile.collectAsState()
    val waypoints by viewModel.waypoints.collectAsState()
    val geoSuggestions by viewModel.geoSuggestions.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val repeatPolicy by viewModel.repeatPolicy.collectAsState()
    val repeatCount by viewModel.repeatCount.collectAsState()
    val gpxLoadState by viewModel.gpxLoadState.collectAsState()

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .padding(bottom = scaffoldInsets.calculateBottomPadding() + 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AnimatedContent(
            targetState = selectedMode,
            transitionSpec = {
                (fadeIn() + slideInVertically { it / 4 }) togetherWith
                    (fadeOut() + slideOutVertically { -it / 4 })
            },
            label = "mode_panel",
        ) { mode ->
            when (mode) {
                AppMode.CLASSIC ->
                    ClassicModePanel(
                        profiles = profiles,
                        selectedProfile = selectedProfile,
                        enabled = enabled,
                        onSelect = viewModel::selectProfile,
                        repeatPolicy = repeatPolicy,
                        repeatCount = repeatCount,
                        onRepeatPolicyChange = viewModel::setRepeatPolicy,
                        onRepeatCountChange = viewModel::setRepeatCount,
                    )

                AppMode.JOYSTICK -> JoystickModePanel()

                AppMode.WAYPOINTS ->
                    WaypointsModePanel(
                        waypoints = waypoints,
                        onRemoveWaypoint = viewModel::removeWaypoint,
                        onClearWaypoints = viewModel::clearWaypoints,
                        profiles = profiles,
                        selectedProfile = selectedProfile,
                        enabled = enabled,
                        onSelectProfile = viewModel::selectProfile,
                        geoSuggestions = geoSuggestions,
                        isSearching = isSearching,
                        onSearchAddress = viewModel::searchAddress,
                        onSelectSuggestion = viewModel::addWaypointFromGeoResult,
                        onClearSuggestions = viewModel::clearSuggestions,
                        onStart = { pauseSec -> onStartSimulation(selectedProfile, pauseSec) },
                        repeatPolicy = repeatPolicy,
                        repeatCount = repeatCount,
                        onRepeatPolicyChange = viewModel::setRepeatPolicy,
                        onRepeatCountChange = viewModel::setRepeatCount,
                    )

                AppMode.GPX ->
                    GpxModePanel(
                        gpxLoadState = gpxLoadState,
                        onPickFile = onPickGpxFile,
                        onClearRoute = viewModel::clearGpxRoute,
                    )
            }
        }

        Button(
            onClick = { viewModel.saveCurrentAsFavorite() },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
        ) {
            Text("Salvar como favorito")
        }
    }
}

@Composable
private fun GhostPinMapViewport(
    viewModel: SimulationViewModel,
    lowMemorySignal: Int,
    modifier: Modifier = Modifier,
) {
    val startLat by viewModel.startLat.collectAsState()
    val startLng by viewModel.startLng.collectAsState()
    val endLat by viewModel.endLat.collectAsState()
    val endLng by viewModel.endLng.collectAsState()
    val route by viewModel.route.collectAsState()
    val startPlaced by viewModel.startPlaced.collectAsState()
    val selectedMode by viewModel.selectedMode.collectAsState()
    val simulationState by viewModel.simulationState.collectAsState()
    val deviceLocation by viewModel.deviceLocation.collectAsState()
    val waypoints by viewModel.waypoints.collectAsState()
    val routeEtaText by viewModel.routeEtaText.collectAsState()

    Box(modifier = modifier) {
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
            lowMemorySignal = lowMemorySignal,
            onMapLongPress = viewModel::onMapLongPress,
            modifier = Modifier.fillMaxSize().heightIn(min = 280.dp),
        )

        SimulationStatusCard(
            state = simulationState,
            etaText = routeEtaText,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
        )

        if (simulationState is SimulationState.FetchingRoute) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(top = 92.dp),
                color = MaterialTheme.colorScheme.statusWarning,
                trackColor = MaterialTheme.colorScheme.panelBackground,
            )
        }
    }
}
