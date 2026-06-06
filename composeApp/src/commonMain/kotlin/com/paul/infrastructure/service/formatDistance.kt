package com.paul.infrastructure.service

import kotlin.math.roundToInt

/**
 * Formats a distance in meters to a human-readable string (m or km).
 */
fun formatDistance(distanceMeters: Float): String {
    return if (distanceMeters < 1000) {
        "${distanceMeters.roundToInt()} m"
    } else {
        "${"%.1f".format(java.util.Locale.US, distanceMeters / 1000f)} km"
    }
}
