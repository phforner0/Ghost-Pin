package com.ghostpin.app.ui

import com.ghostpin.core.model.MovementProfile

internal fun profileHeuristics(profile: MovementProfile): List<String> =
    buildList {
        if (profile.maxSpeedMs >= 18.0) add("High speed")
        if (profile.maxTurnRateDegPerSec >= 100.0) add("Aggressive turns")
        if (profile.tunnelProbabilityPerSec >= 0.03) add("High tunnel rate")
        if (profile.sigma <= 1.0 && profile.pMultipath <= 0.01) add("Low noise")
        if (profile.pMultipath >= 0.04) add("High multipath")
        if (profile.maxSpeedMs <= 3.0 && profile.maxAccelMs2 <= 1.6) add("Stable")
    }.take(4)
