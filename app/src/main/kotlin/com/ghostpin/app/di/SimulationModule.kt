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
 * Architecture note (post-refactor):
 *  - [com.ghostpin.app.data.SimulationRepository] is annotated [@Singleton] +
 *    [@Inject constructor], so Hilt resolves it automatically — no explicit
 *    [@Provides] needed here.
 *  - [com.ghostpin.app.routing.OsrmRouteProvider] is also auto-provided.
 *
 * [TrajectoryValidator] lives in the :engine module (no Hilt dependency),
 * so it requires an explicit [@Provides] here.
 *
 * [LayeredNoiseModel] is intentionally NOT a singleton: each simulation
 * creates a fresh instance via [LayeredNoiseModel.fromProfile] so that
 * OU state resets cleanly between runs. The binding below provides a default
 * for injection points that need one.
 */
@Module
@InstallIn(SingletonComponent::class)
object SimulationModule {
    /**
     * Provides the trajectory validator used to pre-validate routes against
     * profile constraints before simulation starts.
     *
     * Singleton: stateless, safe to share.
     */
    @Provides
    @Singleton
    fun provideTrajectoryValidator(): TrajectoryValidator = TrajectoryValidator()

    /**
     * Default noise model (Pedestrian profile).
     *
     * The [com.ghostpin.app.service.SimulationService] overrides this per-profile
     * by calling [LayeredNoiseModel.fromProfile] directly — this binding exists
     * so any injection point that wants a default always receives a valid instance.
     */
    @Provides
    fun provideLayeredNoiseModel(): LayeredNoiseModel = LayeredNoiseModel.fromProfile(MovementProfile.PEDESTRIAN)
}
