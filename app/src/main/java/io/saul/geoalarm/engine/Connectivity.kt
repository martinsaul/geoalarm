package io.saul.geoalarm.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

object Connectivity {
    /** Last state reported by [watch]; authoritative while the process is alive. */
    @Volatile private var known: Boolean? = null

    /** True when the default network can reach the internet (validation not required). */
    fun isOnline(context: Context): Boolean = known ?: query(context)

    private fun query(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Calls [onChange] whenever we go on- or offline, for as long as the process lives.
     * Trusts the default-network callback's own events: during onLost, activeNetwork can still
     * report the network that is going away.
     */
    fun watch(context: Context, onChange: (Boolean) -> Unit) {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        var last: Boolean? = null
        fun report(now: Boolean) {
            known = now
            if (now != last) {
                last = now
                onChange(now)
            }
        }
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
                report(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))

            override fun onLost(network: Network) = report(false)
        })
    }
}
