package com.paul.infrastructure.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class AndroidLocationService(
    private val context: Context
) : ILocationService {

    // *** THE FIX IS HERE: Use `by lazy` for the client ***
    // This defers the initialization of the FusedLocationProviderClient until the first time
    // it's accessed (inside getLocationFlow). By that time, the context is guaranteed to be valid.
    private val fusedLocationProviderClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    @SuppressLint("MissingPermission") // Permissions are checked before this is called
    override fun getLocationFlow(): Flow<UserLocation> = callbackFlow {
        // 1. Check permissions. If not granted, we can't proceed.
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            close() // Close the flow if we have no permission
            return@callbackFlow
        }

        // 2. Define the location request parameters
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000 // Interval in milliseconds (e.g., 5 seconds)
        ).build()

        // 3. Define the callback that will receive location updates
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    // Create our custom UserLocation object
                    val userLocation = UserLocation(
                        position = GeoPosition(location.latitude, location.longitude),
                        bearing = if (location.hasBearing()) location.bearing else null
                    )
                    // Try to send the new location to the flow collector
                    trySend(userLocation)
                }
            }
        }

        // 4. Register the callback to start receiving updates
        fusedLocationProviderClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )

        // 5. awaitClose is crucial! It's called when the flow is cancelled.
        // We must unregister the location updates to prevent memory leaks and battery drain.
        awaitClose {
            fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        }
    }
}