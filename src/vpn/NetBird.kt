package dev.morin.sunrelay.vpn

import androidx.compose.runtime.Composable
import dev.morin.sunrelay.R
import dev.morin.sunrelay.config.SettingsReader
import dev.morin.sunrelay.config.SettingsWriter
import dev.morin.sunrelay.util.objects
import org.json.JSONArray

data class NetBird(val url: String = "", val token: String = "") : ApiVpn() {
    override val key: String get() = "netbird"
    override val label: String get() = "NetBird"
    override val blurb: String get() = "Finds hosts with the NetBird API (cloud or self-hosted)."
    override val monogram: String get() = "NB"
    override val logo: Int get() = R.drawable.ic_vpn_netbird
    override val accessHint: String get() = " (NetBird access policies)"
    override val canScan: Boolean get() = token.isNotBlank()

    override fun writeTo(settings: SettingsWriter) {
        settings.string(URL, url.trim())
        settings.string(TOKEN, token.trim())
    }

    override fun devices(): List<VpnDevice> {
        val base = http.httpsBase(url.ifBlank { DEFAULT_URL })
        val result = http.get("$base/api/peers", "Token ${token.trim()}")
        if (result.isNotFound) throw IllegalStateException("No NetBird API at $base")
        return JSONArray(result.bodyOrThrow()).objects().mapNotNull { peer ->
            val ip = peer.optString("ip").takeIf { '.' in it } ?: return@mapNotNull null
            val dns = peer.optString("dns_label")
            VpnDevice(
                name = dns.substringBefore('.').ifBlank { peer.optString("name").ifBlank { ip } },
                ip = ip,
                fqdn = dns,
                os = peer.optString("os"),
                online = !peer.has("connected") || peer.optBoolean("connected"),
            )
        }
    }

    @Composable
    override fun ApiFields(onChange: (Vpn) -> Unit) {
        VpnHint(
            "SunRelay lists your peers through the NetBird API. In the dashboard, open Team → Users → your user " +
                "and create a personal access token. A self-hosted server needs its management URL; leave it blank for NetBird cloud.",
        )
        OpenUrlButton("Open NetBird dashboard", CONSOLE_URL)
        VpnField(url, "Server URL (blank = $DEFAULT_URL)") { onChange(copy(url = it)) }
        VpnField(token, "Personal access token", secret = true) { onChange(copy(token = it)) }
    }

    @Composable
    override fun ConnectActions(connected: Boolean, onAction: () -> Unit) =
        OpenVpnAppButton(APP_PACKAGE, label, onAction)

    companion object {
        private const val DEFAULT_URL = "https://api.netbird.io"
        private const val CONSOLE_URL = "https://app.netbird.io/"
        private const val APP_PACKAGE = "io.netbird.client"
        private const val URL = "nbUrl"
        private const val TOKEN = "nbToken"

        fun readFrom(settings: SettingsReader) =
            NetBird(settings.string(URL).orEmpty(), settings.string(TOKEN).orEmpty())
    }
}
