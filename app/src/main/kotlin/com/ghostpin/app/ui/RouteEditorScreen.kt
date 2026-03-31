@file:Suppress("ktlint:standard:function-naming")

package com.ghostpin.app.ui

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ghostpin.app.routing.RouteFileParser
import com.ghostpin.app.ui.theme.GhostPinColors
import com.ghostpin.core.math.GeoMath
import com.ghostpin.core.model.Route
import com.ghostpin.core.model.Waypoint
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Route Editor full-screen composable.
 *
 * Sprint 4 — Task 17.
 *
 * Features implemented:
 *  - Waypoint list with add / remove / reorder affordances.
 *  - Per-segment override sheet (speed, pause, loop).
 *  - Route name editing.
 *  - Save to Room.
 *  - Export as GPX / KML / TCX.
 *  - Import from file URI via the shared route parser pipeline.
 *  - Saved routes list with load / delete.
 *
 * Note: Map integration (long-press to add waypoints directly on the map) is wired
 * through [RouteEditorViewModel.addWaypoint], called by the parent [MainActivity]
 * map controller. The editor screen itself shows the waypoint list and controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteEditorScreen(
    viewModel: RouteEditorViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onRouteReady: (com.ghostpin.core.model.Route) -> Unit = {},
    onImportRouteFile: () -> Unit = {},
    onExportRouteFile: (String, String) -> Unit = { _, _ -> },
    pendingImportedRouteUri: Uri? = null,
    onImportedRouteConsumed: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val savedRoutes by viewModel.savedRoutes.collectAsState(initial = emptyList())
    val context = LocalContext.current
    val activityManager =
        remember(context) {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        }
    val lowPerformanceDevice = remember(activityManager) { activityManager?.isLowRamDevice == true }
    val currentRoute =
        remember(state.waypoints, state.segmentOverrides, state.routeName, state.routeId) {
            viewModel.buildCurrentRoute()
        }
    val previewRoute =
        remember(currentRoute, lowPerformanceDevice) {
            currentRoute?.let { downsampleRouteForPreview(it, lowPerformanceDevice) }
        }
    var isPreviewPlaying by remember(previewRoute?.id) { mutableStateOf(false) }
    var previewProgress by remember(previewRoute?.id) { mutableFloatStateOf(0f) }
    var previewSpeed by remember(previewRoute?.id) { mutableFloatStateOf(1f) }
    val previewAltitude = remember(previewRoute) { previewRoute?.let { buildElevationProfile(it) } }
    val simplifiedPreview = lowPerformanceDevice || (currentRoute?.waypoints?.size ?: 0) > 1800

    var showSavedRoutes by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var editingSegmentIndex by remember { mutableIntStateOf(-1) }
    var showSegmentSheet by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }

    // Handle export trigger
    LaunchedEffect(state.exportContent) {
        state.exportContent?.let { (filename, content) ->
            onExportRouteFile(filename, content)
            viewModel.clearExport()
        }
    }

    LaunchedEffect(pendingImportedRouteUri) {
        pendingImportedRouteUri?.let { uri ->
            viewModel.importFromUri(context, uri)
            onImportedRouteConsumed()
        }
    }

    LaunchedEffect(isPreviewPlaying, previewRoute?.id, previewSpeed) {
        val route = previewRoute ?: return@LaunchedEffect
        if (!isPreviewPlaying) return@LaunchedEffect
        val maxIndex = (route.waypoints.size - 1).coerceAtLeast(1)
        while (isPreviewPlaying && previewProgress < 1f) {
            delay(16)
            val progressDelta = (previewSpeed * (1f / maxIndex) * 0.6f)
            previewProgress = (previewProgress + progressDelta).coerceIn(0f, 1f)
            if (previewProgress >= 1f) isPreviewPlaying = false
        }
    }

    Scaffold(
        containerColor = GhostPinColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.routeName,
                        color = GhostPinColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { showNameDialog = true },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = GhostPinColors.TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GhostPinColors.TopBar),
                actions = {
                    IconButton(onClick = onImportRouteFile) {
                        Icon(Icons.Default.FileOpen, null, tint = GhostPinColors.Primary)
                    }
                    // Export button
                    IconButton(onClick = { showExportMenu = true }) {
                        Icon(Icons.Default.Share, null, tint = GhostPinColors.Primary)
                    }
                    // Saved routes
                    IconButton(onClick = { showSavedRoutes = !showSavedRoutes }) {
                        Icon(Icons.Default.Folder, null, tint = GhostPinColors.Primary)
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar(containerColor = GhostPinColors.TopBar) {
                // Clear
                IconButton(onClick = { viewModel.clearRoute() }) {
                    Icon(Icons.Default.DeleteSweep, null, tint = GhostPinColors.Error)
                }
                Spacer(Modifier.weight(1f))
                // Use for simulation
                AnimatedVisibility(state.canSimulate) {
                    OutlinedButton(
                        onClick = {
                            viewModel.buildCurrentRoute()?.let { onRouteReady(it) }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GhostPinColors.Primary),
                        border =
                            ButtonDefaults.outlinedButtonBorder.copy(
                                brush =
                                    androidx.compose.ui.graphics
                                        .SolidColor(GhostPinColors.Primary)
                            ),
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text("Iniciar com esta rota")
                    }
                }
                // Save
                Button(
                    onClick = { viewModel.save() },
                    enabled = state.canSave && !state.isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = GhostPinColors.Primary),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = GhostPinColors.Background,
                        )
                    } else {
                        Text("Save", color = GhostPinColors.Background, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(8.dp))
            }
        }
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
        ) {
            // Error banner
            AnimatedVisibility(state.error != null, enter = fadeIn(), exit = fadeOut()) {
                state.error?.let { err ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = GhostPinColors.ErrorContainer),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            err,
                            color = GhostPinColors.Error,
                            modifier = Modifier.padding(12.dp),
                            fontSize = 13.sp,
                        )
                    }
                }
            }

            RoutePreviewCard(
                route = previewRoute,
                previewProgress = previewProgress,
                isPlaying = isPreviewPlaying,
                previewSpeed = previewSpeed,
                elevationProfile = previewAltitude,
                simplifiedPreview = simplifiedPreview,
                onSeek = { previewProgress = it.coerceIn(0f, 1f) },
                onPlayPause = {
                    if (previewProgress >= 1f) previewProgress = 0f
                    isPreviewPlaying = !isPreviewPlaying
                },
                onSpeedChange = { previewSpeed = it },
                onStartWithRoute = { currentRoute?.let(onRouteReady) },
            )

            // Section header
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Waypoints",
                    color = GhostPinColors.TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${state.waypoints.size} points",
                    color = GhostPinColors.TextTertiary,
                    fontSize = 12.sp,
                )
            }

            if (state.waypoints.isEmpty()) {
                // Empty state hint
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.TouchApp,
                            null,
                            tint = GhostPinColors.TextMuted,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Long-press on the map\nto add waypoints",
                            color = GhostPinColors.TextTertiary,
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            } else {
                // Waypoint list
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(state.waypoints) { index, waypoint ->
                        WaypointCard(
                            index = index,
                            waypoint = waypoint,
                            isLast = index == state.waypoints.lastIndex,
                            hasOverride = state.segmentOverrides.containsKey(index),
                            onRemove = { viewModel.removeWaypoint(index) },
                            onEditSegment = {
                                editingSegmentIndex = index
                                showSegmentSheet = true
                            },
                        )
                    }
                }
            }

            // Saved routes list (collapsible)
            AnimatedVisibility(showSavedRoutes) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Saved Routes",
                        color = GhostPinColors.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (savedRoutes.isEmpty()) {
                        Text("No saved routes yet.", color = GhostPinColors.TextTertiary, fontSize = 13.sp)
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 220.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(savedRoutes.size) { i ->
                                val route = savedRoutes[i]
                                SavedRouteCard(
                                    route = route,
                                    onLoad = {
                                        viewModel.loadRoute(route.id)
                                        showSavedRoutes = false
                                    },
                                    onDelete = { viewModel.deleteRoute(route.id) },
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Export Menu ──────────────────────────────────────────────────────
        if (showExportMenu) {
            AlertDialog(
                onDismissRequest = { showExportMenu = false },
                title = { Text("Export Route", color = GhostPinColors.TextPrimary) },
                text = {
                    Column {
                        listOf(
                            RouteFileParser.RouteFormat.GPX to "GPX 1.1 (universal)",
                            RouteFileParser.RouteFormat.KML to "KML (Google Maps/Earth)",
                            RouteFileParser.RouteFormat.TCX to "TCX (Garmin/Fitness)",
                        ).forEach { (format, label) ->
                            TextButton(
                                onClick = {
                                    viewModel.prepareExport(format)
                                    showExportMenu = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(label, color = GhostPinColors.Primary)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showExportMenu = false }) {
                        Text("Cancel", color = GhostPinColors.TextSecondary)
                    }
                },
                containerColor = GhostPinColors.Surface,
            )
        }

        // ── Segment Override Sheet ────────────────────────────────────────────
        if (showSegmentSheet && editingSegmentIndex >= 0) {
            val segIndex = editingSegmentIndex
            val currentOverride = state.segmentOverrides[segIndex]
            SegmentOverrideSheet(
                segmentIndex = segIndex,
                initialSpeed = currentOverride?.speedOverrideMs,
                initialPause = currentOverride?.pauseDurationSec,
                initialLoop = currentOverride?.loop ?: false,
                onDismiss = { showSegmentSheet = false },
                onApply = { speed, pause, loop ->
                    viewModel.setSegmentOverride(segIndex, speed, pause, loop)
                    showSegmentSheet = false
                },
                onClear = {
                    viewModel.clearSegmentOverride(segIndex)
                    showSegmentSheet = false
                },
            )
        }

        // ── Route Name Dialog ─────────────────────────────────────────────────
        if (showNameDialog) {
            var nameInput by remember { mutableStateOf(state.routeName) }
            AlertDialog(
                onDismissRequest = { showNameDialog = false },
                title = { Text("Route Name", color = GhostPinColors.TextPrimary) },
                text = {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        singleLine = true,
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GhostPinColors.Primary,
                                unfocusedBorderColor = GhostPinColors.TextMuted,
                                focusedTextColor = GhostPinColors.TextPrimary,
                                unfocusedTextColor = GhostPinColors.TextPrimary,
                            ),
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.setRouteName(nameInput)
                        showNameDialog = false
                    }) { Text("OK", color = GhostPinColors.Primary) }
                },
                dismissButton = {
                    TextButton(onClick = { showNameDialog = false }) {
                        Text("Cancel", color = GhostPinColors.TextSecondary)
                    }
                },
                containerColor = GhostPinColors.Surface,
            )
        }
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────

private data class ElevationProfile(
    val values: List<Float>,
    val isEstimated: Boolean,
)

@Composable
private fun RoutePreviewCard(
    route: Route?,
    previewProgress: Float,
    isPlaying: Boolean,
    previewSpeed: Float,
    elevationProfile: ElevationProfile?,
    simplifiedPreview: Boolean,
    onSeek: (Float) -> Unit,
    onPlayPause: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onStartWithRoute: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = GhostPinColors.Surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Preview da rota",
                    color = GhostPinColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                if (simplifiedPreview) {
                    Text(
                        "Modo simplificado",
                        color = GhostPinColors.Warning,
                        fontSize = 11.sp,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            RoutePathPreview(
                route = route,
                progress = previewProgress,
                height = if (simplifiedPreview) 110.dp else 150.dp,
            )

            elevationProfile?.let {
                Spacer(Modifier.height(8.dp))
                ElevationMiniGraph(
                    profile = it,
                    progress = previewProgress,
                    height = if (simplifiedPreview) 52.dp else 72.dp,
                )
            }

            Slider(
                value = previewProgress,
                onValueChange = {
                    onSeek(it)
                },
                enabled = route != null && route.waypoints.size >= 2,
                valueRange = 0f..1f,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalIconButton(
                    onClick = onPlayPause,
                    enabled = route != null && route.waypoints.size >= 2,
                ) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                }
                listOf(1f, 2f, 4f).forEach { speed ->
                    FilterChip(
                        selected = previewSpeed == speed,
                        onClick = { onSpeedChange(speed) },
                        label = { Text("${speed.toInt()}x") },
                    )
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onStartWithRoute,
                    enabled = route != null && route.waypoints.size >= 2,
                    colors = ButtonDefaults.buttonColors(containerColor = GhostPinColors.Primary),
                ) {
                    Text("Iniciar com esta rota", color = GhostPinColors.Background)
                }
            }
        }
    }
}

@Composable
private fun RoutePathPreview(
    route: Route?,
    progress: Float,
    height: Dp
) {
    Canvas(
        modifier =
            Modifier.fillMaxWidth().height(height).background(
                color = GhostPinColors.SurfaceVariant,
                shape = RoundedCornerShape(8.dp),
            )
    ) {
        val points = route?.waypoints.orEmpty()
        if (points.size < 2) return@Canvas
        val minLat = points.minOf { it.lat }
        val maxLat = points.maxOf { it.lat }
        val minLng = points.minOf { it.lng }
        val maxLng = points.maxOf { it.lng }
        val latRange = (maxLat - minLat).takeIf { it > 1e-7 } ?: 1e-7
        val lngRange = (maxLng - minLng).takeIf { it > 1e-7 } ?: 1e-7
        val padding = 20f
        val mapped =
            points.map {
                val x = ((it.lng - minLng) / lngRange).toFloat() * (size.width - 2 * padding) + padding
                val y = ((maxLat - it.lat) / latRange).toFloat() * (size.height - 2 * padding) + padding
                Offset(x, y)
            }
        val path =
            Path().apply {
                moveTo(mapped.first().x, mapped.first().y)
                mapped.drop(1).forEach { lineTo(it.x, it.y) }
            }
        drawPath(
            path,
            color = GhostPinColors.TextMuted,
            style =
                androidx.compose.ui.graphics.drawscope
                    .Stroke(width = 4f, cap = StrokeCap.Round)
        )
        val playheadIndex = ((mapped.lastIndex) * progress).roundToInt().coerceIn(0, mapped.lastIndex)
        drawCircle(
            color = GhostPinColors.Primary,
            radius = 9f,
            center = mapped[playheadIndex],
        )
        drawCircle(color = Color.White, radius = 4f, center = mapped[playheadIndex])
    }
}

@Composable
private fun ElevationMiniGraph(
    profile: ElevationProfile,
    progress: Float,
    height: Dp
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Perfil de altitude", color = GhostPinColors.TextSecondary, fontSize = 11.sp)
            if (profile.isEstimated) {
                Text("  (estimada)", color = GhostPinColors.Warning, fontSize = 11.sp)
            }
        }
        Canvas(
            modifier =
                Modifier.fillMaxWidth().height(height).background(
                    color = GhostPinColors.SurfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                )
        ) {
            if (profile.values.size < 2) return@Canvas
            val min = profile.values.minOrNull() ?: 0f
            val max = profile.values.maxOrNull() ?: 1f
            val range = (max - min).takeIf { it > 1e-4f } ?: 1f
            val padding = 12f
            val points =
                profile.values.mapIndexed { index, v ->
                    val x =
                        (index.toFloat() / (profile.values.size - 1).coerceAtLeast(1)) * (size.width - 2 * padding) +
                            padding
                    val y = size.height - (((v - min) / range) * (size.height - 2 * padding) + padding)
                    Offset(x, y)
                }
            val path =
                Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                }
            drawPath(
                path,
                color = GhostPinColors.TextMuted,
                style =
                    androidx.compose.ui.graphics.drawscope
                        .Stroke(width = 2.5f)
            )
            val px = progress.coerceIn(0f, 1f) * size.width
            drawRect(
                color = GhostPinColors.Primary.copy(alpha = 0.14f),
                topLeft = Offset.Zero,
                size = Size(px, size.height),
            )
        }
    }
}

