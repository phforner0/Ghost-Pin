package com.ghostpin.app.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import com.ghostpin.app.R;
import com.ghostpin.core.model.MockLocation;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.Style;
import org.maplibre.android.style.layers.CircleLayer;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.layers.SymbolLayer;
import org.maplibre.android.style.sources.GeoJsonSource;

/**
 * Encapsulates MapLibre styling and layer management for the simulation UI.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000F\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0006\n\u0002\b\u0005\b\u0007\u0018\u0000 \u001b2\u00020\u0001:\u0001\u001bB)\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0012\u0010\u0006\u001a\u000e\u0012\u0004\u0012\u00020\b\u0012\u0004\u0012\u00020\t0\u0007\u00a2\u0006\u0002\u0010\nJ\u0006\u0010\r\u001a\u00020\tJ\u0006\u0010\u000e\u001a\u00020\tJ\b\u0010\u000f\u001a\u00020\u0010H\u0002J\u0010\u0010\u0011\u001a\u00020\t2\u0006\u0010\u000b\u001a\u00020\fH\u0002J\u000e\u0010\u0012\u001a\u00020\t2\u0006\u0010\u0013\u001a\u00020\u0014J&\u0010\u0015\u001a\u00020\t2\u0006\u0010\u0016\u001a\u00020\u00172\u0006\u0010\u0018\u001a\u00020\u00172\u0006\u0010\u0019\u001a\u00020\u00172\u0006\u0010\u001a\u001a\u00020\u0017R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001a\u0010\u0006\u001a\u000e\u0012\u0004\u0012\u00020\b\u0012\u0004\u0012\u00020\t0\u0007X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u000b\u001a\u0004\u0018\u00010\fX\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u001c"}, d2 = {"Lcom/ghostpin/app/ui/MapController;", "", "map", "Lorg/maplibre/android/maps/MapLibreMap;", "context", "Landroid/content/Context;", "onMapLongClick", "Lkotlin/Function1;", "Lorg/maplibre/android/geometry/LatLng;", "", "(Lorg/maplibre/android/maps/MapLibreMap;Landroid/content/Context;Lkotlin/jvm/functions/Function1;)V", "style", "Lorg/maplibre/android/maps/Style;", "clearPosition", "clearRoute", "createMarkerBitmap", "Landroid/graphics/Bitmap;", "setupLayers", "updatePosition", "location", "Lcom/ghostpin/core/model/MockLocation;", "updateRoute", "startLat", "", "startLng", "endLat", "endLng", "Companion", "app_nonplayDebug"})
public final class MapController {
    @org.jetbrains.annotations.NotNull()
    private final org.maplibre.android.maps.MapLibreMap map = null;
    @org.jetbrains.annotations.NotNull()
    private final android.content.Context context = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.jvm.functions.Function1<org.maplibre.android.geometry.LatLng, kotlin.Unit> onMapLongClick = null;
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String SOURCE_ROUTE = "source_route";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String LAYER_ROUTE = "layer_route";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String SOURCE_POSITION = "source_position";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String LAYER_POSITION_ACCURACY = "layer_position_accuracy";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String LAYER_POSITION_ICON = "layer_position_icon";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String ICON_ID_MARKER = "icon_marker";
    @org.jetbrains.annotations.Nullable()
    private org.maplibre.android.maps.Style style;
    @org.jetbrains.annotations.NotNull()
    public static final com.ghostpin.app.ui.MapController.Companion Companion = null;
    
    public MapController(@org.jetbrains.annotations.NotNull()
    org.maplibre.android.maps.MapLibreMap map, @org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function1<? super org.maplibre.android.geometry.LatLng, kotlin.Unit> onMapLongClick) {
        super();
    }
    
    private final void setupLayers(org.maplibre.android.maps.Style style) {
    }
    
    /**
     * Updates the drawn route polyline and adjusts the camera bounds to fit it.
     */
    public final void updateRoute(double startLat, double startLng, double endLat, double endLng) {
    }
    
    /**
     * Updates the marker position and the radius of the accuracy circle.
     */
    public final void updatePosition(@org.jetbrains.annotations.NotNull()
    com.ghostpin.core.model.MockLocation location) {
    }
    
    public final void clearRoute() {
    }
    
    public final void clearPosition() {
    }
    
    private final android.graphics.Bitmap createMarkerBitmap() {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0006\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\n"}, d2 = {"Lcom/ghostpin/app/ui/MapController$Companion;", "", "()V", "ICON_ID_MARKER", "", "LAYER_POSITION_ACCURACY", "LAYER_POSITION_ICON", "LAYER_ROUTE", "SOURCE_POSITION", "SOURCE_ROUTE", "app_nonplayDebug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}