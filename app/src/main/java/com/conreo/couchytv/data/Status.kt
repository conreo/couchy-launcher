package com.conreo.couchytv.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class NetStatus(
    val wifi: Boolean = false,
    val ethernet: Boolean = false,
    val vpn: Boolean = false,
) {
    val connected get() = wifi || ethernet
}

/** Emits the current network status and updates on every connectivity change. */
fun networkStatusFlow(context: Context): Flow<NetStatus> = callbackFlow {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun compute(): NetStatus {
        var wifi = false
        var eth = false
        var vpn = false
        @Suppress("DEPRECATION")
        val networks = runCatching { cm.allNetworks }.getOrNull().orEmpty()
        for (network in networks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) wifi = true
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) eth = true
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) vpn = true
        }
        return NetStatus(wifi, eth, vpn)
    }

    trySend(compute())

    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { trySend(compute()) }
        override fun onLost(network: Network) { trySend(compute()) }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            trySend(compute())
        }
    }
    val request = NetworkRequest.Builder().build()
    cm.registerNetworkCallback(request, callback)
    awaitClose { cm.unregisterNetworkCallback(callback) }
}
