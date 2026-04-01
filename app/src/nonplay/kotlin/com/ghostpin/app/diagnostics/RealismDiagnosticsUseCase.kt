package com.ghostpin.app.diagnostics

import com.ghostpin.core.math.GeoMath
import com.ghostpin.core.model.MockLocation
import com.ghostpin.core.model.MovementProfile
import com.ghostpin.core.model.NoiseVector
import com.ghostpin.core.model.Route
import com.ghostpin.core.model.Waypoint
import com.ghostpin.engine.interpolation.RouteInterpolator
import com.ghostpin.engine.noise.MultipathNoiseModel
import com.ghostpin.engine.noise.OrnsteinUhlenbeckNoiseGenerator
import com.ghostpin.engine.validation.TrajectoryValidator
import com.ghostpin.realism.RealismReport
import com.ghostpin.realism.metrics.MetricResult
import dagger.hilt.android.scopes.ViewModelScoped
import java.util.Random
import javax.inject.Inject
import kotlin.math.cos

data class RealismDiagnosticsInput(
    val profile: MovementProfile,
    val route: Route?,
    val waypoints: List<Waypoint>,
    val startLat: Double,
    val startLng: Double,
    val endLat: Double,
    val endLng: Double,
)

data class RealismDiagnosticsResult(
    val profileName: String,
    val routeName: String,
    val routeSource: String,
    val sampleCount: Int,
    val scorePercent: Int,
    val trajectoryWarnings: List<String>,
    val metrics: List<MetricResult>,
    val summary: String,
)

@ViewModelScoped
class RealismDiagnosticsUseCase
    @Inject
    constructor(
        private val trajectoryValidator: TrajectoryValidator,
    ) {
        fun analyze(input: RealismDiagnosticsInput): Result<RealismDiagnosticsResult> =
            runCatching {
                val (route, routeSource) = resolveRoute(input)
                val validation = trajectoryValidator.validate(route, input.profile)
                val (locations, noiseVectors, latNoiseSeries) = synthesizeSession(route, input.profile)
                val report =
                    RealismReport.evaluate(
                        locations = locations,
                        noiseVectors = noiseVectors,
                        latNoiseSeries = latNoiseSeries,
                        expectedPMultipath = input.profile.pMultipath,
                    )

                RealismDiagnosticsResult(
                    profileName = input.profile.name,
                    routeName = route.name,
                    routeSource = routeSource,
                    sampleCount = locations.size,
                    scorePercent = ((report.passCount.toDouble() / report.total.coerceAtLeast(1)) * 100.0).toInt(),
                    trajectoryWarnings = validation.warnings,
                    metrics = report.results,
                    summary = report.summary(),
                )
            }

        private fun resolveRoute(input: RealismDiagnosticsInput): Pair<Route, String> {
            input.route?.takeIf { it.waypoints.size >= 2 }?.let {
                return it to "Current route"
            }
            if (input.waypoints.size >= 2) {
                return Route(
                    id = "diagnostics-waypoints",
                    name = "Waypoint Route",
                    waypoints = input.waypoints,
                ) to "Current waypoints"
            }

            val startEndDistance = GeoMath.haversineMeters(input.startLat, input.startLng, input.endLat, input.endLng)
            if (startEndDistance > 5.0) {
                return Route(
                    id = "diagnostics-pins",
                    name = "Pin Route",
                    waypoints =
                        listOf(
                            Waypoint(input.startLat, input.startLng),
                            Waypoint(input.endLat, input.endLng),
                        ),
                ) to "Start and end pins"
            }

            error("Add a route or at least two waypoints before running realism diagnostics.")
        }

        private fun synthesizeSession(
            route: Route,
            profile: MovementProfile,
            sampleCount: Int = 180,
            deltaTimeSec: Double = 1.0,
        ): Triple<List<MockLocation>, List<NoiseVector>, List<Double>> {
            val random = Random(42L)
            val multipath =
                MultipathNoiseModel(
                    ouGenerator =
                        OrnsteinUhlenbeckNoiseGenerator(
                            theta = profile.theta,
                            sigma = profile.sigma,
                            random = random,
                        ),
                    pMultipath = profile.pMultipath,
                    laplaceScale = profile.laplaceScale,
                    random = random,
                )
            val interpolator = RouteInterpolator(route)
            val stepMeters = interpolator.totalDistanceMeters / (sampleCount - 1).coerceAtLeast(1)
            val speedMs = (profile.maxSpeedMs * 0.65).toFloat()

            val locations = ArrayList<MockLocation>(sampleCount)
            val noiseVectors = ArrayList<NoiseVector>(sampleCount)
            val latNoiseSeries = ArrayList<Double>(sampleCount)

            repeat(sampleCount) { index ->
                val frame = interpolator.positionAt(stepMeters * index)
                val noise = multipath.sample(deltaTimeSec)
                val metersToDegreesLat = 1.0 / 111_320.0
                val cosLat = cos(Math.toRadians(frame.lat)).coerceAtLeast(0.001)
                val metersToDegreesLng = 1.0 / (111_320.0 * cosLat)

                noiseVectors += noise
                latNoiseSeries += noise.lat
                locations +=
                    MockLocation(
                        lat = frame.lat + noise.lat * metersToDegreesLat,
                        lng = frame.lng + noise.lng * metersToDegreesLng,
                        altitude = 0.0,
                        speed = speedMs,
                        bearing = frame.bearing,
                        accuracy = if (noise.isJump) 14f else 6f,
                        elapsedRealtimeNanos = (index + 1L) * 1_000_000_000L,
                        timestampMs = (index + 1L) * 1_000L,
                    )
            }

            return Triple(locations, noiseVectors, latNoiseSeries)
        }
    }
