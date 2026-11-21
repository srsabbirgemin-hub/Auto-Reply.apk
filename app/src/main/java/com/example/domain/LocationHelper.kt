package com.example.domain

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

class LocationHelper(private val context: Context) {

    fun hasCoarseLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    suspend fun getApproximateLocationSummary(): String? = withContext(Dispatchers.IO) {
        if (!hasCoarseLocationPermission()) return@withContext null

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext null

        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
            LocationManager.GPS_PROVIDER
        ).filter { provider ->
            runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
        }

        var bestLocation: Location? = null
        for (provider in providers) {
            val loc = runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            if (loc != null && (bestLocation == null || loc.time > bestLocation.time)) {
                bestLocation = loc
            }
        }

        val resolved = bestLocation ?: return@withContext null
        val lat = String.format(Locale.US, "%.4f", resolved.latitude)
        val lon = String.format(Locale.US, "%.4f", resolved.longitude)
        val areaLabel = reverseGeocodeCity(resolved.latitude, resolved.longitude)
        return@withContext if (!areaLabel.isNullOrBlank()) {
            "$areaLabel (approx. $lat, $lon)"
        } else {
            "approx. coordinates ($lat, $lon)"
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun reverseGeocodeCity(lat: Double, lon: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                runCatching {
                    geocoder.getFromLocation(lat, lon, 1) { addresses ->
                        val addr = addresses.firstOrNull()
                        val label = listOfNotNull(addr?.subLocality, addr?.locality, addr?.adminArea)
                            .distinct()
                            .joinToString(", ")
                            .ifBlank { null }
                        if (cont.isActive) cont.resume(label)
                    }
                }.onFailure {
                    if (cont.isActive) cont.resume(null)
                }
            }
        } else {
            runCatching {
                val addr = geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()
                listOfNotNull(addr?.subLocality, addr?.locality, addr?.adminArea)
                    .distinct()
                    .joinToString(", ")
                    .ifBlank { null }
            }.getOrNull()
        }
    }
}
