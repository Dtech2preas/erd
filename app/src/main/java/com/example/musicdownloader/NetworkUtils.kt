package com.example.musicdownloader

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException

object NetworkUtils {
    const val USER_AGENT = "com.google.android.youtube/19.29.35 (Linux; U; Android 14; en_US) gzip"

    fun isWifiConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}

object IPv4Dns : okhttp3.Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = okhttp3.Dns.SYSTEM.lookup(hostname)
        val ipv4Addresses = addresses.filter { it is Inet4Address }

        if (ipv4Addresses.isNotEmpty()) {
            return ipv4Addresses
        }

        if (addresses.isEmpty()) {
             throw UnknownHostException("No addresses found for $hostname")
        }

        if (ipv4Addresses.isEmpty()) {
             throw UnknownHostException("No IPv4 addresses found for $hostname")
        }

        return ipv4Addresses
    }
}
