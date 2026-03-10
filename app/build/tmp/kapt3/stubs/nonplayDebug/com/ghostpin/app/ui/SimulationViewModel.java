package com.ghostpin.app.ui;

import androidx.lifecycle.ViewModel;
import com.ghostpin.app.service.SimulationState;
import com.ghostpin.core.model.MovementProfile;
import dagger.hilt.android.lifecycle.HiltViewModel;
import kotlinx.coroutines.flow.StateFlow;
import javax.inject.Inject;

/**
 * ViewModel managing simulation UI state.
 * Handles profile selection, coordinate input, and service state observation.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000>\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u0006\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010 \n\u0002\b\u000b\n\u0002\u0010\u0002\n\u0002\b\b\b\u0007\u0018\u00002\u00020\u0001B\u0007\b\u0007\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u001f\u001a\u00020 2\u0006\u0010!\u001a\u00020\bJ\u0016\u0010\"\u001a\u00020 2\u0006\u0010#\u001a\u00020\u00052\u0006\u0010$\u001a\u00020\u0005J\u0016\u0010%\u001a\u00020 2\u0006\u0010#\u001a\u00020\u00052\u0006\u0010$\u001a\u00020\u0005J\u000e\u0010&\u001a\u00020 2\u0006\u0010'\u001a\u00020\nR\u0014\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u0006\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\b0\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\t\u001a\b\u0012\u0004\u0012\u00020\n0\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u000b\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\f\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0017\u0010\r\u001a\b\u0012\u0004\u0012\u00020\u00050\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0017\u0010\u0011\u001a\b\u0012\u0004\u0012\u00020\u00050\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\u0010R\u0017\u0010\u0013\u001a\b\u0012\u0004\u0012\u00020\b0\u0014\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u0016R\u0017\u0010\u0017\u001a\b\u0012\u0004\u0012\u00020\b0\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0018\u0010\u0010R\u0017\u0010\u0019\u001a\b\u0012\u0004\u0012\u00020\n0\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001a\u0010\u0010R\u0017\u0010\u001b\u001a\b\u0012\u0004\u0012\u00020\u00050\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001c\u0010\u0010R\u0017\u0010\u001d\u001a\b\u0012\u0004\u0012\u00020\u00050\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001e\u0010\u0010\u00a8\u0006("}, d2 = {"Lcom/ghostpin/app/ui/SimulationViewModel;", "Landroidx/lifecycle/ViewModel;", "()V", "_endLat", "Lkotlinx/coroutines/flow/MutableStateFlow;", "", "_endLng", "_selectedProfile", "Lcom/ghostpin/core/model/MovementProfile;", "_simulationState", "Lcom/ghostpin/app/service/SimulationState;", "_startLat", "_startLng", "endLat", "Lkotlinx/coroutines/flow/StateFlow;", "getEndLat", "()Lkotlinx/coroutines/flow/StateFlow;", "endLng", "getEndLng", "profiles", "", "getProfiles", "()Ljava/util/List;", "selectedProfile", "getSelectedProfile", "simulationState", "getSimulationState", "startLat", "getStartLat", "startLng", "getStartLng", "selectProfile", "", "profile", "setEndCoords", "lat", "lng", "setStartCoords", "updateSimulationState", "state", "app_nonplayDebug"})
@dagger.hilt.android.lifecycle.HiltViewModel()
public final class SimulationViewModel extends androidx.lifecycle.ViewModel {
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<com.ghostpin.core.model.MovementProfile> _selectedProfile = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<com.ghostpin.core.model.MovementProfile> selectedProfile = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<com.ghostpin.app.service.SimulationState> _simulationState = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<com.ghostpin.app.service.SimulationState> simulationState = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<java.lang.Double> _startLat = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<java.lang.Double> startLat = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<java.lang.Double> _startLng = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<java.lang.Double> startLng = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<java.lang.Double> _endLat = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<java.lang.Double> endLat = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<java.lang.Double> _endLng = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<java.lang.Double> endLng = null;
    @org.jetbrains.annotations.NotNull()
    private final java.util.List<com.ghostpin.core.model.MovementProfile> profiles = null;
    
    @javax.inject.Inject()
    public SimulationViewModel() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<com.ghostpin.core.model.MovementProfile> getSelectedProfile() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<com.ghostpin.app.service.SimulationState> getSimulationState() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<java.lang.Double> getStartLat() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<java.lang.Double> getStartLng() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<java.lang.Double> getEndLat() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<java.lang.Double> getEndLng() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<com.ghostpin.core.model.MovementProfile> getProfiles() {
        return null;
    }
    
    public final void selectProfile(@org.jetbrains.annotations.NotNull()
    com.ghostpin.core.model.MovementProfile profile) {
    }
    
    public final void updateSimulationState(@org.jetbrains.annotations.NotNull()
    com.ghostpin.app.service.SimulationState state) {
    }
    
    public final void setStartCoords(double lat, double lng) {
    }
    
    public final void setEndCoords(double lat, double lng) {
    }
}