package com.ghostpin.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ghostpin.app.data.SimulationConfig
import com.ghostpin.app.diagnostics.RealismDiagnosticsInput
import com.ghostpin.app.service.SimulationState
import com.ghostpin.app.scheduling.ScheduleEntity
import com.ghostpin.core.model.AppMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealismDiagnosticsScreen(
    simulationViewModel: SimulationViewModel,
    onBack: () -> Unit,
    viewModel: RealismDiagnosticsViewModel = hiltViewModel(),
) {
    val selectedProfile by simulationViewModel.selectedProfile.collectAsState()
    val selectedMode by simulationViewModel.selectedMode.collectAsState()
    val simulationState by simulationViewModel.simulationState.collectAsState()
    val route by simulationViewModel.route.collectAsState()
    val waypoints by simulationViewModel.waypoints.collectAsState()
    val lastUsedConfig by simulationViewModel.lastUsedConfig.collectAsState()
    val startLat by simulationViewModel.startLat.collectAsState()
    val startLng by simulationViewModel.startLng.collectAsState()
    val endLat by simulationViewModel.endLat.collectAsState()
    val endLng by simulationViewModel.endLng.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val scheduleViewModel: ScheduleViewModel = hiltViewModel()
    val schedules by scheduleViewModel.schedules.collectAsState()
    val exactAlarmUiState by scheduleViewModel.exactAlarmUiState.collectAsState()

    val input =
        RealismDiagnosticsInput(
            profile = selectedProfile,
            route = route,
            waypoints = waypoints,
            startLat = startLat,
            startLng = startLng,
            endLat = endLat,
            endLng = endLng,
        )

    LaunchedEffect(Unit) {
        scheduleViewModel.refreshExactAlarmUiState()
    }

    LaunchedEffect(input) {
        viewModel.analyze(input)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Realism Diagnostics") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                },
                actions = {
                    TextButton(onClick = { viewModel.analyze(input) }) {
                        Text("Re-run")
                    }
                },
            )
        },
    ) { padding ->
        when (val state = uiState) {
            RealismDiagnosticsUiState.Idle,
            RealismDiagnosticsUiState.Loading -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(24.dp))
                }
            }

            is RealismDiagnosticsUiState.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DebugStatusCard(
                        simulationState = simulationState,
                        selectedMode = selectedMode,
                        selectedProfileName = selectedProfile.name,
                        routeName = route?.name,
                        waypointsCount = waypoints.size,
                        lastUsedConfig = lastUsedConfig,
                        schedules = schedules,
                        exactAlarmMessage = exactAlarmUiState.message,
                    )
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { viewModel.analyze(input) }) {
                        Text("Try again")
                    }
                }
            }

            is RealismDiagnosticsUiState.Success -> {
                val result = state.result
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("${result.profileName} · ${result.routeName}", fontWeight = FontWeight.SemiBold)
                                Text("Route source: ${result.routeSource}")
                                Text("Synthetic samples: ${result.sampleCount}")
                                Text("Score: ${result.scorePercent}%")
                                Text(result.scoreNote, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    item {
                        DebugStatusCard(
                            simulationState = simulationState,
                            selectedMode = selectedMode,
                            selectedProfileName = selectedProfile.name,
                            routeName = route?.name,
                            waypointsCount = waypoints.size,
                            lastUsedConfig = lastUsedConfig,
                            schedules = schedules,
                            exactAlarmMessage = exactAlarmUiState.message,
                        )
                    }

                    if (result.trajectoryWarnings.isNotEmpty()) {
                        item {
                            Card(
                                colors =
                                    CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                    ),
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text("Trajectory warnings", fontWeight = FontWeight.SemiBold)
                                    result.trajectoryWarnings.forEach { warning ->
                                        Text(warning)
                                    }
                                }
                            }
                        }
                    }

                    items(result.metrics, key = { it.name }) { metric ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(metric.name, fontWeight = FontWeight.Medium)
                                    Text(metric.detail)
                                }
                                Text(
                                    if (metric.passed) "PASS" else "FAIL",
                                    color =
                                        if (metric.passed) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        },
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugStatusCard(
    simulationState: SimulationState,
    selectedMode: AppMode,
    selectedProfileName: String,
    routeName: String?,
    waypointsCount: Int,
    lastUsedConfig: SimulationConfig?,
    schedules: List<ScheduleEntity>,
    exactAlarmMessage: String,
) {
    val nextScheduleSummary =
        schedules.firstOrNull()?.let { schedule ->
            "${schedule.modeDisplayName()} · ${formatRelativeScheduleTime(schedule.startAtMs)}"
        } ?: "None"
    val currentRouteSummary =
        routeName ?: scheduleRouteSummary(routeId = null, appMode = selectedMode, waypointsCount = waypointsCount)
    val lastUsedConfigSummary =
        lastUsedConfig?.let { config ->
            "${config.profileName} · ${config.appMode.displayName} · ${config.routeSummary()}"
        } ?: "None"

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Debug Status", fontWeight = FontWeight.SemiBold)
            Text("Simulation: ${simulationStateLabel(simulationState)}")
            Text("Selected mode: ${selectedMode.displayName}")
            Text("Selected profile: $selectedProfileName")
            Text("Current route: $currentRouteSummary")
            Text("Last launch plan: $lastUsedConfigSummary")
            Text("Active schedules: ${schedules.size} · Next: $nextScheduleSummary")
            Text("Exact alarms: $exactAlarmMessage", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun simulationStateLabel(state: SimulationState): String =
    when (state) {
        SimulationState.Idle -> "Idle"
        is SimulationState.FetchingRoute -> "Fetching route (${state.profileName})"
        is SimulationState.Running -> "Running ${state.progressPercent.times(100).toInt()}%"
        is SimulationState.Paused -> "Paused ${state.progressPercent.times(100).toInt()}%"
        is SimulationState.Error -> "Error: ${state.message}"
    }
