package com.ghostpin.app.di;

import com.ghostpin.core.model.MovementProfile;
import com.ghostpin.engine.noise.LayeredNoiseModel;
import com.ghostpin.engine.validation.TrajectoryValidator;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import javax.inject.Singleton;

/**
 * Hilt module providing simulation engine dependencies.
 *
 * The LayeredNoiseModel is provided as a factory since it needs to be
 * reconfigured when the user switches profiles. The [TrajectoryValidator]
 * is stateless and can be a singleton.
 */
@dagger.Module()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c7\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u0003\u001a\u00020\u0004H\u0007J\b\u0010\u0005\u001a\u00020\u0006H\u0007\u00a8\u0006\u0007"}, d2 = {"Lcom/ghostpin/app/di/SimulationModule;", "", "()V", "provideLayeredNoiseModel", "Lcom/ghostpin/engine/noise/LayeredNoiseModel;", "provideTrajectoryValidator", "Lcom/ghostpin/engine/validation/TrajectoryValidator;", "app_nonplayDebug"})
@dagger.hilt.InstallIn(value = {dagger.hilt.components.SingletonComponent.class})
public final class SimulationModule {
    @org.jetbrains.annotations.NotNull()
    public static final com.ghostpin.app.di.SimulationModule INSTANCE = null;
    
    private SimulationModule() {
        super();
    }
    
    @dagger.Provides()
    @javax.inject.Singleton()
    @org.jetbrains.annotations.NotNull()
    public final com.ghostpin.engine.validation.TrajectoryValidator provideTrajectoryValidator() {
        return null;
    }
    
    /**
     * Provides a default LayeredNoiseModel configured for Pedestrian.
     * The ViewModel will swap this out when profiles change.
     */
    @dagger.Provides()
    @org.jetbrains.annotations.NotNull()
    public final com.ghostpin.engine.noise.LayeredNoiseModel provideLayeredNoiseModel() {
        return null;
    }
}