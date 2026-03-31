package com.ghostpin.engine.noise

import kotlin.math.pow
import kotlin.math.round

/**
 * Simulates finite receiver resolution by quantizing lat/lng coordinates
 * to a fixed number of decimal places.
 *
 * Real GPS receivers output coordinates with limited precision (typically
 * 6-7 decimal places). Raw floating-point values with 15+ significant digits
 * are a dead giveaway of synthetic data.
 *
 * @param decimals Number of decimal places to preserve (default 7 ≈ 1.1cm resolution).
 */
class QuantizationNoise(
    private val decimals: Int = 7,
) {
    private val factor = 10.0.pow(decimals)

    /**
     * Quantize a coordinate value to the configured decimal precision.
     */
    fun quantize(value: Double): Double = round(value * factor) / factor
}
