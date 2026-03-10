package com.ghostpin.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a persisted [com.ghostpin.core.model.Route].
 *
 * Sprint 4 — Task 17: Route editor with persistence.
 *
 * Waypoints and segments are stored as JSON blobs to avoid over-normalising
 * what is effectively an ordered list that is always read/written together.
 * This avoids multi-table joins on every route load and keeps the schema
 * simple for an app with at most ~100 saved routes.
 */
@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey val id: String,
    val name: String,
    val waypointsJson: String,           // JSON array of Waypoint objects
    val segmentsJson: String,            // JSON array of Segment objects (may be "[]")
    val sourceFormat: String,            // "manual" | "gpx" | "kml" | "tcx" | "osrm"
    val totalDistanceMeters: Double,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)
