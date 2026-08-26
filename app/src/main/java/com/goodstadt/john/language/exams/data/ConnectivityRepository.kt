package com.goodstadt.john.language.exams.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectivityRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * A Flow that emits the latest network connection status.
     * It's lifecycle-aware and will update automatically.
     */
    val isOnline: Flow<Boolean> = callbackFlow {
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true) // Network is available
            }
            override fun onLost(network: Network) {
                trySend(false) // Network is lost
            }
        }

        // Register the callback
        connectivityManager.registerDefaultNetworkCallback(networkCallback)

        // Set initial value
        trySend(isCurrentlyOnline())

        // Unregister the callback when the Flow is cancelled
        awaitClose {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }.distinctUntilChanged() // Only emit when the status actually changes

    /**
     * A simple synchronous check for the current network status.
     * Useful for one-off checks.
     */
    /*
    13. CLAUDE Deprecated connectivity API
File: ConnectivityRepository.kt line 52
connectivityManager.activeNetworkInfo and isConnectedOrConnecting are deprecated since API 29. Your minSdk is 28, so this works, but will eventually stop working reliably.

Fix: Use connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork) to check for NET_CAPABILITY_INTERNET.
     */
    fun isCurrentlyOnlineObsolete(): Boolean {
        val activeNetwork = connectivityManager.activeNetworkInfo
        return activeNetwork?.isConnectedOrConnecting == true
    }
    fun isCurrentlyOffline(): Boolean {
        return !isCurrentlyOnline()
    }
    fun isCurrentlyOnline(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // 1. Get the currently active network
        val activeNetwork = connectivityManager.activeNetwork ?: return false

        // 2. Get the capabilities for that network
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

        // 3. Check for specific transport types and the INTERNET capability
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
                (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
    }
}