private fun downsampleRouteForPreview(
    route: Route,
    lowPerformance: Boolean
): Route {
    val maxPoints = if (lowPerformance) 120 else 320
    if (route.waypoints.size <= maxPoints) return route
    val sampled = downsampleWaypoints(route.waypoints, maxPoints)
    return route.copy(waypoints = sampled)
}

private fun downsampleWaypoints(
    waypoints: List<Waypoint>,
    maxPoints: Int
): List<Waypoint> {
    if (waypoints.size <= maxPoints) return waypoints
    val step = (waypoints.size - 1).toFloat() / (maxPoints - 1).toFloat()
    return buildList(maxPoints) {
        for (i in 0 until maxPoints) {
            val index = (i * step).roundToInt().coerceIn(0, waypoints.lastIndex)
            add(waypoints[index])
        }
    }
}

private fun buildElevationProfile(route: Route): ElevationProfile {
    val waypoints = route.waypoints
    val realAltitude = waypoints.any { kotlin.math.abs(it.altitude) > 0.001 }
    if (realAltitude) {
        return ElevationProfile(values = waypoints.map { it.altitude.toFloat() }, isEstimated = false)
    }
    val estimated = mutableListOf<Float>()
    var distanceAcc = 0.0
    waypoints.forEachIndexed { index, waypoint ->
        if (index > 0) {
            val prev = waypoints[index - 1]
            distanceAcc += GeoMath.haversineMeters(prev.lat, prev.lng, waypoint.lat, waypoint.lng)
        }
        val synthetic = 35f + (kotlin.math.sin(distanceAcc / 650.0) * 18.0).toFloat()
        estimated += synthetic
    }
    return ElevationProfile(values = estimated, isEstimated = true)
}

