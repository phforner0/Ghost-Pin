package com.ghostpin.engine.interpolation

/**
 * 1-D Kalman filter for position + velocity.
 *
 * Applied independently to latitude and longitude to smooth the output of
 * [RouteInterpolator] before [com.ghostpin.engine.noise.LayeredNoiseModel] adds
 * realistic GPS noise on top.
 *
 * Without this filter, interpolated positions can exhibit tiny discontinuities
 * at segment boundaries (especially the Catmull-Rom ↔ linear transition) that
 * would show up as detectable micro-jumps in the injected Location stream.
 *
 * **State vector:** [position, velocity]
 * **Observation:**  position only (H = [1, 0])
 *
 * Tuning guide:
 *   - Lower [processNoise]      → trusts prediction more → smoother output, more lag
 *   - Higher [measurementNoise] → trusts measurement less → smoother output, more lag
 *   For GPS simulation at 5 Hz, processNoise ≈ 1e-5 and measurementNoise ≈ 0.3 work well.
 *
 * @param processNoise     Q — how much the system state can change per second² (units: coord²/s²)
 * @param measurementNoise R — measurement uncertainty in coord units² (larger = more smoothing)
 */
class KalmanFilter1D(
    private val processNoise: Double = 1e-5,
    private val measurementNoise: Double = 0.3,
) {
    // State estimate
    private var pos: Double = 0.0
    private var vel: Double = 0.0

    // Covariance matrix P (2×2, symmetric)
    private var p00: Double = 1.0
    private var p01: Double = 0.0
    private var p10: Double = 0.0
    private var p11: Double = 1.0

    private var initialized: Boolean = false

    /**
     * Feed a new position [measurement] and return the smoothed estimate.
     *
     * @param measurement   Raw position (latitude or longitude in degrees).
     * @param deltaTimeSec  Time elapsed since the previous call (must be > 0).
     * @return Smoothed position estimate.
     */
    fun update(measurement: Double, deltaTimeSec: Double): Double {
        require(deltaTimeSec > 0.0) { "deltaTimeSec must be positive" }

        if (!initialized) {
            pos = measurement
            initialized = true
            return pos
        }

        val dt = deltaTimeSec

        // ── PREDICT ──────────────────────────────────────────────────────────
        // State transition: F = [[1, dt], [0, 1]]
        val predPos = pos + vel * dt
        val predVel = vel

        // P' = F * P * F^T + Q
        // Q is scaled by dt to keep units consistent (position uncertainty grows with time)
        val q = processNoise * dt
        val pp00 = p00 + dt * (p10 + p01) + dt * dt * p11 + q
        val pp01 = p01 + dt * p11
        val pp10 = p10 + dt * p11
        val pp11 = p11 + q

        // ── UPDATE ────────────────────────────────────────────────────────────
        // Innovation: y = z - H * x_pred  (H = [1, 0])
        val innovation = measurement - predPos

        // Innovation covariance: S = H * P' * H^T + R = P'[0][0] + R
        val S = pp00 + measurementNoise

        // Kalman gain: K = P' * H^T / S
        val k0 = pp00 / S   // gain for position update
        val k1 = pp10 / S   // gain for velocity update

        // Updated state
        pos = predPos + k0 * innovation
        vel = predVel + k1 * innovation

        // Updated covariance: P = (I - K * H) * P'
        p00 = (1.0 - k0) * pp00
        p01 = (1.0 - k0) * pp01
        p10 = pp10 - k1 * pp00
        p11 = pp11 - k1 * pp01

        return pos
    }

    /**
     * Returns the current velocity estimate (coordinate units per second).
     * Useful for deriving approximate speed when integrated over lat+lng.
     */
    fun currentVelocity(): Double = vel

    /**
     * Reset filter to uninitialised state — call before a new simulation.
     */
    fun reset() {
        initialized = false
        pos = 0.0
        vel = 0.0
        p00 = 1.0; p01 = 0.0
        p10 = 0.0; p11 = 1.0
    }
}
