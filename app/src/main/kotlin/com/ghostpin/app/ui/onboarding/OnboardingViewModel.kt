package com.ghostpin.app.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostpin.app.data.OnboardingDataStore
import com.ghostpin.core.model.DefaultCoordinates
import com.ghostpin.core.model.MovementProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the 3-step onboarding flow.
 *
 * Step 1 — Permissions (overlay, notification, location) Step 2 — Mock location app configuration
 * Step 3 — Initial simulation setup (profile + coordinates)
 */
@HiltViewModel
class OnboardingViewModel
@Inject
constructor(
        @ApplicationContext private val context: Context,
        private val onboardingDataStore: OnboardingDataStore,
) : ViewModel() {

    data class UiState(
            val currentStep: Int = 0,
            // Step 1: Permissions
            val hasOverlayPermission: Boolean = false,
            val hasNotificationPermission: Boolean = false,
            val hasLocationPermission: Boolean = false,
            // Step 2: Mock location
            val isMockLocationConfigured: Boolean = false,
            val isDevOptionsEnabled: Boolean = false,
            // Step 3: Simulation config
            val selectedProfile: String = "Car",
            val startLat: String = DefaultCoordinates.START_LAT.toString(),
            val startLng: String = DefaultCoordinates.START_LNG.toString(),
            // Profiles list
            val availableProfiles: List<String> = MovementProfile.BUILT_IN.keys.toList(),
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Whether onboarding is already completed (skip to main flow). */
    val isOnboardingComplete: Flow<Boolean> = onboardingDataStore.isComplete

    /** Refresh permission/configuration status — call from onResume(). */
    fun refreshStatus() {
        _uiState.update { state ->
            state.copy(
                    hasOverlayPermission = Settings.canDrawOverlays(context),
                    hasNotificationPermission = hasNotificationPermission(),
                    hasLocationPermission =
                            ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED,
                    isMockLocationConfigured = isMockLocationApp(),
                    isDevOptionsEnabled = isDevOptionsEnabled(),
            )
        }
    }

    fun nextStep() {
        _uiState.update { it.copy(currentStep = (it.currentStep + 1).coerceAtMost(2)) }
    }

    fun previousStep() {
        _uiState.update { it.copy(currentStep = (it.currentStep - 1).coerceAtLeast(0)) }
    }

    fun selectProfile(name: String) {
        _uiState.update { it.copy(selectedProfile = name) }
    }

    fun setStartLat(value: String) {
        _uiState.update { it.copy(startLat = value) }
    }

    fun setStartLng(value: String) {
        _uiState.update { it.copy(startLng = value) }
    }

    /** Validate coordinates and return parsed values, or null if invalid. */
    fun validateCoordinates(): Pair<Double, Double>? {
        val lat = _uiState.value.startLat.toDoubleOrNull() ?: return null
        val lng = _uiState.value.startLng.toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
        return lat to lng
    }

    /** Mark onboarding as complete and persist. */
    fun completeOnboarding() {
        viewModelScope.launch { onboardingDataStore.markComplete() }
    }

    fun setStep(step: Int) {
        _uiState.update { it.copy(currentStep = step.coerceIn(0, 2)) }
    }

    // ── Private helpers ──────────────────────────────────────────────────

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else true // Not needed below Android 13
    }

    @Suppress("DEPRECATION")
    private fun isMockLocationApp(): Boolean {
        return try {
            val mockApp = Settings.Secure.getString(context.contentResolver, "mock_location")
            mockApp == context.packageName
        } catch (_: Exception) {
            false
        }
    }

    private fun isDevOptionsEnabled(): Boolean {
        return try {
            Settings.Global.getInt(
                    context.contentResolver,
                    Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                    0
            ) == 1
        } catch (_: Exception) {
            false
        }
    }
}
