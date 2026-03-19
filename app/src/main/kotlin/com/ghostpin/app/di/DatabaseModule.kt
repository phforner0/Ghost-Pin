package com.ghostpin.app.di

import android.content.Context
import androidx.room.Room
import com.ghostpin.app.data.db.GhostPinDatabase
import com.ghostpin.app.data.db.FavoriteSimulationDao
import com.ghostpin.app.data.db.ProfileDao
import com.ghostpin.app.data.db.RouteDao
import com.ghostpin.app.data.db.SimulationHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing the Room database and its DAOs.
 *
 * Sprint 4 — Task 14.
 *
 * The database is a singleton scoped to the application lifecycle.
 * Each DAO is also singleton — Room DAOs are thread-safe and stateless.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GhostPinDatabase =
        Room.databaseBuilder(
            context,
            GhostPinDatabase::class.java,
            "ghostpin.db",
        )
            .addMigrations(GhostPinDatabase.MIGRATION_1_2, GhostPinDatabase.MIGRATION_2_3)
            .build()

    @Provides
    @Singleton
    fun provideProfileDao(db: GhostPinDatabase): ProfileDao = db.profileDao()

    @Provides
    @Singleton
    fun provideRouteDao(db: GhostPinDatabase): RouteDao = db.routeDao()

    @Provides
    @Singleton
    fun provideSimulationHistoryDao(db: GhostPinDatabase): SimulationHistoryDao = db.simulationHistoryDao()

    @Provides
    @Singleton
    fun provideFavoriteSimulationDao(db: GhostPinDatabase): FavoriteSimulationDao = db.favoriteSimulationDao()
}
