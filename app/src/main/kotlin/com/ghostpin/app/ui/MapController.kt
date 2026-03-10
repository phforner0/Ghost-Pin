package com.ghostpin.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.ghostpin.core.model.MockLocation
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

/** Encapsulates MapLibre styling and layer management for the simulation UI. */
class MapController(
        private val map: MapLibreMap,
        private val context: Context,
        private val onMapLongClick: (LatLng) -> Unit,
) {
    companion object {
        private const val SOURCE_ROUTE = "source_route"
        private const val LAYER_ROUTE = "layer_route"

        private const val SOURCE_POSITION = "source_position"
        private const val LAYER_POSITION_ACCURACY = "layer_position_accuracy"
        private const val LAYER_POSITION_ICON = "layer_position_icon"

        private const val ICON_ID_MARKER = "icon_marker"
    }

    private var style: Style? = null

    init {
        // Load a free vector style using URL directly
        map.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")) {
                loadedStyle ->
            this.style = loadedStyle
            setupLayers(loadedStyle)

            map.addOnMapLongClickListener { latLng ->
                onMapLongClick(latLng)
                true
            }
        }
    }

    private fun setupLayers(style: Style) {
        // 1. Add route line source and layer
        style.addSource(GeoJsonSource(SOURCE_ROUTE))
        style.addLayer(
                LineLayer(LAYER_ROUTE, SOURCE_ROUTE)
                        .withProperties(lineColor("#80CBC4"), lineWidth(4f), lineOpacity(0.8f))
        )

        // 2. Add current position marker source
        style.addSource(GeoJsonSource(SOURCE_POSITION))

        // Create a programmatic circle bitmap for the marker
        style.addImage(ICON_ID_MARKER, createMarkerBitmap())

        // 3. Add accuracy circle layer (underneath marker)
        style.addLayer(
                CircleLayer(LAYER_POSITION_ACCURACY, SOURCE_POSITION)
                        .withProperties(
                                circleColor("#80CBC4"),
                                circleOpacity(0.2f),
                                circleStrokeColor("#80CBC4"),
                                circleStrokeWidth(1f),
                                // We'll update radius dynamically based on MockLocation.accuracy
                                circleRadius(0f)
                        )
        )

        // 4. Add the actual pin marker layer
        style.addLayer(
                SymbolLayer(LAYER_POSITION_ICON, SOURCE_POSITION)
                        .withProperties(
                                iconImage(ICON_ID_MARKER),
                                iconAllowOverlap(true),
                                iconIgnorePlacement(true)
                        )
        )
    }

    /** Updates the drawn route polyline and adjusts the camera bounds to fit it. */
    fun updateRoute(startLat: Double, startLng: Double, endLat: Double, endLng: Double) {
        val currentStyle = style ?: return

        val p1 = Point.fromLngLat(startLng, startLat)
        val p2 = Point.fromLngLat(endLng, endLat)
        val lineString = LineString.fromLngLats(listOf(p1, p2))
        val feature = Feature.fromGeometry(lineString)

        val source = currentStyle.getSourceAs<GeoJsonSource>(SOURCE_ROUTE)
        source?.setGeoJson(feature)

        // Move camera to fit route with padding
        val bounds =
                LatLngBounds.Builder()
                        .include(LatLng(startLat, startLng))
                        .include(LatLng(endLat, endLng))
                        .build()

        map.easeCamera(
                org.maplibre.android.camera.CameraUpdateFactory.newLatLngBounds(bounds, 150),
                1000
        )
    }

    /** Updates the marker position and the radius of the accuracy circle. */
    fun updatePosition(location: MockLocation) {
        val currentStyle = style ?: return

        val point = Point.fromLngLat(location.lng, location.lat)
        val feature = Feature.fromGeometry(point)
        // Store accuracy in feature properties to data-drive the radius if needed,
        // or just set it directly on the layer. Setting on layer is simpler for now.

        val source = currentStyle.getSourceAs<GeoJsonSource>(SOURCE_POSITION)
        source?.setGeoJson(feature)

        val accuracyLayer = currentStyle.getLayerAs<CircleLayer>(LAYER_POSITION_ACCURACY)

        // Very basic conversion: 1 meter roughly translates to some pixel radius at current zoom.
        // MapLibre's circle-radius is in pixels. Accurate representation requires calculating
        // meters-per-pixel at current latitude and zoom, but a static multiplier is fine for visual
        // demo.
        val zoomBase = map.cameraPosition.zoom
        val metersPerPixel =
                156543.03392 * Math.cos(location.lat * Math.PI / 180.0) / Math.pow(2.0, zoomBase)
        val radiusInPixels = (location.accuracy / metersPerPixel).toFloat().coerceAtLeast(10f)

        accuracyLayer?.setProperties(circleRadius(radiusInPixels))
    }

    fun clearRoute() {
        style?.getSourceAs<GeoJsonSource>(SOURCE_ROUTE)
                ?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
    }

    fun clearPosition() {
        style?.getSourceAs<GeoJsonSource>(SOURCE_POSITION)
                ?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
    }

    private fun createMarkerBitmap(): Bitmap {
        val size = 48
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.parseColor("#00E676") // A bright green pin
                }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(size / 2f, size / 2f, size / 4f, paint)
        return bitmap
    }
}
