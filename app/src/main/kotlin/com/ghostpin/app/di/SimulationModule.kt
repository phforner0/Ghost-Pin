package com.ghostpin.app.di

import com.ghostpin.app.routing.OsrmRouteProvider
import com.ghostpin.core.model.MovementProfile
import com.ghostpin.engine.noise.LayeredNoiseModel
import com.ghostpin.engine.validation.TrajectoryValidator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing simulation engine dependencies.
 *
 * Sprint 2: [OsrmRouteProvider] is annotated [@Singleton] + [@Inject constructor]
 * so Hilt resolves it automatically — no explicit @Provides needed.
 * It is listed here only for documentation clarity.
 *
 * [LayeredNoiseModel] is intentionally NOT a singleton: each simulation
 * creates a fresh instance via [LayeredNoiseModel.fromProfile] so that
 * the OU state resets cleanly between runs.
 */
@Module
@InstallIn(SingletonComponent::class)
object SimulationModule {

    @Provides
    @Singleton
    fun provideTrajectoryValidator(): TrajectoryValidator = TrajectoryValidator()

    /**
     * Default noise model (Pedestrian). The service overrides this per-profile
     * by calling [LayeredNoiseModel.fromProfile] directly — this binding exists
     * so injection points that want a default always get a valid instance.
     */
    @Provides
    fun provideLayeredNoiseModel(): LayeredNoiseModel =
        LayeredNoiseModel.fromProfile(MovementProfile.PEDESTRIAN)
}
