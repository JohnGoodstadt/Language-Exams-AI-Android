package com.goodstadt.john.language.exams.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
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
    fun isCurrentlyOnline(): Boolean {
        val activeNetwork = connectivityManager.activeNetworkInfo
        return activeNetwork?.isConnectedOrConnecting == true
    }
}