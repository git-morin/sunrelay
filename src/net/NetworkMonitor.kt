package dev.morin.sunrelay.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.Inet4Address

class NetworkMonitor(context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    fun isVpnActive(): Boolean = networks().any { isVpn(it) }

    fun hotspotAddresses(): List<TetherAddress> {
        val uplinks = uplinkInterfaces()
        return LocalInterfaces.up()
            .filter { it.name !in uplinks }
            .flatMap { ni ->
                val kind = TetherKind.of(ni.name) ?: return@flatMap emptyList()
                ni.inetAddresses.toList()
                    .filterIsInstance<Inet4Address>()
                    .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress && it.isSiteLocalAddress }
                    .mapNotNull { it.hostAddress?.let { ip -> TetherAddress(ip, kind) } }
            }
            .sortedBy { it.kind.ordinal }
    }

    fun vpnSubnets(): List<Ipv4Subnet> = networks().filter { isVpn(it) }.flatMap { network ->
        val link = connectivity.getLinkProperties(network) ?: return@flatMap emptyList()
        val own = link.linkAddresses.mapNotNull { (it.address as? Inet4Address)?.let(Ipv4Subnet::toInt) }
        val fromAddresses = link.linkAddresses.mapNotNull { la ->
            (la.address as? Inet4Address)?.let { Ipv4Subnet.toInt(it) to la.prefixLength }
        }
        val fromRoutes = link.routes.mapNotNull { route ->
            val d = route.destination
            (d.address as? Inet4Address)?.takeIf { d.prefixLength in 1..31 }?.let { Ipv4Subnet.toInt(it) to d.prefixLength }
        }
        (fromAddresses + fromRoutes).mapNotNull { (address, prefix) -> Ipv4Subnet.scannable(address, prefix, own) }
    }.distinct()

    private fun uplinkInterfaces(): Set<String> = networks().mapNotNull { network ->
        val caps = connectivity.getNetworkCapabilities(network) ?: return@mapNotNull null
        val isUplink = !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_LOCAL_NETWORK)
        if (isUplink) connectivity.getLinkProperties(network)?.interfaceName else null
    }.toSet()

    private fun isVpn(network: Network) =
        connectivity.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true

    @Suppress("DEPRECATION")
    private fun networks(): List<Network> = connectivity.allNetworks.toList()

}
