package com.ghostpin.app.di

import com.ghostpin.app.location.FakeLocationInjector
import com.ghostpin.app.location.LocationInjector
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [LocationModule::class],
)
object FakeLocationModule {
    @Provides
    @Singleton
    fun provideFakeLocationInjector(): FakeLocationInjector = FakeLocationInjector()

    @Provides
    @Singleton
    fun provideLocationInjector(fakeLocationInjector: FakeLocationInjector,): LocationInjector = fakeLocationInjector
}
