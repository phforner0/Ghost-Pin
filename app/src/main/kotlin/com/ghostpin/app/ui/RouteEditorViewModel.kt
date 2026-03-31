package com.ghostpin.app.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostpin.app.data.RouteRepository
import com.ghostpin.app.routing.RouteFileExporter
import com.ghostpin.app.routing.RouteFileParser
import com.ghostpin.app.routing.RouteImportValidator
import com.ghostpin.core.math.GeoMath
import com.ghostpin.core.model.Route
import com.ghostpin.core.model.Segment
import com.ghostpin.core.model.SegmentOverrides
import com.ghostpin.core.model.Waypoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for the Route Editor screen.
 *
 * Sprint 4 — Task 17.
 *
 * Manages:
 *  - An ordered list of [Waypoint]s the user builds by tapping the map.
 *  - Per-segment overrides: speed, pause duration, loop.
 *  - Route name editing.
 *  - Save / delete / import / export via [RouteRepository] and [RouteFileParser]/[RouteFileExporter].
 *
 * The editor always operates on a mutable in-memory route. The user explicitly
 * presses "Save" to persist to Room — no auto-save.
 */
@HiltViewModel
class RouteEditorViewModel
    @Inject
    constructor(
        private val routeRepository: RouteRepository,
        private val parser: RouteFileParser,
        private val exporter: RouteFileExporter,
    ) : ViewModel() {
        // ── Editor state ─────────────────────────────────────────────────────────

        data class EditorState(
            val routeId: String = UUID.randomUUID().toString(),
            val routeName: String = "New Route",
            val waypoints: List<Waypoint> = emptyList(),
            val segmentOverrides: Map<Int, SegmentOverrides> = emptyMap(), // index = fromIndex
            val isSaving: Boolean = false,
            val error: String? = null,
            val exportContent: Pair<String, String>? = null, // Pair(filename, content)
        ) {
            val canSave: Boolean get() = waypoints.size >= 2
            val canSimulate: Boolean get() = waypoints.size >= 2
        }

        private val _state = MutableStateFlow(EditorState())
        val state: StateFlow<EditorState> = _state.asStateFlow()

        /** All persisted routes, observed live from Room. */
        val savedRoutes = routeRepository.observeAll()

        // ── Waypoint editing ─────────────────────────────────────────────────────

        /** Add a waypoint at the end of the current route. */
        fun addWaypoint(
            lat: Double,
            lng: Double,
            label: String? = null
        ) {
            val wp = Waypoint(lat = lat, lng = lng, label = label)
            _state.value =
                _state.value.copy(
                    waypoints = _state.value.waypoints + wp,
                    error = null,
                )
        }

        /** Insert a waypoint at a specific index. */
        fun insertWaypoint(
            index: Int,
            lat: Double,
            lng: Double
        ) {
            val waypoints = _state.value.waypoints.toMutableList()
            val clamped = index.coerceIn(0, waypoints.size)
            waypoints.add(clamped, Waypoint(lat, lng))
            _state.value = _state.value.copy(waypoints = waypoints)
        }

        /** Move an existing waypoint to new coordinates (e.g. after drag on map). */
        fun updateWaypoint(
            index: Int,
            lat: Double,
            lng: Double
        ) {
            val waypoints = _state.value.waypoints.toMutableList()
            if (index !in waypoints.indices) return
            waypoints[index] = waypoints[index].copy(lat = lat, lng = lng)
            _state.value = _state.value.copy(waypoints = waypoints)
        }

        /** Remove a waypoint by index. Also clears overrides referencing that segment. */
        fun removeWaypoint(index: Int) {
            val waypoints = _state.value.waypoints.toMutableList()
            if (index !in waypoints.indices) return
            waypoints.removeAt(index)
            // Shift segment override indices
            val shifted =
                _state.value.segmentOverrides
                    .filterKeys { it != index }
                    .mapKeys { (k, _) -> if (k > index) k - 1 else k }
            _state.value = _state.value.copy(waypoints = waypoints, segmentOverrides = shifted)
        }

        /** Clear all waypoints and overrides (reset the editor). */
        fun clearRoute() {
            _state.value = EditorState(routeId = UUID.randomUUID().toString())
        }

        // ── Segment overrides ─────────────────────────────────────────────────────

        /**
         * Set per-segment overrides for the segment starting at [fromIndex].
         *
         * @param fromIndex The waypoint index that starts this segment.
         * @param speedOverrideMs Optional speed cap for this segment (m/s).
         * @param pauseDurationSec Optional pause duration at the end of this segment (seconds).
         * @param loop If true, the simulation loops back to the start when this segment ends.
         */
        fun setSegmentOverride(
            fromIndex: Int,
            speedOverrideMs: Double? = null,
            pauseDurationSec: Double? = null,
            loop: Boolean = false,
        ) {
            val overrides = _state.value.segmentOverrides.toMutableMap()
            val override = SegmentOverrides(speedOverrideMs, pauseDurationSec, loop)
            if (speedOverrideMs == null && pauseDurationSec == null && !loop) {
                overrides.remove(fromIndex) // Clear override if nothing set
            } else {
                overrides[fromIndex] = override
            }
            _state.value = _state.value.copy(segmentOverrides = overrides)
        }

        /** Clear all overrides for a segment. */
        fun clearSegmentOverride(fromIndex: Int) {
            val overrides = _state.value.segmentOverrides.toMutableMap()
            overrides.remove(fromIndex)
            _state.value = _state.value.copy(segmentOverrides = overrides)
        }

        // ── Route name ────────────────────────────────────────────────────────────

        fun setRouteName(name: String) {
            _state.value = _state.value.copy(routeName = name.trim().ifBlank { "New Route" })
        }

        // ── Save / Load ───────────────────────────────────────────────────────────

        /**
         * Save the current editor state to Room.
         *
         * Builds a [Route] from the current waypoints and overrides,
         * runs [TrajectoryValidator]-style sanity checks inline,
         * then persists via [RouteRepository].
         */
        fun save() {
            val current = _state.value
            if (!current.canSave) {
                _state.value = current.copy(error = "Need at least 2 waypoints to save.")
                return
            }

            _state.value = current.copy(isSaving = true, error = null)

            viewModelScope.launch {
                runCatching {
                    val route = buildRoute(current)
                    routeRepository.save(route, "manual")
                    route
                }.onSuccess {
                    _state.value = _state.value.copy(isSaving = false)
                }.onFailure { e ->
                    _state.value = _state.value.copy(isSaving = false, error = e.message)
                }
            }
        }

        /**
         * Load a saved route into the editor for modification.
         *
         * @param routeId Room entity ID of the route to load.
         */
        fun loadRoute(routeId: String) {
            viewModelScope.launch {
                val route =
                    routeRepository.getById(routeId) ?: run {
                        _state.value = _state.value.copy(error = "Route not found.")
                        return@launch
                    }
                val overrides =
                    route.segments
                        .filter { it.overrides != null }
                        .associate { it.fromIndex to it.overrides!! }

                _state.value =
                    EditorState(
                        routeId = route.id,
                        routeName = route.name,
                        waypoints = route.waypoints,
                        segmentOverrides = overrides,
                    )
            }
        }

        /** Delete a saved route by ID. Does not affect the current editor state. */
        fun deleteRoute(routeId: String) {
            viewModelScope.launch {
                routeRepository.deleteById(routeId)
            }
        }

        // ── Import ────────────────────────────────────────────────────────────────

        /**
         * Import a route from a GPS file URI (GPX, KML, or TCX).
         *
         * On success, loads the parsed route into the editor.
         * On failure, sets [EditorState.error] with a user-friendly message.
         */
        fun importFromUri(
            context: Context,
            uri: Uri
        ) {
            viewModelScope.launch {
                runCatching {
                    val validUri = RouteImportValidator.validateUri(uri).getOrThrow()
                    val displayName = RouteImportValidator.resolveDisplayName(context.contentResolver, validUri)
                    context.contentResolver.openInputStream(validUri)?.use { input ->
                        parser.parse(input, displayName).getOrThrow()
                    } ?: error("Cannot open route file")
                }.onSuccess { route ->
                    _state.value =
                        EditorState(
                            routeId = route.id,
                            routeName = route.name,
                            waypoints = route.waypoints,
                            error = null,
                        )
                }.onFailure { e ->
                    _state.value = _state.value.copy(error = "Import failed: ${e.message}")
                }
            }
        }

        // ── Export ────────────────────────────────────────────────────────────────

        /**
         * Prepare the current route for export.
         *
         * Stores the export content in [EditorState.exportContent] as a Pair(filename, content).
         * The UI observes this and triggers a share/save dialog.
         *
         * @param format Desired export format.
         */
        fun prepareExport(format: RouteFileParser.RouteFormat) {
            val current = _state.value
            if (!current.canSave) {
                _state.value = current.copy(error = "Need at least 2 waypoints to export.")
                return
            }

            val route = buildRoute(current)
            val (ext, content) =
                when (format) {
                    RouteFileParser.RouteFormat.GPX -> "gpx" to exporter.toGpx(route)
                    RouteFileParser.RouteFormat.KML -> "kml" to exporter.toKml(route)
                    RouteFileParser.RouteFormat.TCX -> "tcx" to exporter.toTcx(route)
                }
            val filename = "${route.name.replace(" ", "_")}.$ext"
            _state.value = current.copy(exportContent = filename to content)
        }

        /** Clear the pending export after the UI has handled it. */
        fun clearExport() {
            _state.value = _state.value.copy(exportContent = null)
        }

        // ── Build domain Route ────────────────────────────────────────────────────

        /**
         * Construct the current [Route] domain model from editor state.
         * This is the route that gets saved and used for simulation.
         */
        fun buildCurrentRoute(): Route? {
            val current = _state.value
            if (!current.canSave) return null
            return buildRoute(current)
        }

        private fun buildRoute(state: EditorState): Route {
            val waypoints = state.waypoints
            val segments =
                (0 until waypoints.size - 1).map { i ->
                    val override = state.segmentOverrides[i]
                    val dist = haversine(waypoints[i], waypoints[i + 1])
                    Segment(
                        id = "seg_$i",
                        fromIndex = i,
                        toIndex = i + 1,
                        distance = dist,
                        overrides = override,
                    )
                }
            return Route(
                id = state.routeId,
                name = state.routeName,
                waypoints = waypoints,
                segments = segments,
            )
        }

        // ── Haversine ─────────────────────────────────────────────────────────────

        private fun haversine(
            a: Waypoint,
            b: Waypoint
        ): Double = GeoMath.haversineMeters(a.lat, a.lng, b.lat, b.lng)
    }
