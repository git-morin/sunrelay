package dev.morin.sunrelay.sunshine

data class ServerInfo(
    val hostname: String?,
    val state: String?,
    val appVersion: String?,
) {
    val stateLabel: String?
        get() = when (state) {
            null -> null
            "SUNSHINE_SERVER_FREE" -> "ready"
            "SUNSHINE_SERVER_BUSY" -> "game running"
            else -> state.removePrefix("SUNSHINE_SERVER_").lowercase()
        }

    val summary: String get() = "${hostname ?: "?"} · ${stateLabel ?: "?"}"
}
