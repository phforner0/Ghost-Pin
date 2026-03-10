package com.ghostpin.app.di

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
 * The LayeredNoiseModel is provided as a factory since it needs to be
 * reconfigured when the user switches profiles. The [TrajectoryValidator]
 * is stateless and can be a singleton.
 */
@Module
@InstallIn(SingletonComponent::class)
object SimulationModule {

    @Provides
    @Singleton
    fun provideTrajectoryValidator(): TrajectoryValidator {
        return TrajectoryValidator()
    }

    /**
     * Provides a default LayeredNoiseModel configured for Pedestrian.
     * The ViewModel will swap this out when profiles change.
     */
    @Provides
    fun provideLayeredNoiseModel(): LayeredNoiseModel {
        return LayeredNoiseModel.fromProfile(MovementProfile.PEDESTRIAN)
    }
}
