package com.ghostpin.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostpin.app.service.SimulationState

// ── Status Card ───────────────────────────────────────────────────────────────

/** Displays current simulation state with ETA when available. */
@Composable
fun SimulationStatusCard(state: SimulationState, etaText: String? = null) {
    Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
    ) {
        Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Simulation status",
                    tint =
                            when (state) {
                                is SimulationState.Running -> Color(0xFF80CBC4)
                                is SimulationState.FetchingRoute -> Color(0xFFFFB300)
                                is SimulationState.Error -> Color(0xFFCF6679)
                                else -> Color(0xFF666666)
                            },
                    modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                        text =
                                when (state) {
                                    is SimulationState.Idle -> "Idle"
                                    is SimulationState.FetchingRoute ->
                                            "Fetching route… • ${state.profileName}"
                                    is SimulationState.Running ->
                                            "Simulating • ${state.profileName}"
                                    is SimulationState.Paused -> "Paused • ${state.profileName}"
                                    is SimulationState.Error -> "Error"
                                },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = Color(0xFFE0E0E0),
                )
                // Show ETA when a route is loaded but simulation hasn't started
                if (state is SimulationState.Idle && etaText != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                            text = "ETA: $etaText",
                            fontSize = 13.sp,
                            color = Color(0xFF80CBC4),
                    )
                }
                if (state is SimulationState.Running) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                            text =
                                    "Volta ${state.currentLap}/${state.totalLapsLabel} · ${state.lapProgressPercent.times(100).toInt()}% · " +
                                            "${state.elapsedTimeSec}s elapsed" +
                                            (if (etaText != null) " · ETA ~$etaText" else ""),
                            fontSize = 13.sp,
                            color = Color(0xFF888888),
                    )
                }
                if (state is SimulationState.Paused) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Pausado na volta ${state.currentLap}/${state.totalLapsLabel} · ${state.lapProgressPercent.times(100).toInt()}%",
                        fontSize = 13.sp,
                        color = Color(0xFF888888),
                    )
                }
                if (state is SimulationState.Error) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                            text = state.message,
                            fontSize = 12.sp,
                            color = Color(0xFFCF6679),
                    )
                }
            }
        }
    }
}
