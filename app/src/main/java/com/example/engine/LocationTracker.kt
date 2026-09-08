package com.example.engine

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.data.LocationData
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * LocationTracker: Tracks active GPS coordinates during a Safe Walk session.
 * Detects prolonged stationary state after a detected fall.
 */
class LocationTracker(
    private val context: Context,
    private val onProlongedStillnessDetected: () -> Unit
) {
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _locationData = MutableStateFlow(LocationData())
    val locationData: StateFlow<LocationData> = _locationData.asStateFlow()

    private var locationCallback: LocationCallback? = null
    private var isTracking = false
    private var lastRecordedLocation: Location? = null
    private var stationaryStartTimestamp: Long = 0L

    fun checkPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    fun startTracking() {
        val hasPerm = checkPermission()
        _locationData.value = _locationData.value.copy(hasPermission = hasPerm)
        if (!hasPerm) {
            return
        }

        isTracking = true
        stationaryStartTimestamp = 0L

        // Fetch immediate last location if available
        try {
            fusedClient.lastLocation.addOnSuccessListener { loc: Location? ->
                if (loc != null) {
                    updateLocation(loc)
                }
            }
        } catch (_: Exception) {}

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(3000L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    updateLocation(loc)
                }
            }
        }

        try {
            fusedClient.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
        } catch (_: Exception) {}
    }

    fun stopTracking() {
        isTracking = false
        locationCallback?.let {
            fusedClient.removeLocationUpdates(it)
        }
        locationCallback = null
    }

    private fun updateLocation(loc: Location) {
        val now = System.currentTimeMillis()

        // Check if movement is stationary (< 3.0 meters)
        lastRecordedLocation?.let { prev ->
            val dist = prev.distanceTo(loc)
            if (dist < 3.0f) {
                if (stationaryStartTimestamp == 0L) {
                    stationaryStartTimestamp = now
                } else if (now - stationaryStartTimestamp > 25_000L) {
                    onProlongedStillnessDetected()
                }
            } else {
                stationaryStartTimestamp = now
            }
        } ?: run {
            stationaryStartTimestamp = now
        }

        lastRecordedLocation = loc

        _locationData.value = LocationData(
            latitude = loc.latitude,
            longitude = loc.longitude,
            accuracyMeters = loc.accuracy,
            timestamp = now,
            hasPermission = true,
            isAvailable = true
        )
    }

    /**
     * For demo simulations
     */
    fun simulateLocation(lat: Double = 37.7749, lng: Double = -122.4194) {
        _locationData.value = LocationData(
            latitude = lat,
            longitude = lng,
            accuracyMeters = 4.2f,
            timestamp = System.currentTimeMillis(),
            hasPermission = true,
            isAvailable = true
        )
    }
}
