package com.ghostpin.app.ui

import android.net.Uri
import com.ghostpin.app.data.RouteRepository
import com.ghostpin.app.data.db.RouteDao
import com.ghostpin.app.data.db.RouteEntity
import com.ghostpin.app.routing.RouteFileExporter
import com.ghostpin.app.routing.RouteFileParser
import com.ghostpin.core.model.Waypoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class RouteEditorViewModelTest {
    @Test
    fun `save marks route as persisted and sets info message`() =
        runTest {
            val dao = FakeRouteDao()
            val viewModel = RouteEditorViewModel(RouteRepository(dao), RouteFileParser(), RouteFileExporter())

            viewModel.addWaypoint(-23.0, -46.0)
            viewModel.addWaypoint(-22.0, -43.0)
            viewModel.save()

            kotlinx.coroutines.delay(50)

            assertEquals(RouteEditorViewModel.RouteEditorOrigin.SAVED, viewModel.state.value.origin)
            assertNotNull(viewModel.state.value.persistedRouteId)
            assertEquals("Route saved.", viewModel.state.value.infoMessage)
            assertEquals(1, dao.routes.value.size)
        }

    @Test
    fun `import marks route as imported and summary describes draft start path`() =
        runTest {
            val dao = FakeRouteDao()
            val viewModel = RouteEditorViewModel(RouteRepository(dao), RouteFileParser(), RouteFileExporter())
            val context = RuntimeEnvironment.getApplication()
            val routeFile = File(context.cacheDir, "route-editor-import.gpx")
            routeFile.writeText(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <gpx version="1.1" creator="test">
                  <trk><name>Imported Route</name><trkseg>
                    <trkpt lat="-23.0" lon="-46.0" />
                    <trkpt lat="-22.999" lon="-45.999" />
                  </trkseg></trk>
                </gpx>
                """.trimIndent()
            )

            viewModel.importFromUri(context, Uri.fromFile(routeFile))

            kotlinx.coroutines.delay(50)

            assertEquals(RouteEditorViewModel.RouteEditorOrigin.IMPORTED, viewModel.state.value.origin)
            assertTrue(
                viewModel.state.value.infoMessage!!
                    .contains("Imported route")
            )
            val summary = viewModel.buildRouteSummary()
            assertEquals("Imported", summary?.statusLabel)
            assertEquals("Start with current draft", summary?.startActionLabel)
        }

    @Test
    fun `load and delete saved route preserves draft with clear message`() =
        runTest {
            val dao = FakeRouteDao()
            val repository = RouteRepository(dao)
            val viewModel = RouteEditorViewModel(repository, RouteFileParser(), RouteFileExporter())

            repository.save(
                com.ghostpin.core.model.Route(
                    id = "route-1",
                    name = "Saved Route",
                    waypoints = listOf(Waypoint(-23.0, -46.0), Waypoint(-22.0, -43.0)),
                )
            )

            viewModel.loadRoute("route-1")
            kotlinx.coroutines.delay(50)
            assertEquals(RouteEditorViewModel.RouteEditorOrigin.SAVED, viewModel.state.value.origin)

            viewModel.deleteRoute("route-1")
            kotlinx.coroutines.delay(50)

            assertEquals(RouteEditorViewModel.RouteEditorOrigin.DRAFT, viewModel.state.value.origin)
            assertEquals(null, viewModel.state.value.persistedRouteId)
            assertEquals("Saved route deleted. Current draft preserved.", viewModel.state.value.infoMessage)
        }

    @Test
    fun `summary reports overrides and altitude source`() =
        runTest {
            val viewModel = RouteEditorViewModel(RouteRepository(FakeRouteDao()), RouteFileParser(), RouteFileExporter())

            viewModel.addWaypoint(-23.0, -46.0, null)
            viewModel.addWaypoint(-22.5, -44.0, null)
            viewModel.addWaypoint(-22.0, -43.0, null)
            viewModel.updateWaypoint(1, -22.5, -44.0)
            viewModel.setSegmentOverride(0, speedOverrideMs = 12.0)

            // rebuild with altitude present
            val current = viewModel.state.value
            val updatedWaypoints = current.waypoints.toMutableList()
            updatedWaypoints[1] = updatedWaypoints[1].copy(altitude = 50.0)
            val field = RouteEditorViewModel::class.java.getDeclaredField("_state")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val stateFlow = field.get(viewModel) as MutableStateFlow<RouteEditorViewModel.EditorState>
            stateFlow.value = current.copy(waypoints = updatedWaypoints)

            val summary = viewModel.buildRouteSummary()

            assertEquals(3, summary?.waypointCount)
            assertEquals(1, summary?.overrideCount)
            assertEquals("Altitude from route data", summary?.altitudeSourceLabel)
        }

    @Test
    fun `editing saved route detaches draft and saving preserves original route`() =
        runTest {
            val dao = FakeRouteDao()
            val repository = RouteRepository(dao)
            val viewModel = RouteEditorViewModel(repository, RouteFileParser(), RouteFileExporter())

            repository.save(
                com.ghostpin.core.model.Route(
                    id = "route-1",
                    name = "Saved Route",
                    waypoints = listOf(Waypoint(-23.0, -46.0), Waypoint(-22.0, -43.0)),
                )
            )

            viewModel.loadRoute("route-1")
            kotlinx.coroutines.delay(50)
            val originalRouteId = viewModel.state.value.routeId

            viewModel.setRouteName("Edited Draft")

            assertEquals(RouteEditorViewModel.RouteEditorOrigin.DRAFT, viewModel.state.value.origin)
            assertEquals(null, viewModel.state.value.persistedRouteId)
            assertTrue(viewModel.state.value.routeId != originalRouteId)

            viewModel.save()
            kotlinx.coroutines.delay(50)

            assertEquals(2, dao.routes.value.size)
            assertTrue(dao.routes.value.any { it.id == originalRouteId })
            assertTrue(dao.routes.value.any { it.name == "Edited Draft" })
        }

    private class FakeRouteDao : RouteDao {
        val routes = MutableStateFlow<List<RouteEntity>>(emptyList())

        override fun observeAll(): Flow<List<RouteEntity>> = routes.map { it }

        override suspend fun getById(id: String): RouteEntity? = routes.value.firstOrNull { it.id == id }

        override suspend fun insert(route: RouteEntity) {
            routes.value = routes.value.filterNot { it.id == route.id } + route
        }

        override suspend fun update(route: RouteEntity) {
            routes.value = routes.value.map { if (it.id == route.id) route else it }
        }

        override suspend fun deleteById(id: String) {
            routes.value = routes.value.filterNot { it.id == id }
        }

        override suspend fun count(): Int = routes.value.size
    }
}
