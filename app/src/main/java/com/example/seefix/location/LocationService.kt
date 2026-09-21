package com.example.seefix.location

import android.content.Context
import android.location.LocationManager as SystemLocationManager

/**
 * Location provider helper wrapping system location checks and SeeFix LocationManager.
 */
class LocationService(private val context: Context) {
    val locationManager = LocationManager(context)

    fun isLocationEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? SystemLocationManager
        return lm?.isProviderEnabled(SystemLocationManager.GPS_PROVIDER) == true ||
                lm?.isProviderEnabled(SystemLocationManager.NETWORK_PROVIDER) == true
    }
}
