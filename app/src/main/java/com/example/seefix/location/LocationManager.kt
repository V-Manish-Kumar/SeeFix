package com.example.seefix.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.seefix.ai.LocationInfo
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

sealed class LocationResult {
    data class Success(val location: LocationInfo, val accuracy: Float = 0f) : LocationResult()
    object Unavailable : LocationResult()
}

/**
 * FusedLocationProviderClient manager that checks runtime location permissions
 * and fetches current GNSS coordinates without static fallbacks.
 */
class LocationManager(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _userLocation = MutableStateFlow<LocationInfo?>(null)
    val userLocation: StateFlow<LocationInfo?> = _userLocation.asStateFlow()

    private val _locationResult = MutableStateFlow<LocationResult>(LocationResult.Unavailable)
    val locationResult: StateFlow<LocationResult> = _locationResult.asStateFlow()

    fun hasLocationPermission(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineLocation || coarseLocation
    }

    @SuppressLint("MissingPermission")
    suspend fun getLocation(): LocationResult = suspendCancellableCoroutine { continuation ->
        if (!hasLocationPermission()) {
            _userLocation.value = null
            _locationResult.value = LocationResult.Unavailable
            if (continuation.isActive) continuation.resume(LocationResult.Unavailable)
            return@suspendCancellableCoroutine
        }

        try {
            val cancellationTokenSource = CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cancellationTokenSource.token
            ).addOnSuccessListener { location: Location? ->
                if (location != null) {
                    Log.d("LocationManager", "[LOCATION_FETCHED accuracy=${location.accuracy}m]")
                    val locInfo = LocationInfo(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        address = "GNSS Fix (${String.format(Locale.US, "%.4f", location.latitude)}, ${String.format(
                            Locale.US, "%.4f", location.longitude)})"
                    )
                    _userLocation.value = locInfo
                    val res = LocationResult.Success(locInfo, location.accuracy)
                    _locationResult.value = res
                    if (continuation.isActive) continuation.resume(res)
                } else {
                    fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc: Location? ->
                        if (lastLoc != null) {
                            Log.d("LocationManager", "[LOCATION_FETCHED accuracy=${lastLoc.accuracy}m]")
                            val locInfo = LocationInfo(
                                latitude = lastLoc.latitude,
                                longitude = lastLoc.longitude,
                                address = "Last Known Fix (${String.format(Locale.US, "%.4f", lastLoc.latitude)}, ${String.format(
                                    Locale.US, "%.4f", lastLoc.longitude)})"
                            )
                            _userLocation.value = locInfo
                            val res = LocationResult.Success(locInfo, lastLoc.accuracy)
                            _locationResult.value = res
                            if (continuation.isActive) continuation.resume(res)
                        } else {
                            _userLocation.value = null
                            _locationResult.value = LocationResult.Unavailable
                            if (continuation.isActive) continuation.resume(LocationResult.Unavailable)
                        }
                    }.addOnFailureListener {
                        _userLocation.value = null
                        _locationResult.value = LocationResult.Unavailable
                        if (continuation.isActive) continuation.resume(LocationResult.Unavailable)
                    }
                }
            }.addOnFailureListener {
                _userLocation.value = null
                _locationResult.value = LocationResult.Unavailable
                if (continuation.isActive) continuation.resume(LocationResult.Unavailable)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _userLocation.value = null
            _locationResult.value = LocationResult.Unavailable
            if (continuation.isActive) continuation.resume(LocationResult.Unavailable)
        }
    }

    @SuppressLint("MissingPermission")
    fun fetchCurrentLocation(onResult: ((LocationInfo?) -> Unit)? = null) {
        if (!hasLocationPermission()) {
            _userLocation.value = null
            _locationResult.value = LocationResult.Unavailable
            onResult?.invoke(null)
            return
        }

        try {
            val cancellationTokenSource = CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cancellationTokenSource.token
            ).addOnSuccessListener { location: Location? ->
                if (location != null) {
                    Log.d("LocationManager", "[LOCATION_FETCHED accuracy=${location.accuracy}m]")
                    val locInfo = LocationInfo(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        address = "GNSS Fix (${String.format(Locale.US, "%.4f", location.latitude)}, ${String.format(
                            Locale.US, "%.4f", location.longitude)})"
                    )
                    _userLocation.value = locInfo
                    _locationResult.value = LocationResult.Success(locInfo, location.accuracy)
                    onResult?.invoke(locInfo)
                } else {
                    fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc: Location? ->
                        if (lastLoc != null) {
                            Log.d("LocationManager", "[LOCATION_FETCHED accuracy=${lastLoc.accuracy}m]")
                            val locInfo = LocationInfo(
                                latitude = lastLoc.latitude,
                                longitude = lastLoc.longitude,
                                address = "Last Known Fix (${String.format(Locale.US, "%.4f", lastLoc.latitude)}, ${String.format(
                                    Locale.US, "%.4f", lastLoc.longitude)})"
                            )
                            _userLocation.value = locInfo
                            _locationResult.value = LocationResult.Success(locInfo, lastLoc.accuracy)
                            onResult?.invoke(locInfo)
                        } else {
                            _userLocation.value = null
                            _locationResult.value = LocationResult.Unavailable
                            onResult?.invoke(null)
                        }
                    }.addOnFailureListener {
                        _userLocation.value = null
                        _locationResult.value = LocationResult.Unavailable
                        onResult?.invoke(null)
                    }
                }
            }.addOnFailureListener {
                _userLocation.value = null
                _locationResult.value = LocationResult.Unavailable
                onResult?.invoke(null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _userLocation.value = null
            _locationResult.value = LocationResult.Unavailable
            onResult?.invoke(null)
        }
    }
}
