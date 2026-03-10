package com.ghostpin.app;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import dagger.hilt.android.HiltAndroidApp;

/**
 * GhostPin Application class.
 * Initializes Hilt DI and notification channels.
 */
@dagger.hilt.android.HiltAndroidApp()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0003\b\u0007\u0018\u0000 \u00062\u00020\u0001:\u0001\u0006B\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u0003\u001a\u00020\u0004H\u0002J\b\u0010\u0005\u001a\u00020\u0004H\u0016\u00a8\u0006\u0007"}, d2 = {"Lcom/ghostpin/app/GhostPinApp;", "Landroid/app/Application;", "()V", "createNotificationChannels", "", "onCreate", "Companion", "app_nonplayDebug"})
public final class GhostPinApp extends android.app.Application {
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String CHANNEL_SIMULATION = "simulation_channel";
    @org.jetbrains.annotations.NotNull()
    public static final com.ghostpin.app.GhostPinApp.Companion Companion = null;
    
    public GhostPinApp() {
        super();
    }
    
    @java.lang.Override()
    public void onCreate() {
    }
    
    private final void createNotificationChannels() {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0005"}, d2 = {"Lcom/ghostpin/app/GhostPinApp$Companion;", "", "()V", "CHANNEL_SIMULATION", "", "app_nonplayDebug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}