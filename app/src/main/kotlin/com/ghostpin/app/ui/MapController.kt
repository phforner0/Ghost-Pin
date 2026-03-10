package com.ghostpin.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.ghostpin.core.model.MockLocation
import com.ghostpin.core.model.Route
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * Encapsulates all MapLibre layer management for the GhostPin simulation UI.
 *
 * Sprint 2 changes:
 *  - Bug #4: map style switched to OpenFreeMap Liberty (full street detail)
 *  - Bug #5: dedicated start / end pin layers with coloured circle icons
 *  - [updateRoute] now accepts a full [Route] and draws the OSRM polyline
 *  - [updateRoute] overload kept for backward-compat (two-point straight line)
 */
class MapController(
    private val map: MapLibreMap,
    @Suppress("unused") private val context: Context,
    private val onMapLongClick: (LatLng) -> Unit,
) {
    companion object {
        // Route polyline
        private const val SOURCE_ROUTE            = "source_route"
        private const val LAYER_ROUTE             = "layer_route"

        // Current position (animated dot)
        private const val SOURCE_POSITION         = "source_position"
        private const val LAYER_POSITION_ACCURACY = "layer_position_accuracy"
        private const val LAYER_POSITION_ICON     = "layer_position_icon"

        // Start pin  (Bug #5)
        private const val SOURCE_PIN_START        = "source_pin_start"
        private const val LAYER_PIN_START         = "layer_pin_start"

        // End pin  (Bug #5)
        private const val SOURCE_PIN_END          = "source_pin_end"
        private const val LAYER_PIN_END           = "layer_pin_end"

        // Icon image IDs
        private const val ICON_POSITION           = "icon_position"
        private const val ICON_PIN_START          = "icon_pin_start"
        private const val ICON_PIN_END            = "icon_pin_end"

        // Bug #4: full street tiles from OpenFreeMap (free, no API key)
        private const val MAP_STYLE_URL           =
            "https://tiles.openfreemap.org/styles/liberty"
    }

    private var style: Style? = null

    init {
        // Bug #4: was "https://demotiles.maplibre.org/style.json" (no streets)
        map.setStyle(Style.Builder().fromUri(MAP_STYLE_URL)) { loadedStyle ->
            this.style = loadedStyle
            setupLayers(loadedStyle)
            map.addOnMapLongClickListener { latLng ->
                onMapLongClick(latLng)
                true
            }
        }
    }

    // ── Layer setup ──────────────────────────────────────────────────────────

    private fun setupLayers(style: Style) {
        // ── Route polyline ────────────────────────────────────────────────
        style.addSource(GeoJsonSource(SOURCE_ROUTE))
        style.addLayer(
            LineLayer(LAYER_ROUTE, SOURCE_ROUTE).withProperties(
                lineColor("#80CBC4"),
                lineWidth(3.5f),
                lineOpacity(0.85f),
            )
        )

        // ── Start pin  (Bug #5: green circle with white border) ───────────
        style.addSource(GeoJsonSource(SOURCE_PIN_START))
        style.addImage(ICON_PIN_START, createPinBitmap(
            fillArgb = android.graphics.Color.parseColor("#00E676"),  // green
            borderArgb = android.graphics.Color.WHITE,
        ))
        style.addLayer(
            SymbolLayer(LAYER_PIN_START, SOURCE_PIN_START).withProperties(
                iconImage(ICON_PIN_START),
                iconAllowOverlap(true),
                iconIgnorePlacement(true),
                iconAnchor("bottom"),
                iconSize(1.0f),
            )
        )

        // ── End pin  (Bug #5: red circle with white border) ───────────────
        style.addSource(GeoJsonSource(SOURCE_PIN_END))
        style.addImage(ICON_PIN_END, createPinBitmap(
            fillArgb = android.graphics.Color.parseColor("#FF5252"),  // red
            borderArgb = android.graphics.Color.WHITE,
        ))
        style.addLayer(
            SymbolLayer(LAYER_PIN_END, SOURCE_PIN_END).withProperties(
                iconImage(ICON_PIN_END),
                iconAllowOverlap(true),
                iconIgnorePlacement(true),
                iconAnchor("bottom"),
                iconSize(1.0f),
            )
        )

        // ── Position accuracy ring ────────────────────────────────────────
        style.addSource(GeoJsonSource(SOURCE_POSITION))
        style.addLayer(
            CircleLayer(LAYER_POSITION_ACCURACY, SOURCE_POSITION).withProperties(
                circleColor("#80CBC4"),
                circleOpacity(0.18f),
                circleStrokeColor("#80CBC4"),
                circleStrokeWidth(1f),
                circleRadius(0f),
            )
        )

        // ── Animated position dot ─────────────────────────────────────────
        style.addImage(ICON_POSITION, createDotBitmap())
        style.addLayer(
            SymbolLayer(LAYER_POSITION_ICON, SOURCE_POSITION).withProperties(
                iconImage(ICON_POSITION),
                iconAllowOverlap(true),
                iconIgnorePlacement(true),
            )
        )
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Draw a multi-point OSRM route polyline and update the start / end pins.
     * Adjusts the camera to fit all waypoints.
     */
    fun updateRoute(route: Route) {
        val currentStyle = style ?: return
        if (route.waypoints.size < 2) return

        // Build polyline from all OSRM waypoints
        val points = route.waypoints.map { Point.fromLngLat(it.lng, it.lat) }
        val lineString = LineString.fromLngLats(points)
        currentStyle.getSourceAs<GeoJsonSource>(SOURCE_ROUTE)
            ?.setGeoJson(Feature.fromGeometry(lineString))

        // Update start / end pins
        val start = route.waypoints.first()
        val end   = route.waypoints.last()
        updatePin(currentStyle, SOURCE_PIN_START, start.lat, start.lng)
        updatePin(currentStyle, SOURCE_PIN_END,   end.lat,   end.lng)

        // Fit camera to route bounds
        fitCamera(points.map { LatLng(it.latitude(), it.longitude()) })
    }

    /**
     * Draw a straight two-point preview line (used before OSRM fetch completes).
     * Fixes Bug #5: also places start / end pins.
     */
    fun updateRoute(startLat: Double, startLng: Double, endLat: Double, endLng: Double) {
        val currentStyle = style ?: return

        val p1 = Point.fromLngLat(startLng, startLat)
        val p2 = Point.fromLngLat(endLng, endLat)
        currentStyle.getSourceAs<GeoJsonSource>(SOURCE_ROUTE)
            ?.setGeoJson(Feature.fromGeometry(LineString.fromLngLats(listOf(p1, p2))))

        updatePin(currentStyle, SOURCE_PIN_START, startLat, startLng)
        updatePin(currentStyle, SOURCE_PIN_END,   endLat,   endLng)

        fitCamera(listOf(LatLng(startLat, startLng), LatLng(endLat, endLng)))
    }

    /** Move the animated position dot to the current mock location. */
    fun updatePosition(location: MockLocation) {
        val currentStyle = style ?: return

        val point   = Point.fromLngLat(location.lng, location.lat)
        currentStyle.getSourceAs<GeoJsonSource>(SOURCE_POSITION)
            ?.setGeoJson(Feature.fromGeometry(point))

        // Scale accuracy ring to match reported accuracy
        val zoom = map.cameraPosition.zoom
        val metersPerPixel = 156_543.03392 *
            Math.cos(Math.toRadians(location.lat)) / Math.pow(2.0, zoom)
        val radiusPx = (location.accuracy / metersPerPixel).toFloat().coerceAtLeast(8f)

        currentStyle.getLayerAs<CircleLayer>(LAYER_POSITION_ACCURACY)
            ?.setProperties(circleRadius(radiusPx))
    }

    /** Hide the animated position dot (call when idle). */
    fun clearPosition() {
        style?.getSourceAs<GeoJsonSource>(SOURCE_POSITION)
            ?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
    }

    /** Remove the route line and both pins. */
    fun clearRoute() {
        val s = style ?: return
        s.getSourceAs<GeoJsonSource>(SOURCE_ROUTE)
            ?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        s.getSourceAs<GeoJsonSource>(SOURCE_PIN_START)
            ?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        s.getSourceAs<GeoJsonSource>(SOURCE_PIN_END)
            ?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun updatePin(style: Style, sourceId: String, lat: Double, lng: Double) {
        val point = Point.fromLngLat(lng, lat)
        style.getSourceAs<GeoJsonSource>(sourceId)
            ?.setGeoJson(Feature.fromGeometry(point))
    }

    private fun fitCamera(points: List<LatLng>) {
        if (points.size < 2) return
        try {
            val bounds = LatLngBounds.Builder().apply {
                points.forEach { include(it) }
            }.build()
            map.easeCamera(
                org.maplibre.android.camera.CameraUpdateFactory.newLatLngBounds(bounds, 150),
                800,
            )
        } catch (_: Exception) { /* degenerate bounds on same point */ }
    }

    /**
     * Teardrop-style pin bitmap.
     * [fillArgb] = main body colour; [borderArgb] = outer ring colour.
     */
    private fun createPinBitmap(fillArgb: Int, borderArgb: Int): Bitmap {
        val w = 36; val h = 50
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = w / 2f; val r = w / 2f - 2f

        // Outer border ring
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = borderArgb }
        canvas.drawCircle(cx, r + 2f, r + 2f, borderPaint)

        // Filled body
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillArgb }
        canvas.drawCircle(cx, r + 2f, r, fillPaint)

        // White inner dot (like Google Maps pin)
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
        }
        canvas.drawCircle(cx, r + 2f, r * 0.32f, dotPaint)

        return bitmap
    }

    /** Small pulsing dot for the live position marker. */
    private fun createDotBitmap(): Bitmap {
        val size = 24
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = size / 2f

        val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#80CBC4")
            alpha = 180
        }
        canvas.drawCircle(cx, cx, cx, outerPaint)

        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
        }
        canvas.drawCircle(cx, cx, cx * 0.45f, innerPaint)
        return bitmap
    }
}
