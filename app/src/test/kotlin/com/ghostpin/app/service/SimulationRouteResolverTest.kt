package com.ghostpin.app.service

import com.ghostpin.app.data.RouteRepository
import com.ghostpin.app.data.SimulationConfig
import com.ghostpin.app.data.SimulationRepository
import com.ghostpin.app.data.db.FavoriteSimulationDao
import com.ghostpin.app.data.db.FavoriteSimulationEntity
import com.ghostpin.app.data.db.ProfileDao
import com.ghostpin.app.data.db.ProfileEntity
import com.ghostpin.app.data.db.RouteDao
import com.ghostpin.app.data.db.RouteEntity
import com.ghostpin.app.data.db.SimulationHistoryDao
import com.ghostpin.app.data.db.SimulationHistoryEntity
import com.ghostpin.app.routing.OsrmRouteProvider
import com.ghostpin.core.model.AppMode
import com.ghostpin.core.model.MovementProfile
import com.ghostpin.core.model.Route
import com.ghostpin.core.model.Waypoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SimulationRouteResolverTest {

    @Test
    fun `gpx mode falls back to cached config waypoints when no route is emitted`() =
        runTest {
            val simulationRepository = buildRepository()
            val routeRepository = RouteRepository(FakeRouteDao())

            val result =
                resolveSimulationRoute(
                    request =
                        SimulationRouteRequest(
                            profile = MovementProfile.PEDESTRIAN,
                            startLat = -23.0,
                            startLng = -46.0,
                            endLat = -22.0,
                            endLng = -43.0,
                            appMode = AppMode.GPX,
                            waypoints = emptyList(),
                            resumeState = null,
                            cachedRoute = null,
                            persistedRouteId = null,
                            cachedConfigWaypoints = listOf(Waypoint(-23.0, -46.0), Waypoint(-22.0, -43.0)),
                        ),
                    routeRepository = routeRepository,
                    simulationRepository = simulationRepository,
                    osrmRouteProvider = OsrmRouteProvider(),
                    loggerTag = "SimulationRouteResolverTest",
                )

            assertTrue(result is SimulationRouteResult.Success)
            val route = (result as SimulationRouteResult.Success).route
            assertEquals("Saved GPX Route", route.name)
            assertEquals(2, route.waypoints.size)
        }

    @Test
    fun `persisted route id missing returns explicit error`() =
        runTest {
            val simulationRepository = buildRepository()
            val routeRepository = RouteRepository(FakeRouteDao())

            val result =
                resolveSimulationRoute(
                    request =
                        SimulationRouteRequest(
                            profile = MovementProfile.CAR,
                            startLat = -23.0,
                            startLng = -46.0,
                            endLat = -22.0,
                            endLng = -43.0,
                            appMode = AppMode.CLASSIC,
                            waypoints = emptyList(),
                            resumeState = null,
                            cachedRoute = null,
                            persistedRouteId = "missing-route",
                            cachedConfigWaypoints = emptyList(),
                        ),
                    routeRepository = routeRepository,
                    simulationRepository = simulationRepository,
                    osrmRouteProvider = OsrmRouteProvider(),
                    loggerTag = "SimulationRouteResolverTest",
                )

            assertTrue(result is SimulationRouteResult.Error)
            assertEquals(
                "Saved route not found for routeId 'missing-route'.",
                (result as SimulationRouteResult.Error).message,
            )
        }

    @Test
    fun `joystick mode returns joystick result without route lookup`() =
        runTest {
            val simulationRepository = buildRepository()
            val routeRepository = RouteRepository(FakeRouteDao())

            val result =
                resolveSimulationRoute(
                    request =
                        SimulationRouteRequest(
                            profile = MovementProfile.PEDESTRIAN,
                            startLat = 1.0,
                            startLng = 2.0,
                            endLat = 3.0,
                            endLng = 4.0,
                            appMode = AppMode.JOYSTICK,
                            waypoints = emptyList(),
                            resumeState = null,
                            cachedRoute = null,
                            persistedRouteId = null,
                            cachedConfigWaypoints = emptyList(),
                        ),
                    routeRepository = routeRepository,
                    simulationRepository = simulationRepository,
                    osrmRouteProvider = OsrmRouteProvider(),
                    loggerTag = "SimulationRouteResolverTest",
                )

            assertTrue(result is SimulationRouteResult.Joystick)
            val joystick = result as SimulationRouteResult.Joystick
            assertEquals(1.0, joystick.startLat, 0.0)
            assertEquals(2.0, joystick.startLng, 0.0)
        }

    private fun buildRepository(): SimulationRepository {
        val routeDao = FakeRouteDao()
        return SimulationRepository(
            simulationHistoryDao = FakeSimulationHistoryDao(),
            favoriteSimulationDao = FakeFavoriteSimulationDao(),
            routeDao = routeDao,
            profileDao = FakeProfileDao(),
        ).also {
            it.emitConfig(
                SimulationConfig(
                    profileName = MovementProfile.PEDESTRIAN.name,
                    profileLookupKey = MovementProfile.PEDESTRIAN.name,
                    startLat = -23.0,
                    startLng = -46.0,
                )
            )
        }
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
        override suspend fun insert(entity: SimulationHistoryEntity) = Unit
        override suspend fun listPaged(limit: Int, offset: Int): List<SimulationHistoryEntity> = emptyList()
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
