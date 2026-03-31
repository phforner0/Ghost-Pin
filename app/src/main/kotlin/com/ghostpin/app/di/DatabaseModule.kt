package com.ghostpin.app.di

import android.content.Context
import androidx.room.Room
import com.ghostpin.app.data.db.FavoriteSimulationDao
import com.ghostpin.app.data.db.GhostPinDatabase
import com.ghostpin.app.data.db.ProfileDao
import com.ghostpin.app.data.db.RouteDao
import com.ghostpin.app.data.db.SimulationHistoryDao
import com.ghostpin.app.scheduling.ScheduleDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

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
            .addMigrations(
                GhostPinDatabase.MIGRATION_1_2,
                GhostPinDatabase.MIGRATION_2_3,
                GhostPinDatabase.MIGRATION_3_4,
                GhostPinDatabase.MIGRATION_4_5,
            )
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

    @Provides
    @Singleton
    fun provideScheduleDao(db: GhostPinDatabase): ScheduleDao = db.scheduleDao()
}
