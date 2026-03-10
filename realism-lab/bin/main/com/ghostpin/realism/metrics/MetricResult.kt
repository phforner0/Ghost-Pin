package com.ghostpin.realism.metrics

/**
 * Result of a single Realism Lab metric evaluation.
 */
data class MetricResult(
    val name: String,
    val value: Double,
    val passed: Boolean,
    val detail: String = "",
)
