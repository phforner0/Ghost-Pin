package com.ghostpin.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.SystemClock
import com.ghostpin.core.model.MockLocation
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges the pure-Kotlin [MockLocation] data class to Android's LocationManager
 * mock provider API.
 *
 * Handles:
 * - Test provider registration / unregistration
 * - MockLocation → android.location.Location conversion
 * - elapsedRealtimeNanos and provider metadata
 */
@Singleton
class MockLocationInjector @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val locationManager: LocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private var isProviderRegistered = false
    private val providerName = LocationManager.GPS_PROVIDER

    /**
     * Register this app as a mock location provider.
     * Must be called before injecting locations.
     *
     * Requirements:
     * - App must be selected as "Mock location app" in Developer Options
     * - ACCESS_FINE_LOCATION permission must be granted
     */
    @SuppressLint("MissingPermission")
    fun registerProvider() {
        if (isProviderRegistered) return

        try {
            locationManager.addTestProvider(
                providerName,
                false,  // requiresNetwork
                false,  // requiresSatellite
                false,  // requiresCell
                false,  // hasMonetaryCost
                true,   // supportsAltitude
                true,   // supportsSpeed
                true,   // supportsBearing
                ProviderProperties.POWER_USAGE_LOW,
                ProviderProperties.ACCURACY_FINE,
            )
            locationManager.setTestProviderEnabled(providerName, true)
            isProviderRegistered = true
        } catch (e: SecurityException) {
            throw IllegalStateException(
                "Cannot register mock provider. Ensure this app is selected as " +
                "'Mock location app' in Developer Options.", e
            )
        }
    }

    /**
     * Inject a [MockLocation] into the system as a real GPS fix.
     */
    @SuppressLint("MissingPermission")
    fun inject(mockLocation: MockLocation) {
        if (!isProviderRegistered) {
            registerProvider()
        }

        val androidLocation = toAndroidLocation(mockLocation)
        locationManager.setTestProviderLocation(providerName, androidLocation)
    }

    /**
     * Unregister the mock provider and restore normal GPS operation.
     */
    fun unregisterProvider() {
        if (!isProviderRegistered) return

        try {
            locationManager.setTestProviderEnabled(providerName, false)
            locationManager.removeTestProvider(providerName)
        } catch (e: Exception) {
            // Best-effort cleanup
        } finally {
            isProviderRegistered = false
        }
    }

    /**
     * Convert platform-independent [MockLocation] to Android [Location].
     */
    private fun toAndroidLocation(mock: MockLocation): Location {
        return Location(providerName).apply {
            latitude = mock.lat
            longitude = mock.lng
            altitude = mock.altitude
            speed = mock.speed
            bearing = mock.bearing
            accuracy = mock.accuracy
            verticalAccuracyMeters = mock.verticalAccuracy
            speedAccuracyMetersPerSecond = mock.speedAccuracy
            bearingAccuracyDegrees = mock.bearingAccuracy
            time = mock.timestampMs
            elapsedRealtimeNanos = if (mock.elapsedRealtimeNanos > 0) {
                mock.elapsedRealtimeNanos
            } else {
                SystemClock.elapsedRealtimeNanos()
            }
        }
    }
}
