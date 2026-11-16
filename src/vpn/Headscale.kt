package dev.morin.sunrelay.vpn

import androidx.compose.runtime.Composable
import dev.morin.sunrelay.R
import dev.morin.sunrelay.config.SettingsReader
import dev.morin.sunrelay.config.SettingsWriter
import dev.morin.sunrelay.util.objects
import dev.morin.sunrelay.util.strings
import org.json.JSONObject

data class Headscale(val url: String = "", val apiKey: String = "") : ApiVpn() {
    override val key: String get() = "headscale"
    override val label: String get() = "Headscale"
    override val blurb: String get() = "Self-hosted Tailscale server. Uses the Tailscale app; finds hosts with the Headscale API."
    override val monogram: String get() = "HS"
    override val logo: Int get() = R.drawable.ic_vpn_headscale
    override val scanLabel: String get() = "Scan tailnet"
    override val accessHint: String get() = " (Headscale ACLs)"
    override val canScan: Boolean get() = url.isNotBlank() && apiKey.isNotBlank()

    override fun writeTo(settings: SettingsWriter) {
        settings.string(URL, url.trim())
        settings.string(API_KEY, apiKey.trim())
    }

    override fun devices(): List<VpnDevice> {
        val base = http.httpsBase(url)
        val authorization = "Bearer ${apiKey.trim()}"
        val endpoint = ENDPOINTS.firstNotNullOfOrNull { (path, field) ->
            http.get("$base$path", authorization).takeUnless { it.isNotFound }?.let { it to field }
        } ?: throw IllegalStateException("No Headscale API at $base")
        val (result, field) = endpoint
        return JSONObject(result.bodyOrThrow()).optJSONArray(field).objects().mapNotNull { node ->
            val ip = node.optJSONArray("ipAddresses").strings().firstOrNull { '.' in it } ?: return@mapNotNull null
            VpnDevice(
                name = node.optString("givenName").ifBlank { node.optString("name").ifBlank { ip } },
                ip = ip,
                online = !node.has("online") || node.optBoolean("online"),
            )
        }
    }

    @Composable
    override fun ApiFields(onChange: (Vpn) -> Unit) {
        VpnHint(
            "SunRelay lists your nodes through the Headscale API. On the server, run `headscale apikeys create` " +
                "and paste the key here. The server URL must use https.",
        )
        VpnField(url, "Server URL") { onChange(copy(url = it)) }
        VpnField(apiKey, "API key", secret = true) { onChange(copy(apiKey = it)) }
    }

    @Composable
    override fun ConnectActions(connected: Boolean, onAction: () -> Unit) = TailscaleAppActions(connected, onAction)

    companion object {
        private const val URL = "hsUrl"
        private const val API_KEY = "hsKey"
        private val ENDPOINTS = listOf("/api/v1/node" to "nodes", "/api/v1/machine" to "machines")

        fun readFrom(settings: SettingsReader) =
            Headscale(settings.string(URL).orEmpty(), settings.string(API_KEY).orEmpty())
    }
}
