package com.paul.infrastructure.service

import kotlinx.coroutines.flow.Flow

data class UserLocation(
    val position: GeoPosition,
    val bearing: Float? // Bearing in degrees, nullable as it's not always available
)

interface ILocationService {
    /**
     * Provides a continuous stream of user location updates.
     * The flow will emit new [UserLocation] objects whenever the location or bearing changes.
     * The flow will complete or do nothing if permissions are not granted.
     */
    fun getLocationFlow(): Flow<UserLocation>
}