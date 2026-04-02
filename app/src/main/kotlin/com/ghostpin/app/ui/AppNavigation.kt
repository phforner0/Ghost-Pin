@file:Suppress("ktlint:standard:function-naming")

package com.ghostpin.app.ui

import android.net.Uri
import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ghostpin.app.BuildConfig
import com.ghostpin.app.ui.onboarding.OnboardingScreen
import com.ghostpin.core.model.MovementProfile

/**
 * Navigation route constants.
 */
object AppRoute {
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"
    const val ROUTE_EDITOR = "route_editor"
    const val HISTORY = "history"
    const val SCHEDULE = "schedule"
    const val PROFILES = "profiles"
    const val DIAGNOSTICS = "diagnostics"
}

/**
 * Top-level navigation host. Replaces the old manual `when (isOnboardingComplete)` switching.
 *
 * - **Onboarding** → shown once, then navigates to Main and clears backstack.
 * - **Main** → primary GhostPinScreen with simulation controls.
 * - **RouteEditor** → full-screen route editor, navigable from TopAppBar.
 */
@Composable
fun AppNavHost(
    viewModel: SimulationViewModel,
    isOnboardingComplete: Boolean,
    permissionMessage: String?,
    lowMemorySignal: Int = 0,
    onPermissionMessageDismissed: () -> Unit,
    onStartSimulation: (MovementProfile, Double) -> Unit,
    onStopSimulation: () -> Unit,
    onPickGpxFile: () -> Unit,
    onImportRouteFile: () -> Unit,
    onExportRouteFile: (String, String) -> Unit,
    pendingImportedRouteUri: Uri?,
    onImportedRouteConsumed: () -> Unit,
) {
    val navController = rememberNavController()

    val startDestination = if (isOnboardingComplete) AppRoute.MAIN else AppRoute.ONBOARDING

    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        // ── Onboarding ──────────────────────────────────────────────────────
        composable(AppRoute.ONBOARDING) {
            OnboardingScreen(
                onComplete = { profileName, lat, lng ->
                    viewModel.selectProfile(
                        MovementProfile.BUILT_IN[profileName] ?: MovementProfile.CAR
                    )
                    viewModel.setStartLat(lat)
                    viewModel.setStartLng(lng)
                    navController.navigate(AppRoute.MAIN) {
                        popUpTo(AppRoute.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        // ── Main Screen ─────────────────────────────────────────────────────
        composable(AppRoute.MAIN) {
            LaunchedEffect(Unit) { viewModel.initializeLocation(navController.context) }
            GhostPinScreen(
                viewModel = viewModel,
                permissionMessage = permissionMessage,
                lowMemorySignal = lowMemorySignal,
                onPermissionMessageDismissed = onPermissionMessageDismissed,
                onStartSimulation = onStartSimulation,
                onStopSimulation = onStopSimulation,
                onPickGpxFile = onPickGpxFile,
                onNavigateToRouteEditor = {
                    navController.navigate(AppRoute.ROUTE_EDITOR)
                },
                onNavigateToHistory = {
                    navController.navigate(AppRoute.HISTORY)
                },
                onNavigateToSchedule = {
                    if (BuildConfig.SCHEDULING_ENABLED) {
                        navController.navigate(AppRoute.SCHEDULE)
                    }
                },
                onNavigateToProfiles = {
                    navController.navigate(AppRoute.PROFILES)
                },
                onNavigateToDiagnostics = {
                    if (BuildConfig.REALISM_DIAGNOSTICS_ENABLED) {
                        navController.navigate(AppRoute.DIAGNOSTICS)
                    }
                },
            )
        }

        // ── Route Editor ────────────────────────────────────────────────────
        composable(AppRoute.ROUTE_EDITOR) {
            RouteEditorScreen(
                onBack = { navController.popBackStack() },
                onRouteReady = { route ->
                    viewModel.applyRouteForSimulation(route)
                    navController.navigate(AppRoute.MAIN) {
                        popUpTo(AppRoute.ROUTE_EDITOR) { inclusive = true }
                    }
                },
                onImportRouteFile = onImportRouteFile,
                onExportRouteFile = onExportRouteFile,
                pendingImportedRouteUri = pendingImportedRouteUri,
                onImportedRouteConsumed = onImportedRouteConsumed,
            )
        }

        composable(AppRoute.HISTORY) {
            val historyViewModel: HistoryViewModel = hiltViewModel()
            HistoryScreen(
                viewModel = historyViewModel,
                onBack = { navController.popBackStack() },
                onReplay = { history ->
                    viewModel.applyReplayConfig(history)
                    navController.navigate(AppRoute.MAIN) {
                        popUpTo(AppRoute.HISTORY) { inclusive = true }
                    }
                    onStartSimulation(viewModel.selectedProfile.value, history.waypointPauseSec)
                },
            )
        }

        if (BuildConfig.SCHEDULING_ENABLED) {
            composable(AppRoute.SCHEDULE) {
                ScheduleScreen(
                    onBack = { navController.popBackStack() },
                    defaultConfig = viewModel.buildCurrentConfig(),
                )
            }
        }

        composable(AppRoute.PROFILES) {
            ProfileManagerScreen(
                onBack = { navController.popBackStack() },
                onUseProfile = { profile ->
                    viewModel.selectProfile(profile)
                    navController.popBackStack()
                },
                onUseAndAnalyze = { profile ->
                    viewModel.selectProfile(profile)
                    if (BuildConfig.REALISM_DIAGNOSTICS_ENABLED) {
                        navController.navigate(AppRoute.DIAGNOSTICS)
                    }
                },
            )
        }

        if (BuildConfig.REALISM_DIAGNOSTICS_ENABLED) {
            composable(AppRoute.DIAGNOSTICS) {
                RealismDiagnosticsScreen(
                    simulationViewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
