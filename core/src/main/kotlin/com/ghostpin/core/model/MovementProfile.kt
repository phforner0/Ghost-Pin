package com.ghostpin.core.model

/**
 * Movement profile containing all calibrated parameters for simulation.
 *
 * Each profile encodes the physical constraints of a movement type AND
 * the noise model parameters calibrated against real GPS traces.
 *
 * @property name Human-readable profile name
 * @property theta OU mean reversion speed (s⁻¹) — higher = faster decorrelation
 * @property sigma OU volatility (meters equivalent)
 * @property pMultipath Probability of multipath jump per sample (0..1)
 * @property laplaceScale Laplace distribution b-parameter for multipath jumps (meters)
 * @property maxSpeedMs Maximum speed for this movement type (m/s)
 * @property maxAccelMs2 Maximum acceleration (m/s²) — for speed clamping
 * @property maxTurnRateDegPerSec Maximum bearing change rate (°/s)
 * @property tunnelProbabilityPerSec Per-second probability of signal loss event
 * @property tunnelDurationMeanSec LogNormal μ for tunnel duration
 * @property tunnelDurationSigmaSec LogNormal σ for tunnel duration
 * @property altitudeSigma Altitude OU volatility (meters) — scaled by modal
 * @property quantizationDecimals Number of decimal places for lat/lng quantization
 */
data class MovementProfile(
    val name: String,
    val theta: Double,
    val sigma: Double,
    val pMultipath: Double,
    val laplaceScale: Double,
    val maxSpeedMs: Double,
    val maxAccelMs2: Double,
    val maxTurnRateDegPerSec: Double,
    val tunnelProbabilityPerSec: Double,
    val tunnelDurationMeanSec: Double,
    val tunnelDurationSigmaSec: Double,
    val altitudeSigma: Double,
    val quantizationDecimals: Int = 7,
) {
    companion object {
        /** Walking on sidewalks — slow, correlated, moderate multipath near buildings. */
        val PEDESTRIAN =
            MovementProfile(
                name = "Pedestrian",
                theta = 0.35,
                sigma = 2.5,
                pMultipath = 0.04,
                laplaceScale = 8.0,
                maxSpeedMs = 2.0, // ~7.2 km/h
                maxAccelMs2 = 1.5,
                maxTurnRateDegPerSec = 90.0,
                tunnelProbabilityPerSec = 0.0005,
                tunnelDurationMeanSec = 2.0,
                tunnelDurationSigmaSec = 0.8,
                altitudeSigma = 3.0,
            )

        /** Urban cycling — moderate speed, slight lateral oscillation. */
        val BICYCLE =
            MovementProfile(
                name = "Bicycle",
                theta = 0.45,
                sigma = 3.0,
                pMultipath = 0.03,
                laplaceScale = 10.0,
                maxSpeedMs = 8.3, // ~30 km/h
                maxAccelMs2 = 2.5,
                maxTurnRateDegPerSec = 60.0,
                tunnelProbabilityPerSec = 0.0008,
                tunnelDurationMeanSec = 2.5,
                tunnelDurationSigmaSec = 1.0,
                altitudeSigma = 2.5,
            )

        /** Highway/suburban driving — faster decorrelation, larger errors. */
        val CAR =
            MovementProfile(
                name = "Car",
                theta = 0.55,
                sigma = 4.0,
                pMultipath = 0.03,
                laplaceScale = 15.0,
                maxSpeedMs = 33.3, // ~120 km/h
                maxAccelMs2 = 4.0,
                maxTurnRateDegPerSec = 45.0,
                tunnelProbabilityPerSec = 0.001,
                tunnelDurationMeanSec = 3.0,
                tunnelDurationSigmaSec = 1.0,
                altitudeSigma = 2.0,
            )

        /** Dense urban: buses, taxis in city center — worst signal conditions. */
        val URBAN_VEHICLE =
            MovementProfile(
                name = "Urban Vehicle",
                theta = 0.60,
                sigma = 5.5,
                pMultipath = 0.06,
                laplaceScale = 18.0,
                maxSpeedMs = 16.7, // ~60 km/h
                maxAccelMs2 = 3.5,
                maxTurnRateDegPerSec = 50.0,
                tunnelProbabilityPerSec = 0.002,
                tunnelDurationMeanSec = 3.0,
                tunnelDurationSigmaSec = 1.0,
                altitudeSigma = 2.0,
            )

        /** Aerial drone — high altitude, minimal obstructions, low noise. */
        val DRONE =
            MovementProfile(
                name = "Drone",
                theta = 0.25,
                sigma = 1.8,
                pMultipath = 0.01,
                laplaceScale = 5.0,
                maxSpeedMs = 20.0, // ~72 km/h
                maxAccelMs2 = 5.0,
                maxTurnRateDegPerSec = 120.0,
                tunnelProbabilityPerSec = 0.0001,
                tunnelDurationMeanSec = 1.0,
                tunnelDurationSigmaSec = 0.5,
                altitudeSigma = 1.5,
            )

        /** All built-in profiles indexed by name. */
        val BUILT_IN: Map<String, MovementProfile> =
            listOf(
                PEDESTRIAN,
                BICYCLE,
                CAR,
                URBAN_VEHICLE,
                DRONE
            ).associateBy { it.name }
    }
}
