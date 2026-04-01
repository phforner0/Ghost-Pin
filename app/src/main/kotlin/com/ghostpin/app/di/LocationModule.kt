package com.ghostpin.app.di

import com.ghostpin.app.location.LocationInjector
import com.ghostpin.app.location.MockLocationInjector
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LocationModule {
    @Binds
    @Singleton
    abstract fun bindLocationInjector(injector: MockLocationInjector,): LocationInjector
}
