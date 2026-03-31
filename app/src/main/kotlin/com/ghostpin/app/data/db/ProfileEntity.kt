package com.ghostpin.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ghostpin.core.model.MovementProfile

/**
 * Room entity representing a persisted [MovementProfile].
 *
 * Sprint 4 — Task 14: ProfileManager with Room persistence.
 *
 * Schema notes:
 *  - [isBuiltIn] flags the 5 factory profiles — they are seeded on first launch
 *    and cannot be deleted by the user.
 *  - [version] uses semantic versioning (major.minor.patch) so the UI can show
 *    when a cloned profile has been modified.
 *  - All OU and multipath parameters are stored individually for full editability.
 */
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String, // UUID for user profiles, "builtin_<name>" for factory
    val name: String,
    val version: String, // semver: "1.0.0"
    val isBuiltIn: Boolean,
    val isCustom: Boolean, // true = user-created or cloned
    // Ornstein-Uhlenbeck noise parameters
    val theta: Double,
    val sigma: Double,
    // Multipath parameters
    val pMultipath: Double,
    val laplaceScale: Double,
    // Physical constraints
    val maxSpeedMs: Double,
    val maxAccelMs2: Double,
    val maxTurnRateDegPerSec: Double,
    // Tunnel/signal-loss parameters
    val tunnelProbabilityPerSec: Double,
    val tunnelDurationMeanSec: Double,
    val tunnelDurationSigmaSec: Double,
    // Altitude
    val altitudeSigma: Double,
    // Quantization
    val quantizationDecimals: Int,
    // Metadata
    val createdAtMs: Long,
    val updatedAtMs: Long,
) {
    /** Convert back to the domain model used by the engine. */
    fun toDomain(): MovementProfile =
        MovementProfile(
            name = name,
            theta = theta,
            sigma = sigma,
            pMultipath = pMultipath,
            laplaceScale = laplaceScale,
            maxSpeedMs = maxSpeedMs,
            maxAccelMs2 = maxAccelMs2,
            maxTurnRateDegPerSec = maxTurnRateDegPerSec,
            tunnelProbabilityPerSec = tunnelProbabilityPerSec,
            tunnelDurationMeanSec = tunnelDurationMeanSec,
            tunnelDurationSigmaSec = tunnelDurationSigmaSec,
            altitudeSigma = altitudeSigma,
            quantizationDecimals = quantizationDecimals,
        )

    companion object {
        /** Create a [ProfileEntity] from a domain [MovementProfile]. */
        fun fromDomain(
            profile: MovementProfile,
            id: String,
            isBuiltIn: Boolean,
            isCustom: Boolean,
            version: String = "1.0.0",
        ): ProfileEntity {
            val now = System.currentTimeMillis()
            return ProfileEntity(
                id = id,
                name = profile.name,
                version = version,
                isBuiltIn = isBuiltIn,
                isCustom = isCustom,
                theta = profile.theta,
                sigma = profile.sigma,
                pMultipath = profile.pMultipath,
                laplaceScale = profile.laplaceScale,
                maxSpeedMs = profile.maxSpeedMs,
                maxAccelMs2 = profile.maxAccelMs2,
                maxTurnRateDegPerSec = profile.maxTurnRateDegPerSec,
                tunnelProbabilityPerSec = profile.tunnelProbabilityPerSec,
                tunnelDurationMeanSec = profile.tunnelDurationMeanSec,
                tunnelDurationSigmaSec = profile.tunnelDurationSigmaSec,
                altitudeSigma = profile.altitudeSigma,
                quantizationDecimals = profile.quantizationDecimals,
                createdAtMs = now,
                updatedAtMs = now,
            )
        }
    }
}
