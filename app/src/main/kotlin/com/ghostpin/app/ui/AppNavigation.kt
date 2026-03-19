package com.ghostpin.app.ui

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
    onPermissionMessageDismissed: () -> Unit,
    onStartSimulation: (MovementProfile, Double) -> Unit,
    onStopSimulation: () -> Unit,
    onPickGpxFile: () -> Unit,
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
                    navController.navigate(AppRoute.SCHEDULE)
                },
            )
        }

        // ── Route Editor ────────────────────────────────────────────────────
        composable(AppRoute.ROUTE_EDITOR) {
            RouteEditorScreen(
                onBack = { navController.popBackStack() },
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
                    val profile = MovementProfile.BUILT_IN[history.profileIdOrName] ?: viewModel.selectedProfile.value
                    onStartSimulation(profile, 0.0)
                },
            )
        }

        composable(AppRoute.SCHEDULE) {
            ScheduleScreen(
                onBack = { navController.popBackStack() },
                defaultStartLat = viewModel.startLat.value,
                defaultStartLng = viewModel.startLng.value,
            )
        }
    }
}

