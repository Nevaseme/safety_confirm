package io.github.nevaseme.plandmk1.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AndroidPositionSource(private val context: Context) {
    @Suppress("DEPRECATION")
    suspend fun acquire(): PositionFix = suspendCancellableCoroutine { continuation ->
        val manager = context.getSystemService(LocationManager::class.java)
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            continuation.resumeWithException(IllegalStateException("アプリ設定で位置情報を許可してください")); return@suspendCancellableCoroutine
        }
        val providers = listOfNotNull(if (fine) LocationManager.GPS_PROVIDER else null, LocationManager.NETWORK_PROVIDER)
            .filter { it in manager.allProviders && manager.isProviderEnabled(it) }
        if (providers.isEmpty()) {
            continuation.resumeWithException(IllegalStateException("端末の位置情報をONにしてください")); return@suspendCancellableCoroutine
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (!continuation.isActive || !location.hasAccuracy() || location.time <= 0) return
                manager.removeUpdates(this)
                continuation.resume(PositionFix(location.latitude, location.longitude, location.accuracy.toDouble(), location.time))
            }
            override fun onProviderDisabled(provider: String) {
                if (continuation.isActive && providers.none { manager.isProviderEnabled(it) }) {
                    manager.removeUpdates(this)
                    continuation.resumeWithException(IllegalStateException("端末の位置情報をONにしてください"))
                }
            }
            override fun onProviderEnabled(provider: String) = Unit
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        continuation.invokeOnCancellation { manager.removeUpdates(listener) }
        try { providers.forEach { manager.requestSingleUpdate(it, listener, Looper.getMainLooper()) } }
        catch (e: Exception) {
            manager.removeUpdates(listener)
            if (continuation.isActive) continuation.resumeWithException(e)
        }
    }
}
