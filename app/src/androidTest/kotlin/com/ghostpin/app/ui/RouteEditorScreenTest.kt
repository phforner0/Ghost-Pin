package com.ghostpin.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ghostpin.app.data.RouteRepository
import com.ghostpin.app.data.db.RouteDao
import com.ghostpin.app.data.db.RouteEntity
import com.ghostpin.app.routing.RouteFileExporter
import com.ghostpin.app.routing.RouteFileParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.Rule
import org.junit.Test

class RouteEditorScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun routeEditor_showsDraftAndPersistedStates() {
        val dao = FakeRouteDao()
        val viewModel = RouteEditorViewModel(RouteRepository(dao), RouteFileParser(), RouteFileExporter())
        viewModel.addWaypoint(-23.0, -46.0)
        viewModel.addWaypoint(-22.0, -43.0)

        composeRule.setContent {
            GhostPinTheme {
                RouteEditorScreen(
                    viewModel = viewModel,
                    onBack = {},
                    onRouteReady = {},
                )
            }
        }

        composeRule.onNodeWithText("Unsaved").assertExists()
        composeRule.onNodeWithText("Start with current draft").assertExists()
        composeRule.onNodeWithText("Save").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            kotlin
                .runCatching {
                    composeRule.onNodeWithText("Persisted").assertExists()
                    true
                }.getOrDefault(false)
        }
        composeRule.onNodeWithText("Start with saved route").assertExists()
    }

    private class FakeRouteDao : RouteDao {
        private val routes = MutableStateFlow<List<RouteEntity>>(emptyList())

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
