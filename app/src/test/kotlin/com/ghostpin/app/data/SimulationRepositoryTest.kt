package com.ghostpin.app.data

import com.ghostpin.app.data.db.FavoriteSimulationDao
import com.ghostpin.app.data.db.FavoriteSimulationEntity
import com.ghostpin.app.data.db.ProfileDao
import com.ghostpin.app.data.db.ProfileEntity
import com.ghostpin.app.data.db.RouteDao
import com.ghostpin.app.data.db.RouteEntity
import com.ghostpin.app.data.db.SimulationHistoryDao
import com.ghostpin.app.data.db.SimulationHistoryEntity
import com.ghostpin.core.model.AppMode
import com.ghostpin.core.model.Waypoint
import com.ghostpin.engine.interpolation.RepeatPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulationRepositoryTest {
    @Test
    fun `resolveFavoriteConfig hydrates persisted route into config`() =
        runTest {
            val favoriteDao = FakeFavoriteSimulationDao()
            val routeDao =
                FakeRouteDao(
                    routes =
                        mutableMapOf(
                            "route-1" to
                                RouteEntity(
                                    id = "route-1",
                                    name = "Saved route",
                                    waypointsJson =
                                        SimulationConfig.serializeWaypoints(
                                            listOf(
                                                Waypoint(-23.0, -46.0),
                                                Waypoint(-22.0, -43.0),
                                            )
                                        ),
                                    segmentsJson = "[]",
                                    sourceFormat = "manual",
                                    totalDistanceMeters = 1000.0,
                                    createdAtMs = 1L,
                                    updatedAtMs = 1L,
                                )
                        )
                )
            val repository =
                SimulationRepository(
                    simulationHistoryDao = FakeSimulationHistoryDao(),
                    favoriteSimulationDao = favoriteDao,
                    routeDao = routeDao,
                    profileDao = FakeProfileDao(),
                )

            val favorite =
                FavoriteSimulationEntity(
                    id = "fav-1",
                    name = "Saved favorite",
                    profileName = "Car",
                    profileIdOrName = "Car",
                    routeId = "route-1",
                    startLat = 0.0,
                    startLng = 0.0,
                    endLat = 0.0,
                    endLng = 0.0,
                    appMode = AppMode.CLASSIC.name,
                    waypointsJson = "[]",
                    waypointPauseSec = 0.0,
                    speedRatio = 1.0,
                    frequencyHz = 5,
                    repeatPolicy = RepeatPolicy.NONE.name,
                    repeatCount = 1,
                    createdAtMs = 1L,
                    updatedAtMs = 1L,
                )

            val resolution = repository.resolveFavoriteConfig(favorite)

            assertTrue(resolution is SimulationRepository.FavoriteResolution.Valid)
            val config = (resolution as SimulationRepository.FavoriteResolution.Valid).config
            assertEquals(2, config.waypoints.size)
            assertEquals(-23.0, config.startLat, 0.0)
            assertEquals(-46.0, config.startLng, 0.0)
            assertEquals(-22.0, config.endLat, 0.0)
            assertEquals(-43.0, config.endLng, 0.0)
        }

    @Test
    fun `applyMostRecentFavorite does not invent fallback when nothing exists`() =
        runTest {
            val repository =
                SimulationRepository(
                    simulationHistoryDao = FakeSimulationHistoryDao(),
                    favoriteSimulationDao = FakeFavoriteSimulationDao(),
                    routeDao = FakeRouteDao(),
                    profileDao = FakeProfileDao(),
                )

            val resolution = repository.applyMostRecentFavorite()

            assertTrue(resolution is SimulationRepository.FavoriteResolution.Invalid)
            assertNull((resolution as SimulationRepository.FavoriteResolution.Invalid).fallbackConfig)
        }

    @Test
    fun `startHistory stores expanded session fields`() =
        runTest {
            val historyDao = FakeSimulationHistoryDao()
            val repository =
                SimulationRepository(
                    simulationHistoryDao = historyDao,
                    favoriteSimulationDao = FakeFavoriteSimulationDao(),
                    routeDao = FakeRouteDao(),
                    profileDao = FakeProfileDao(),
                )

            val config =
                SimulationConfig(
                    profileName = "Car",
                    profileLookupKey = "builtin_car",
                    startLat = -23.0,
                    startLng = -46.0,
                    endLat = -22.0,
                    endLng = -43.0,
                    routeId = "route-1",
                    appMode = AppMode.WAYPOINTS,
                    waypoints = listOf(Waypoint(-23.0, -46.0), Waypoint(-22.0, -43.0)),
                    waypointPauseSec = 3.0,
                    speedRatio = 0.75,
                    frequencyHz = 10,
                    repeatPolicy = RepeatPolicy.LOOP_N,
                    repeatCount = 2,
                )

            val historyId = repository.startHistory(config)
            val stored = historyDao.getById(historyId)!!

            assertEquals(config.profileName, stored.profileName)
            assertEquals(config.profileLookupKey, stored.profileIdOrName)
            assertEquals(config.routeId, stored.routeId)
            assertEquals(config.appMode.name, stored.appMode)
            assertEquals(config.waypoints.size, SimulationConfig.deserializeWaypoints(stored.waypointsJson).size)
            assertEquals(config.speedRatio, stored.speedRatio, 0.0)
            assertEquals(config.frequencyHz, stored.frequencyHz)
            assertEquals(config.repeatPolicy.name, stored.repeatPolicy)
            assertEquals(config.repeatCount, stored.repeatCount)
        }

    private class FakeFavoriteSimulationDao : FavoriteSimulationDao {
        private val favorites = mutableListOf<FavoriteSimulationEntity>()

        override suspend fun upsert(entity: FavoriteSimulationEntity) {
            favorites.removeAll { it.id == entity.id }
            favorites += entity
        }

        override suspend fun update(entity: FavoriteSimulationEntity) {
            upsert(entity)
        }

        override fun observeAll(): Flow<List<FavoriteSimulationEntity>> = flowOf(favorites.toList())

        override suspend fun listRecent(): List<FavoriteSimulationEntity> = favorites.sortedByDescending { it.updatedAtMs }

        override suspend fun getById(id: String): FavoriteSimulationEntity? = favorites.firstOrNull { it.id == id }

        override suspend fun getMostRecent(): FavoriteSimulationEntity? = favorites.maxByOrNull { it.updatedAtMs }

        override suspend fun deleteById(id: String) {
            favorites.removeAll { it.id == id }
        }
    }

    private class FakeRouteDao(
        private val routes: MutableMap<String, RouteEntity> = mutableMapOf(),
    ) : RouteDao {
        override fun observeAll(): Flow<List<RouteEntity>> = flowOf(routes.values.toList())

        override suspend fun getById(id: String): RouteEntity? = routes[id]

        override suspend fun insert(route: RouteEntity) {
            routes[route.id] = route
        }

        override suspend fun update(route: RouteEntity) {
            routes[route.id] = route
        }

        override suspend fun deleteById(id: String) {
            routes.remove(id)
        }

        override suspend fun count(): Int = routes.size
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
        private val history = linkedMapOf<String, SimulationHistoryEntity>()

        override suspend fun insert(entity: SimulationHistoryEntity) {
            history[entity.id] = entity
        }

        override suspend fun listPaged(
            limit: Int,
            offset: Int
        ): List<SimulationHistoryEntity> = history.values.drop(offset).take(limit)

        override suspend fun getById(id: String): SimulationHistoryEntity? = history[id]

        override suspend fun closeById(
            id: String,
            endedAtMs: Long,
            durationMs: Long,
            avgSpeedMs: Double,
            distanceMeters: Double,
            resultStatus: String,
        ) {
            val current = history[id] ?: return
            history[id] =
                current.copy(
                    endedAtMs = endedAtMs,
                    durationMs = durationMs,
                    avgSpeedMs = avgSpeedMs,
                    distanceMeters = distanceMeters,
                    resultStatus = resultStatus,
                )
        }

        override suspend fun deleteById(id: String) {
            history.remove(id)
        }

        override suspend fun clearHistory() {
            history.clear()
        }
    }
}
