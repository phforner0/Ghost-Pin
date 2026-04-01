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
 * Bug #8 fix: [registerProvider] now calls [removeTestProvider] inside a try/catch
 * before [addTestProvider], so that a service crash-and-restart never leaves a
 * "provider already exists" IllegalArgumentException.
 */
@Singleton
class MockLocationInjector
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : LocationInjector {
        private val locationManager: LocationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        private var isProviderRegistered = false
        private val providerName = LocationManager.GPS_PROVIDER

        /**
         * Register this app as a mock location provider.
         * Must be called before injecting locations.
         *
         * Idempotent: safe to call multiple times and safe to call after a service
         * crash where the provider may still be lingering in the system.
         */
        @SuppressLint("MissingPermission")
        override fun registerProvider() {
            // Bug #8: always attempt removal first to avoid "provider already registered"
            // crash on service restart after unexpected termination.
            silentlyRemoveProvider()

            try {
                locationManager.addTestProvider(
                    providerName,
                    false, // requiresNetwork
                    false, // requiresSatellite
                    false, // requiresCell
                    false, // hasMonetaryCost
                    true, // supportsAltitude
                    true, // supportsSpeed
                    true, // supportsBearing
                    ProviderProperties.POWER_USAGE_LOW,
                    ProviderProperties.ACCURACY_FINE,
                )
                locationManager.setTestProviderEnabled(providerName, true)
                isProviderRegistered = true
            } catch (e: SecurityException) {
                throw IllegalStateException(
                    "Cannot register mock provider. Ensure this app is selected as " +
                        "'Mock location app' in Developer Options.",
                    e
                )
            } catch (e: IllegalArgumentException) {
                // Should not happen after the silent removal above, but guard anyway.
                throw IllegalStateException(
                    "addTestProvider failed unexpectedly: ${e.message}",
                    e
                )
            }
        }

        /**
         * Inject a [MockLocation] into the system as a real GPS fix.
         * Auto-registers the provider if not yet registered.
         */
        @SuppressLint("MissingPermission")
        override fun inject(mockLocation: MockLocation) {
            if (!isProviderRegistered) registerProvider()
            locationManager.setTestProviderLocation(providerName, toAndroidLocation(mockLocation))
        }

        /**
         * Unregister the mock provider and restore normal GPS operation.
         * Safe to call even if the provider was never registered.
         */
        override fun unregisterProvider() {
            silentlyRemoveProvider()
        }

        // ── Private helpers ──────────────────────────────────────────────────────

        /** Remove the test provider without throwing — used in both registration and cleanup. */
        private fun silentlyRemoveProvider() {
            try {
                locationManager.setTestProviderEnabled(providerName, false)
                locationManager.removeTestProvider(providerName)
            } catch (_: Exception) {
                // Provider may not exist; that is fine.
            } finally {
                isProviderRegistered = false
            }
        }

        private fun toAndroidLocation(mock: MockLocation): Location =
            Location(providerName).apply {
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
                elapsedRealtimeNanos = mock.elapsedRealtimeNanos
                    .takeIf { it > 0L }
                    ?: SystemClock.elapsedRealtimeNanos()
            }
    }
