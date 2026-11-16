package dev.morin.sunrelay.net

enum class TetherKind(val label: String, private val prefixes: List<String>) {
    WiFi("Wi-Fi hotspot", listOf("wlan", "swlan", "ap")),
    Usb("USB tethering", listOf("rndis", "ncm", "usb")),
    Bluetooth("Bluetooth tethering", listOf("bt-pan", "bt")),
    Ethernet("Ethernet tethering", listOf("eth")),
    ;

    companion object {
        fun of(interfaceName: String): TetherKind? =
            entries.firstOrNull { kind -> kind.prefixes.any { interfaceName.startsWith(it) } }
    }
}

data class TetherAddress(val ip: String, val kind: TetherKind)
