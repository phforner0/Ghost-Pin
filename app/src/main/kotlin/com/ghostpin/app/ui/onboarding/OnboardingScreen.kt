@file:OptIn(
        androidx.compose.foundation.ExperimentalFoundationApi::class,
        androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.ghostpin.app.ui.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ghostpin.app.ui.theme.GhostPinColors
import kotlinx.coroutines.launch

/**
 * 3-step interactive onboarding screen.
 *
 * Step 1: Permission grants (overlay, notifications, location) Step 2: Mock location app
 * configuration guide Step 3: Initial simulation setup (profile + coordinates)
 *
 * Uses [HorizontalPager] for swipe navigation with a step indicator. Persists completion via
 * [OnboardingViewModel] + DataStore.
 */
@Composable
fun OnboardingScreen(
        viewModel: OnboardingViewModel = viewModel(),
        onComplete: (profileName: String, lat: Double, lng: Double) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 3 })

    // Refresh permission status on resume (user may return from Settings)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Sync pager ↔ viewModel step
    LaunchedEffect(pagerState.currentPage) { viewModel.setStep(pagerState.currentPage) }

    Column(
            modifier =
                    Modifier.fillMaxSize().background(GhostPinColors.Surface).systemBarsPadding(),
    ) {
        // Step indicator
        StepIndicator(
                currentStep = pagerState.currentPage,
                totalSteps = 3,
                modifier = Modifier.padding(16.dp),
        )

        HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
        ) { page ->
            when (page) {
                0 -> PermissionsStep(state, viewModel)
                1 -> MockLocationStep(state)
                2 -> SimulationSetupStep(state, viewModel)
            }
        }

        // Bottom navigation
        Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Skip button
            TextButton(
                    onClick = {
                        viewModel.completeOnboarding()
                        val coords = viewModel.validateCoordinates()
                        onComplete(
                                state.selectedProfile,
                                coords?.first ?: -23.5505,
                                coords?.second ?: -46.6333,
                        )
                    }
            ) { Text("Skip", color = GhostPinColors.TextSecondary) }

            if (pagerState.currentPage < 2) {
                Button(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        },
                        colors =
                                ButtonDefaults.buttonColors(
                                        containerColor = GhostPinColors.Primary
                                ),
                ) { Text("Next", color = GhostPinColors.Surface) }
            } else {
                Button(
                        onClick = {
                            val coords = viewModel.validateCoordinates()
                            if (coords != null) {
                                viewModel.completeOnboarding()
                                onComplete(state.selectedProfile, coords.first, coords.second)
                            }
                        },
                        colors =
                                ButtonDefaults.buttonColors(containerColor = GhostPinColors.Accent),
                ) { Text("Start Simulation", color = GhostPinColors.Surface) }
            }
        }
    }
}

// ── Step indicator ──────────────────────────────────────────────────────

@Composable
private fun StepIndicator(currentStep: Int, totalSteps: Int, modifier: Modifier = Modifier) {
    Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(totalSteps) { index ->
            val color by
                    animateColorAsState(
                            targetValue =
                                    if (index <= currentStep) GhostPinColors.Primary
                                    else GhostPinColors.TextSecondary,
                            label = "stepColor",
                    )
            Box(
                    modifier =
                            Modifier.size(if (index == currentStep) 12.dp else 8.dp)
                                    .clip(CircleShape)
                                    .background(color),
            )
            if (index < totalSteps - 1) Spacer(Modifier.width(8.dp))
        }
    }
}

// ── Step 1: Permissions ─────────────────────────────────────────────────

@Composable
private fun PermissionsStep(state: OnboardingViewModel.UiState, viewModel: OnboardingViewModel) {
    val context = LocalContext.current

    // Notification permission launcher (Android 13+)
    val notifLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                viewModel.refreshStatus()
            }

    // Location permission launcher
    val locationLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                viewModel.refreshStatus()
            }

    Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
                "Permissions",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = GhostPinColors.TextPrimary
        )
        Text(
                "GhostPin needs a few permissions to simulate GPS locations.",
                color = GhostPinColors.TextSecondary,
                fontSize = 14.sp,
        )

        Spacer(Modifier.height(8.dp))

        // Overlay permission
        PermissionRow(
                title = "Draw over other apps",
                description = "Required for floating controls",
                isGranted = state.hasOverlayPermission,
                onGrant = {
                    val intent =
                            Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}"),
                            )
                    context.startActivity(intent)
                },
        )

        // Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionRow(
                    title = "Notifications",
                    description = "Show simulation status",
                    isGranted = state.hasNotificationPermission,
                    onGrant = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            )
        }

        // Location permission
        PermissionRow(
                title = "Fine location",
                description = "Access GPS for mock injection",
                isGranted = state.hasLocationPermission,
                onGrant = { locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
        )
    }
}

