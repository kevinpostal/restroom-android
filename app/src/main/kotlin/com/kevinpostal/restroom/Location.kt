package com.kevinpostal.restroom

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/// Platform location, bytes-only style: emits the last known fix first (if recent) so the first
/// search starts immediately, then the current fix when the provider answers.
object Locator {
    private const val LAST_KNOWN_MAX_AGE_MS = 120_000L
    private const val CURRENT_FIX_TIMEOUT_MS = 10_000L

    fun granted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun fixes(context: Context): Flow<Location> = flow {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter(lm::isProviderEnabled)
        val last = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < LAST_KNOWN_MAX_AGE_MS) emit(last)
        for (provider in providers) {
            val fix = withTimeoutOrNull(CURRENT_FIX_TIMEOUT_MS) { current(lm, provider) }
            if (fix != null) {
                if (last == null || fix.time != last.time) emit(fix)
                return@flow
            }
        }
        // Provider never answered (common on the emulator before a `geo fix`): any last-known fix is better than nothing.
        if (last != null && System.currentTimeMillis() - last.time >= LAST_KNOWN_MAX_AGE_MS) emit(last)
    }

    @SuppressLint("MissingPermission")
    private suspend fun current(lm: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { cont ->
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            lm.getCurrentLocation(provider, signal, { it.run() }) { loc -> if (cont.isActive) cont.resume(loc) }
        }
}