@Composable
private fun WaypointCard(
    index: Int,
    waypoint: Waypoint,
    isLast: Boolean,
    hasOverride: Boolean,
    onRemove: () -> Unit,
    onEditSegment: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = GhostPinColors.Surface),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Index badge
            Box(
                modifier =
                    Modifier
                        .size(28.dp)
                        .background(
                            color = if (index == 0) GhostPinColors.Success else GhostPinColors.WaypointDefault,
                            shape = RoundedCornerShape(6.dp),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Text("${index + 1}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    waypoint.label ?: "Waypoint ${index + 1}",
                    color = GhostPinColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "${"%.5f".format(waypoint.lat)}, ${"%.5f".format(waypoint.lng)}",
                    color = GhostPinColors.TextSecondary,
                    fontSize = 11.sp,
                )
            }

            // Segment override button (not for last waypoint — no segment starts here)
            if (!isLast) {
                IconButton(onClick = onEditSegment, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (hasOverride) Icons.Default.Tune else Icons.Default.Settings,
                        null,
                        tint = if (hasOverride) GhostPinColors.Warning else GhostPinColors.TextDisabled,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Remove waypoint
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, null, tint = GhostPinColors.TextDisabled, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SavedRouteCard(
    route: com.ghostpin.core.model.Route,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = GhostPinColors.SurfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Route, null, tint = GhostPinColors.Primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(route.name, color = GhostPinColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("${route.waypoints.size} waypoints", color = GhostPinColors.TextSecondary, fontSize = 11.sp)
            }
            IconButton(onClick = onLoad, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.FileOpen, null, tint = GhostPinColors.Primary, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, null, tint = GhostPinColors.Error, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SegmentOverrideSheet(
    segmentIndex: Int,
    initialSpeed: Double?,
    initialPause: Double?,
    initialLoop: Boolean,
    onDismiss: () -> Unit,
    onApply: (Double?, Double?, Boolean) -> Unit,
    onClear: () -> Unit,
) {
    var speedText by remember { mutableStateOf(initialSpeed?.toString() ?: "") }
    var pauseText by remember { mutableStateOf(initialPause?.toString() ?: "") }
    var loop by remember { mutableStateOf(initialLoop) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = GhostPinColors.Surface,
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(
                "Segment ${segmentIndex + 1} → ${segmentIndex + 2} Overrides",
                color = GhostPinColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(20.dp))

            // Speed override
            OutlinedTextField(
                value = speedText,
                onValueChange = { speedText = it },
                label = { Text("Speed override (m/s)", color = GhostPinColors.TextSecondary) },
                placeholder = { Text("Leave blank = profile default", color = GhostPinColors.TextDisabled) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GhostPinColors.Primary,
                        unfocusedBorderColor = GhostPinColors.TextMuted,
                        focusedTextColor = GhostPinColors.TextPrimary,
                        unfocusedTextColor = GhostPinColors.TextPrimary,
                    ),
            )

            Spacer(Modifier.height(12.dp))

            // Pause duration
            OutlinedTextField(
                value = pauseText,
                onValueChange = { pauseText = it },
                label = { Text("Pause at end of segment (seconds)", color = GhostPinColors.TextSecondary) },
                placeholder = { Text("0 = no pause", color = GhostPinColors.TextDisabled) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GhostPinColors.Primary,
                        unfocusedBorderColor = GhostPinColors.TextMuted,
                        focusedTextColor = GhostPinColors.TextPrimary,
                        unfocusedTextColor = GhostPinColors.TextPrimary,
                    ),
            )

            Spacer(Modifier.height(12.dp))

            // Loop toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = loop,
                    onCheckedChange = { loop = it },
                    colors =
                        SwitchDefaults.colors(
                            checkedThumbColor = GhostPinColors.Background,
                            checkedTrackColor = GhostPinColors.Primary
                        ),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Loop at this segment", color = GhostPinColors.TextPrimary, fontSize = 14.sp)
                    Text(
                        "Simulation restarts from start when reaching this point",
                        color = GhostPinColors.TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onClear,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GhostPinColors.Error),
                ) { Text("Clear Override") }

                Button(
                    onClick = {
                        val speed = speedText.toDoubleOrNull()
                        val pause = pauseText.toDoubleOrNull()
                        onApply(speed, pause, loop)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = GhostPinColors.Primary),
                ) { Text("Apply", color = GhostPinColors.Background, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
