package dev.morin.sunrelay.net

import java.net.Inet4Address
import java.net.NetworkInterface

object LocalInterfaces {
    fun up(): List<NetworkInterface> =
        runCatching { NetworkInterface.getNetworkInterfaces()?.toList() }.getOrNull().orEmpty()
            .filter { runCatching { it.isUp }.getOrDefault(false) }

    fun ipv4Addresses(): Set<String> =
        up().flatMap { it.inetAddresses.toList() }.filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress }.toSet()
}
