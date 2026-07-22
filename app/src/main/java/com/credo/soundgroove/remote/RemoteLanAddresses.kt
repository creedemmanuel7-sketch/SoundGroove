package com.credo.soundgroove.remote

import java.net.Inet4Address
import java.net.NetworkInterface

/** Adresses IPv4 LAN utilisables pour le pairing remote. */
object RemoteLanAddresses {
    fun primaryIpv4(): String? = allIpv4().firstOrNull()

    fun allIpv4(): List<String> {
        val result = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            for (nic in interfaces) {
                if (!nic.isUp || nic.isLoopback) continue
                val addrs = nic.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        result += addr.hostAddress ?: continue
                    }
                }
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return result.distinct()
    }
}
