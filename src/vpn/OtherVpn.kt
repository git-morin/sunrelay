package dev.morin.sunrelay.vpn

import android.content.Context
import androidx.compose.runtime.Composable
import dev.morin.sunrelay.R
import dev.morin.sunrelay.config.SettingsWriter
import dev.morin.sunrelay.net.LocalInterfaces
import dev.morin.sunrelay.net.NetworkMonitor

object OtherVpn : Vpn {
    private const val MAX_ADDRESSES = 4096

    override val key: String get() = "vpn"
    override val label: String get() = "Another VPN"
    override val blurb: String get() = "Any other VPN app. Finds hosts by scanning the VPN's subnet."
    override val logo: Int get() = R.drawable.ic_vpn_generic
    override val logoOnDark: Int get() = R.drawable.ic_vpn_generic_dark
    override val logoScale: Float get() = 0.8f
    override val monogram: String get() = "VPN"
    override val connectName: String get() = "your VPN"
    override val hostLabel: String get() = "Host name or IP"
    override val hasApi: Boolean get() = false
    override val canScan: Boolean get() = true

    override fun writeTo(settings: SettingsWriter) {}

    override fun scan(context: Context, onProgress: (ScanProgress) -> Unit): List<FoundHost> {
        val network = NetworkMonitor(context)
        val subnets = network.vpnSubnets()
        if (subnets.isEmpty()) {
            throw IllegalStateException(
                if (!network.isVpnActive()) "No VPN is connected"
                else "The VPN doesn't expose a subnet to scan. Enter the host yourself.",
            )
        }
        val own = LocalInterfaces.ipv4Addresses()
        val addresses = subnets.asSequence().flatMap { it.hostAddresses() }
            .distinct().filter { it !in own }.take(MAX_ADDRESSES).toList()
        return HostProber.forSubnets.probe(addresses, { it }, ScanProgress.reporter("addresses", onProgress)) { ip, info ->
            FoundHost(VpnDevice(name = info.hostname ?: ip, ip = ip), info)
        }
    }

    @Composable
    override fun ConnectActions(connected: Boolean, onAction: () -> Unit) = OpenVpnAppButton(null, label, onAction)
}
