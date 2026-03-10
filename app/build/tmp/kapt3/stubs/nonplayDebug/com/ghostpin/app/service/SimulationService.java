package com.ghostpin.app.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.SystemClock;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.LifecycleService;
import com.ghostpin.app.BuildConfig;
import com.ghostpin.app.GhostPinApp;
import com.ghostpin.app.R;
import com.ghostpin.app.location.MockLocationInjector;
import com.ghostpin.app.ui.MainActivity;
import com.ghostpin.core.model.MockLocation;
import com.ghostpin.core.model.MovementProfile;
import com.ghostpin.engine.noise.LayeredNoiseModel;
import dagger.hilt.android.AndroidEntryPoint;
import kotlinx.coroutines.flow.StateFlow;
import javax.inject.Inject;

/**
 * Foreground service running the GPS simulation loop.
 *
 * Runs at configurable Hz (default 1 Hz), applying the full noise pipeline
 * to each interpolated position and injecting it via [MockLocationInjector].
 */
@dagger.hilt.android.AndroidEntryPoint()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000`\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0006\n\u0002\b\u0007\b\u0007\u0018\u0000 *2\u00020\u0001:\u0001*B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0014\u001a\u00020\u00152\u0006\u0010\u0016\u001a\u00020\u0017H\u0002J\b\u0010\u0018\u001a\u00020\u0019H\u0016J\"\u0010\u001a\u001a\u00020\u001b2\b\u0010\u001c\u001a\u0004\u0018\u00010\u001d2\u0006\u0010\u001e\u001a\u00020\u001b2\u0006\u0010\u001f\u001a\u00020\u001bH\u0016J8\u0010 \u001a\u00020\u00192\u0006\u0010!\u001a\u00020\"2\u0006\u0010#\u001a\u00020$2\u0006\u0010%\u001a\u00020$2\u0006\u0010&\u001a\u00020$2\u0006\u0010'\u001a\u00020$2\u0006\u0010(\u001a\u00020\u001bH\u0002J\b\u0010)\u001a\u00020\u0019H\u0002R\u0014\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001e\u0010\u0006\u001a\u00020\u00078\u0006@\u0006X\u0087.\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\b\u0010\t\"\u0004\b\n\u0010\u000bR\u0010\u0010\f\u001a\u0004\u0018\u00010\rX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u000e\u001a\u0004\u0018\u00010\u000fX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0017\u0010\u0010\u001a\b\u0012\u0004\u0012\u00020\u00050\u0011\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\u0013\u00a8\u0006+"}, d2 = {"Lcom/ghostpin/app/service/SimulationService;", "Landroidx/lifecycle/LifecycleService;", "()V", "_state", "Lkotlinx/coroutines/flow/MutableStateFlow;", "Lcom/ghostpin/app/service/SimulationState;", "mockLocationInjector", "Lcom/ghostpin/app/location/MockLocationInjector;", "getMockLocationInjector", "()Lcom/ghostpin/app/location/MockLocationInjector;", "setMockLocationInjector", "(Lcom/ghostpin/app/location/MockLocationInjector;)V", "noiseModel", "Lcom/ghostpin/engine/noise/LayeredNoiseModel;", "simulationJob", "Lkotlinx/coroutines/Job;", "state", "Lkotlinx/coroutines/flow/StateFlow;", "getState", "()Lkotlinx/coroutines/flow/StateFlow;", "buildNotification", "Landroid/app/Notification;", "profileName", "", "onDestroy", "", "onStartCommand", "", "intent", "Landroid/content/Intent;", "flags", "startId", "startSimulation", "profile", "Lcom/ghostpin/core/model/MovementProfile;", "startLat", "", "startLng", "endLat", "endLng", "frequencyHz", "stopSimulation", "Companion", "app_nonplayDebug"})
public final class SimulationService extends androidx.lifecycle.LifecycleService {
    @javax.inject.Inject()
    public com.ghostpin.app.location.MockLocationInjector mockLocationInjector;
    @org.jetbrains.annotations.Nullable()
    private com.ghostpin.engine.noise.LayeredNoiseModel noiseModel;
    @org.jetbrains.annotations.Nullable()
    private kotlinx.coroutines.Job simulationJob;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<com.ghostpin.app.service.SimulationState> _state = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<com.ghostpin.app.service.SimulationState> state = null;
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_PROFILE_NAME = "profile_name";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_START_LAT = "start_lat";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_START_LNG = "start_lng";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_END_LAT = "end_lat";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_END_LNG = "end_lng";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_FREQUENCY_HZ = "frequency_hz";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String ACTION_STOP = "com.ghostpin.action.STOP";
    public static final int NOTIFICATION_ID = 1001;
    @org.jetbrains.annotations.NotNull()
    public static final com.ghostpin.app.service.SimulationService.Companion Companion = null;
    
    public SimulationService() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.ghostpin.app.location.MockLocationInjector getMockLocationInjector() {
        return null;
    }
    
    public final void setMockLocationInjector(@org.jetbrains.annotations.NotNull()
    com.ghostpin.app.location.MockLocationInjector p0) {
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<com.ghostpin.app.service.SimulationState> getState() {
        return null;
    }
    
    @java.lang.Override()
    public int onStartCommand(@org.jetbrains.annotations.Nullable()
    android.content.Intent intent, int flags, int startId) {
        return 0;
    }
    
    private final void startSimulation(com.ghostpin.core.model.MovementProfile profile, double startLat, double startLng, double endLat, double endLng, int frequencyHz) {
    }
    
    private final void stopSimulation() {
    }
    
    @java.lang.Override()
    public void onDestroy() {
    }
    
    private final android.app.Notification buildNotification(java.lang.String profileName) {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u001a\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0007\n\u0002\u0010\b\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\fX\u0086T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\r"}, d2 = {"Lcom/ghostpin/app/service/SimulationService$Companion;", "", "()V", "ACTION_STOP", "", "EXTRA_END_LAT", "EXTRA_END_LNG", "EXTRA_FREQUENCY_HZ", "EXTRA_PROFILE_NAME", "EXTRA_START_LAT", "EXTRA_START_LNG", "NOTIFICATION_ID", "", "app_nonplayDebug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}