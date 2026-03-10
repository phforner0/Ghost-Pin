package com.ghostpin.app.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import androidx.activity.ComponentActivity;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.compose.foundation.layout.*;
import androidx.compose.material.icons.Icons;
import androidx.compose.material3.*;
import androidx.compose.runtime.*;
import androidx.compose.ui.Alignment;
import androidx.compose.ui.Modifier;
import androidx.compose.ui.graphics.Brush;
import androidx.compose.ui.text.font.FontWeight;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import com.ghostpin.app.service.SimulationService;
import com.ghostpin.app.service.SimulationState;
import com.ghostpin.core.model.MovementProfile;
import dagger.hilt.android.AndroidEntryPoint;
import org.maplibre.android.MapLibre;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;

@kotlin.Metadata(mv = {1, 9, 0}, k = 2, xi = 48, d1 = {"\u0000>\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\b\u0005\u001a2\u0010\u0000\u001a\u00020\u00012\u0006\u0010\u0002\u001a\u00020\u00032\u0012\u0010\u0004\u001a\u000e\u0012\u0004\u0012\u00020\u0006\u0012\u0004\u0012\u00020\u00010\u00052\f\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\u00010\bH\u0007\u001a\u001b\u0010\t\u001a\u00020\u00012\u0011\u0010\n\u001a\r\u0012\u0004\u0012\u00020\u00010\b\u00a2\u0006\u0002\b\u000bH\u0007\u001a\"\u0010\f\u001a\u00020\u00012\u0006\u0010\u0002\u001a\u00020\u00032\u0006\u0010\r\u001a\u00020\u000e2\b\b\u0002\u0010\u000f\u001a\u00020\u0010H\u0007\u001a2\u0010\u0011\u001a\u00020\u00012\f\u0010\u0012\u001a\b\u0012\u0004\u0012\u00020\u00060\u00132\u0006\u0010\u0014\u001a\u00020\u00062\u0012\u0010\u0015\u001a\u000e\u0012\u0004\u0012\u00020\u0006\u0012\u0004\u0012\u00020\u00010\u0005H\u0007\u001a\u0010\u0010\u0016\u001a\u00020\u00012\u0006\u0010\u0017\u001a\u00020\u000eH\u0007\u00a8\u0006\u0018"}, d2 = {"GhostPinScreen", "", "viewModel", "Lcom/ghostpin/app/ui/SimulationViewModel;", "onStartSimulation", "Lkotlin/Function1;", "Lcom/ghostpin/core/model/MovementProfile;", "onStopSimulation", "Lkotlin/Function0;", "GhostPinTheme", "content", "Landroidx/compose/runtime/Composable;", "InteractiveMap", "simulationState", "Lcom/ghostpin/app/service/SimulationState;", "modifier", "Landroidx/compose/ui/Modifier;", "ProfileSelector", "profiles", "", "selected", "onSelect", "StatusCard", "state", "app_nonplayDebug"})
public final class MainActivityKt {
    
    @androidx.compose.runtime.Composable()
    public static final void GhostPinTheme(@org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> content) {
    }
    
    @kotlin.OptIn(markerClass = {androidx.compose.material3.ExperimentalMaterial3Api.class})
    @androidx.compose.runtime.Composable()
    public static final void GhostPinScreen(@org.jetbrains.annotations.NotNull()
    com.ghostpin.app.ui.SimulationViewModel viewModel, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function1<? super com.ghostpin.core.model.MovementProfile, kotlin.Unit> onStartSimulation, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> onStopSimulation) {
    }
    
    @androidx.compose.runtime.Composable()
    public static final void StatusCard(@org.jetbrains.annotations.NotNull()
    com.ghostpin.app.service.SimulationState state) {
    }
    
    @androidx.compose.runtime.Composable()
    public static final void ProfileSelector(@org.jetbrains.annotations.NotNull()
    java.util.List<com.ghostpin.core.model.MovementProfile> profiles, @org.jetbrains.annotations.NotNull()
    com.ghostpin.core.model.MovementProfile selected, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function1<? super com.ghostpin.core.model.MovementProfile, kotlin.Unit> onSelect) {
    }
    
    @androidx.compose.runtime.Composable()
    public static final void InteractiveMap(@org.jetbrains.annotations.NotNull()
    com.ghostpin.app.ui.SimulationViewModel viewModel, @org.jetbrains.annotations.NotNull()
    com.ghostpin.app.service.SimulationState simulationState, @org.jetbrains.annotations.NotNull()
    androidx.compose.ui.Modifier modifier) {
    }
}