package com.ghostpin.app.di

import com.ghostpin.app.data.DataStorePausedSimulationStore
import com.ghostpin.app.data.DataStoreSimulationConfigStore
import com.ghostpin.app.data.PausedSimulationStore
import com.ghostpin.app.data.SimulationConfigStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PersistenceModule {
    @Binds
    @Singleton
    abstract fun bindSimulationConfigStore(store: DataStoreSimulationConfigStore,): SimulationConfigStore

    @Binds
    @Singleton
    abstract fun bindPausedSimulationStore(store: DataStorePausedSimulationStore,): PausedSimulationStore
}