@Composable
private fun PermissionRow(
        title: String,
        description: String,
        isGranted: Boolean,
        onGrant: () -> Unit,
) {
    Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = GhostPinColors.CardBackground),
            shape = RoundedCornerShape(12.dp),
    ) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = GhostPinColors.TextPrimary)
                Text(description, fontSize = 12.sp, color = GhostPinColors.TextSecondary)
            }
            if (isGranted) {
                Text("✅", fontSize = 20.sp)
            } else {
                OutlinedButton(onClick = onGrant) { Text("Grant", color = GhostPinColors.Primary) }
            }
        }
    }
}

// ── Step 2: Mock Location ───────────────────────────────────────────────

@Composable
private fun MockLocationStep(state: OnboardingViewModel.UiState) {
    val context = LocalContext.current

    Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
                "Mock Location",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = GhostPinColors.TextPrimary
        )

        if (!state.isDevOptionsEnabled) {
            Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2010)),
                    shape = RoundedCornerShape(12.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                            "⚠️ Developer Options disabled",
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFFB300)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                            "Go to Settings → About phone → tap \"Build number\" 7 times to enable Developer Options.",
                            color = GhostPinColors.TextSecondary,
                            fontSize = 13.sp,
                    )
                }
            }
        }

        Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = GhostPinColors.CardBackground),
                shape = RoundedCornerShape(12.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Steps:", fontWeight = FontWeight.SemiBold, color = GhostPinColors.TextPrimary)
                Spacer(Modifier.height(8.dp))
                Text(
                        "1. Open Developer Options",
                        color = GhostPinColors.TextSecondary,
                        fontSize = 13.sp
                )
                Text(
                        "2. Find \"Select mock location app\"",
                        color = GhostPinColors.TextSecondary,
                        fontSize = 13.sp
                )
                Text("3. Select GhostPin", color = GhostPinColors.TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Status: ", color = GhostPinColors.TextSecondary)
                    Text(
                            if (state.isMockLocationConfigured) "✅ Configured"
                            else "❌ Not configured",
                            fontWeight = FontWeight.SemiBold,
                            color =
                                    if (state.isMockLocationConfigured) Color(0xFF4CAF50)
                                    else Color(0xFFEF5350),
                    )
                }
            }
        }

        Button(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                },
                colors = ButtonDefaults.buttonColors(containerColor = GhostPinColors.Primary),
                modifier = Modifier.fillMaxWidth(),
        ) { Text("Open Developer Options", color = GhostPinColors.Surface) }
    }
}

// ── Step 3: Simulation Setup ────────────────────────────────────────────

@Composable
private fun SimulationSetupStep(
        state: OnboardingViewModel.UiState,
        viewModel: OnboardingViewModel
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
                "Simulation Setup",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = GhostPinColors.TextPrimary
        )
        Text(
                "Choose a movement profile and starting coordinates.",
                color = GhostPinColors.TextSecondary,
                fontSize = 14.sp,
        )

        // Profile dropdown
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                    value = state.selectedProfile,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Movement Profile") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    colors =
                            OutlinedTextFieldDefaults.colors(
                                    unfocusedTextColor = GhostPinColors.TextPrimary,
                                    focusedTextColor = GhostPinColors.TextPrimary,
                                    unfocusedBorderColor = GhostPinColors.TextSecondary,
                                    focusedBorderColor = GhostPinColors.Primary,
                            ),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                state.availableProfiles.forEach { profile ->
                    DropdownMenuItem(
                            text = { Text(profile) },
                            onClick = {
                                viewModel.selectProfile(profile)
                                expanded = false
                            },
                    )
                }
            }
        }

        // Coordinates
        OutlinedTextField(
                value = state.startLat,
                onValueChange = { viewModel.setStartLat(it) },
                label = { Text("Latitude") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                colors =
                        OutlinedTextFieldDefaults.colors(
                                unfocusedTextColor = GhostPinColors.TextPrimary,
                                focusedTextColor = GhostPinColors.TextPrimary,
                                unfocusedBorderColor = GhostPinColors.TextSecondary,
                                focusedBorderColor = GhostPinColors.Primary,
                        ),
        )

        OutlinedTextField(
                value = state.startLng,
                onValueChange = { viewModel.setStartLng(it) },
                label = { Text("Longitude") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                colors =
                        OutlinedTextFieldDefaults.colors(
                                unfocusedTextColor = GhostPinColors.TextPrimary,
                                focusedTextColor = GhostPinColors.TextPrimary,
                                unfocusedBorderColor = GhostPinColors.TextSecondary,
                                focusedBorderColor = GhostPinColors.Primary,
                        ),
        )

        // Coordinate hint
        Text(
                text = "Default: São Paulo, SP (-23.5505, -46.6333)",
                color = GhostPinColors.TextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
        )
    }
}
