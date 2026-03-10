package com.ghostpin.app.location;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.location.provider.ProviderProperties;
import android.os.SystemClock;
import com.ghostpin.core.model.MockLocation;
import dagger.hilt.android.qualifiers.ApplicationContext;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Bridges the pure-Kotlin [MockLocation] data class to Android's LocationManager
 * mock provider API.
 *
 * Handles:
 * - Test provider registration / unregistration
 * - MockLocation → android.location.Location conversion
 * - elapsedRealtimeNanos and provider metadata
 */
@javax.inject.Singleton()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000:\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u0007\u0018\u00002\u00020\u0001B\u0011\b\u0007\u0012\b\b\u0001\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u0010\u0010\u000b\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u000eH\u0007J\b\u0010\u000f\u001a\u00020\fH\u0007J\u0010\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0012\u001a\u00020\u000eH\u0002J\u0006\u0010\u0013\u001a\u00020\fR\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\nX\u0082D\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0014"}, d2 = {"Lcom/ghostpin/app/location/MockLocationInjector;", "", "context", "Landroid/content/Context;", "(Landroid/content/Context;)V", "isProviderRegistered", "", "locationManager", "Landroid/location/LocationManager;", "providerName", "", "inject", "", "mockLocation", "Lcom/ghostpin/core/model/MockLocation;", "registerProvider", "toAndroidLocation", "Landroid/location/Location;", "mock", "unregisterProvider", "app_nonplayDebug"})
public final class MockLocationInjector {
    @org.jetbrains.annotations.NotNull()
    private final android.content.Context context = null;
    @org.jetbrains.annotations.NotNull()
    private final android.location.LocationManager locationManager = null;
    private boolean isProviderRegistered = false;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String providerName = "gps";
    
    @javax.inject.Inject()
    public MockLocationInjector(@dagger.hilt.android.qualifiers.ApplicationContext()
    @org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        super();
    }
    
    /**
     * Register this app as a mock location provider.
     * Must be called before injecting locations.
     *
     * Requirements:
     * - App must be selected as "Mock location app" in Developer Options
     * - ACCESS_FINE_LOCATION permission must be granted
     */
    @android.annotation.SuppressLint(value = {"MissingPermission"})
    public final void registerProvider() {
    }
    
    /**
     * Inject a [MockLocation] into the system as a real GPS fix.
     */
    @android.annotation.SuppressLint(value = {"MissingPermission"})
    public final void inject(@org.jetbrains.annotations.NotNull()
    com.ghostpin.core.model.MockLocation mockLocation) {
    }
    
    /**
     * Unregister the mock provider and restore normal GPS operation.
     */
    public final void unregisterProvider() {
    }
    
    /**
     * Convert platform-independent [MockLocation] to Android [Location].
     */
    private final android.location.Location toAndroidLocation(com.ghostpin.core.model.MockLocation mock) {
        return null;
    }
}