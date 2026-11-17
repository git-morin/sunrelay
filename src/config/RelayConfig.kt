package dev.morin.sunrelay.config

import dev.morin.sunrelay.sunshine.SunshineApi
import dev.morin.sunrelay.ui.theme.Accent
import dev.morin.sunrelay.ui.theme.ThemeMode
import dev.morin.sunrelay.vpn.Vpn
import dev.morin.sunrelay.vpn.Vpns

data class RelayConfig(
    val network: NetworkSettings = NetworkSettings(),
    val target: String = "",
    val relay: RelaySettings = RelaySettings(),
    val login: SunshineLogin = SunshineLogin(),
    val appearance: Appearance = Appearance(),
    val onboarded: Boolean = false,
    val autoCheck: Boolean = true,
    val developer: DeveloperSettings = DeveloperSettings(),
) {
    val vpn: Vpn get() = network.selected
    val hasTarget: Boolean get() = target.isNotBlank()
    val canScan: Boolean get() = vpn.canScan
    val canUseSunshineApi: Boolean get() = hasTarget && login.isComplete

    fun sunshineApi(): SunshineApi = login.apiFor(target)
    fun withVpn(vpn: Vpn) = copy(network = network.updated(vpn))
    fun selecting(vpn: Vpn) = copy(network = network.selecting(vpn))
}

data class NetworkSettings(
    val selectedKey: String = Vpns.DEFAULT_KEY,
    val vpns: List<Vpn> = Vpns.defaults(),
) {
    val selected: Vpn get() = vpns.firstOrNull { it.key == selectedKey } ?: vpns.first()

    fun selecting(vpn: Vpn) = copy(selectedKey = vpn.key)
    fun updated(vpn: Vpn) = copy(vpns = vpns.map { if (it.key == vpn.key) vpn else it })
}

data class RelaySettings(
    val tcpPorts: String = DEFAULT_TCP_PORTS,
    val udpPorts: String = "47998,47999,48000,48002,48010",
    val udpIdleSec: Int = 60,
    val mdns: Boolean = true,
) {
    val tcpPortList: List<Int> get() = parsePorts(tcpPorts)
    val udpPortList: List<Int> get() = parsePorts(udpPorts)
    val udpIdleMs: Long get() = udpIdleSec * 1000L

    fun withUdpIdle(seconds: Int) = copy(udpIdleSec = seconds.coerceIn(IDLE_RANGE))

    companion object {
        private const val DEFAULT_TCP_PORTS = "47984,47989,48010"

        /** Older builds also relayed 47990, Sunshine's web admin UI, which Moonlight never needs. */
        private const val LEGACY_TCP_PORTS = "47984,47989,47990,48010"

        fun migrateTcpPorts(saved: String): String = if (saved == LEGACY_TCP_PORTS) DEFAULT_TCP_PORTS else saved

        val IDLE_RANGE = 5..3600

        /** Entries in a port list that aren't a port number, so the UI can say they're ignored. */
        fun invalidPorts(s: String): List<String> = tokens(s).filter { it.toIntOrNull() !in PORT_RANGE }

        private val PORT_RANGE = 1..65535

        private fun tokens(s: String) = s.split(',', ' ').map { it.trim() }.filter { it.isNotEmpty() }

        private fun parsePorts(s: String): List<Int> = tokens(s).mapNotNull { it.toIntOrNull() }.filter { it in PORT_RANGE }.distinct()
    }
}

data class SunshineLogin(
    val user: String = "",
    val password: String = "",
    val pairedUuid: String = "",
) {
    val isComplete: Boolean get() = user.isNotBlank() && password.isNotEmpty()

    fun apiFor(host: String) = SunshineApi(host.trim(), user.trim(), password)
}

data class DeveloperSettings(
    val unlocked: Boolean = false,
    val prereleases: Boolean = false,
) {
    val includePrereleases: Boolean get() = unlocked && prereleases
}

data class Appearance(
    val accent: Accent = Accent.Sunshine,
    val themeMode: ThemeMode = ThemeMode.System,
)
