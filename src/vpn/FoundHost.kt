package dev.morin.sunrelay.vpn

import dev.morin.sunrelay.sunshine.ServerInfo
import java.net.InetAddress

data class VpnDevice(
    val name: String,
    val ip: String,
    val fqdn: String = "",
    val os: String = "",
    val online: Boolean = true,
)

data class FoundHost(val device: VpnDevice, val info: ServerInfo) {
    val details: String
        get() = listOfNotNull(
            device.ip.takeIf { it != device.name },
            device.os.takeIf { it.isNotBlank() },
            info.stateLabel,
            info.appVersion?.let { "v$it" },
        ).joinToString(" · ")

    fun isTarget(target: String): Boolean =
        target == device.ip || dnsNames().any { it.equals(target, ignoreCase = true) }

    fun preferredAddress(): String = dnsNames().firstOrNull { resolvesToDevice(it) } ?: device.ip

    private fun dnsNames() = listOf(device.name, device.fqdn.trimEnd('.')).filter { it.isNotBlank() && it != device.ip }

    private fun resolvesToDevice(name: String) =
        runCatching { InetAddress.getByName(name).hostAddress == device.ip }.getOrDefault(false)
}
