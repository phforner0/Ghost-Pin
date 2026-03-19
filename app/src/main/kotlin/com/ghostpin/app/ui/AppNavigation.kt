package com.ghostpin.app.ui

import androidx.compose.runtime.*
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
            )
        }

        // ── Route Editor ────────────────────────────────────────────────────
        composable(AppRoute.ROUTE_EDITOR) {
            RouteEditorScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
