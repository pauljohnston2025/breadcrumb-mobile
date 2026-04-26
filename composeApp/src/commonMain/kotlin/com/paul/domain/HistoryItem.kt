package com.paul.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class HistoryItem(
    val id: String,
    val routeId: String,
    val timestamp: Instant
) {
    // dirty dirty hacks
    fun isStrava(): Boolean {
        return routeId.startsWith("strava:")
    }

    fun stravaId(): Long {
        assert(isStrava())
        return routeId.substringAfter("strava:").toLong()
    }
}