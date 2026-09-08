package com.george.camarawatch.mobile.preview

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkAddress {
    fun localIpv4(): String? {
        val preferred = mutableListOf<String>()
        val fallback = mutableListOf<String>()
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
        for (nif in interfaces) {
            if (!nif.isUp || nif.isLoopback) continue
            val name = nif.name.lowercase()
            for (addr in nif.inetAddresses) {
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    val ip = addr.hostAddress ?: continue
                    if (name.contains("wlan") || name.contains("ap") || name.contains("swlan") || name.contains("softap")) {
                        preferred += ip
                    } else {
                        fallback += ip
                    }
                }
            }
        }
        return preferred.firstOrNull() ?: fallback.firstOrNull()
    }
}
