package com.ghostpin.app.service

import android.content.Intent
import com.ghostpin.app.data.SimulationRepository
import com.ghostpin.app.data.db.FavoriteSimulationDao
import com.ghostpin.app.data.db.FavoriteSimulationEntity
import com.ghostpin.app.data.db.ProfileDao
import com.ghostpin.app.data.db.ProfileEntity
import com.ghostpin.app.data.db.RouteDao
import com.ghostpin.app.data.db.RouteEntity
import com.ghostpin.app.data.db.SimulationHistoryDao
import com.ghostpin.app.data.db.SimulationHistoryEntity
import com.ghostpin.core.model.AppMode
import com.ghostpin.core.model.MovementProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SimulationStartRequestTest {
    @Test
    fun `parseSimulationStartRequest parses valid waypoint mode request`() =
        runTest {
            val repository =
                SimulationRepository(
                    simulationHistoryDao = FakeSimulationHistoryDao(),
                    favoriteSimulationDao = FakeFavoriteSimulationDao(),
                    routeDao = FakeRouteDao(),
                    profileDao = FakeProfileDao(),
                )

            val intent =
                Intent().apply {
                    putExtra(SimulationService.EXTRA_PROFILE_NAME, MovementProfile.CAR.name)
                    putExtra(SimulationService.EXTRA_START_LAT, -23.0)
                    putExtra(SimulationService.EXTRA_START_LNG, -46.0)
                    putExtra(SimulationService.EXTRA_END_LAT, -22.0)
                    putExtra(SimulationService.EXTRA_END_LNG, -43.0)
                    putExtra(SimulationService.EXTRA_FREQUENCY_HZ, 12)
                    putExtra(SimulationService.EXTRA_SPEED_RATIO, 0.75)
                    putExtra(SimulationService.EXTRA_MODE, AppMode.WAYPOINTS.name)
                    putExtra(SimulationService.EXTRA_WAYPOINT_PAUSE_SEC, 4.0)
                    putExtra(
                        SimulationService.EXTRA_REPEAT_POLICY,
                        com.ghostpin.engine.interpolation.RepeatPolicy.LOOP_N.name
                    )
                    putExtra(SimulationService.EXTRA_REPEAT_COUNT, 3)
                    putExtra(SimulationService.EXTRA_WAYPOINTS_LAT, doubleArrayOf(-23.0, -22.5, -22.0))
                    putExtra(SimulationService.EXTRA_WAYPOINTS_LNG, doubleArrayOf(-46.0, -44.0, -43.0))
                }

            val result =
                parseSimulationStartRequest(
                    intent = intent,
                    repository = repository,
                    resolveProfile = { MovementProfile.BUILT_IN[it] },
                    defaultFrequency = SimulationService.DEFAULT_FREQUENCY,
                    minFrequency = 1,
                    maxFrequency = 60,
                ).getOrThrow()

            assertEquals(MovementProfile.CAR.name, result.profile.name)
            assertEquals(AppMode.WAYPOINTS, result.appMode)
            assertEquals(12, result.frequencyHz)
            assertEquals(0.75, result.speedRatio, 0.0)
            assertEquals(4.0, result.waypointPauseSec, 0.0)
            assertEquals(3, result.repeatCount)
            assertEquals(3, result.waypoints.size)
            assertTrue(!result.isResume)
        }

    @Test
    fun `parseSimulationStartRequest rejects waypoint mode with fewer than two points`() =
        runTest {
            val repository =
                SimulationRepository(
                    simulationHistoryDao = FakeSimulationHistoryDao(),
                    favoriteSimulationDao = FakeFavoriteSimulationDao(),
                    routeDao = FakeRouteDao(),
                    profileDao = FakeProfileDao(),
                )

            val intent =
                Intent().apply {
                    putExtra(SimulationService.EXTRA_MODE, AppMode.WAYPOINTS.name)
                    putExtra(SimulationService.EXTRA_WAYPOINTS_LAT, doubleArrayOf(-23.0))
                    putExtra(SimulationService.EXTRA_WAYPOINTS_LNG, doubleArrayOf(-46.0))
                }

            val result =
                parseSimulationStartRequest(
                    intent = intent,
                    repository = repository,
                    resolveProfile = { MovementProfile.BUILT_IN[it] },
                    defaultFrequency = SimulationService.DEFAULT_FREQUENCY,
                    minFrequency = 1,
                    maxFrequency = 60,
                )

            assertTrue(result.isFailure)
            assertEquals(
                "Add at least 2 waypoints to start multi-stop mode.",
                result.exceptionOrNull()?.message,
            )
        }

    private class FakeFavoriteSimulationDao : FavoriteSimulationDao {
        override suspend fun upsert(entity: FavoriteSimulationEntity) = Unit

        override suspend fun update(entity: FavoriteSimulationEntity) = Unit

        override fun observeAll(): Flow<List<FavoriteSimulationEntity>> = emptyFlow()

        override suspend fun listRecent(): List<FavoriteSimulationEntity> = emptyList()

        override suspend fun getById(id: String): FavoriteSimulationEntity? = null

        override suspend fun getMostRecent(): FavoriteSimulationEntity? = null

        override suspend fun deleteById(id: String) = Unit
    }

    private class FakeRouteDao : RouteDao {
        override fun observeAll(): Flow<List<RouteEntity>> = emptyFlow()

        override suspend fun getById(id: String): RouteEntity? = null

        override suspend fun insert(route: RouteEntity) = Unit

        override suspend fun update(route: RouteEntity) = Unit

        override suspend fun deleteById(id: String) = Unit

        override suspend fun count(): Int = 0
    }

    private class FakeProfileDao : ProfileDao {
        override fun observeAll(): Flow<List<ProfileEntity>> = emptyFlow()

        override suspend fun getAll(): List<ProfileEntity> = emptyList()

        override suspend fun getById(id: String): ProfileEntity? = null

        override suspend fun getByName(name: String): ProfileEntity? = null

        override suspend fun countCustom(): Int = 0

        override suspend fun insert(profile: ProfileEntity) = Unit

        override suspend fun insertAll(profiles: List<ProfileEntity>) = Unit

        override suspend fun update(profile: ProfileEntity) = Unit

        override suspend fun deleteById(id: String) = Unit
    }

    private class FakeSimulationHistoryDao : SimulationHistoryDao {
        override suspend fun insert(entity: SimulationHistoryEntity) = Unit

        override suspend fun listPaged(
            limit: Int,
            offset: Int
        ): List<SimulationHistoryEntity> = emptyList()

        override suspend fun getById(id: String): SimulationHistoryEntity? = null

        override suspend fun closeById(
            id: String,
            endedAtMs: Long,
            durationMs: Long,
            avgSpeedMs: Double,
            distanceMeters: Double,
            resultStatus: String,
        ) = Unit

        override suspend fun deleteById(id: String) = Unit

        override suspend fun clearHistory() = Unit
    }
}
