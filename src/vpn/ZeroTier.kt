package dev.morin.sunrelay.vpn

import androidx.compose.runtime.Composable
import dev.morin.sunrelay.R
import dev.morin.sunrelay.config.SettingsReader
import dev.morin.sunrelay.config.SettingsWriter
import dev.morin.sunrelay.util.objects
import dev.morin.sunrelay.util.strings
import org.json.JSONArray
import org.json.JSONObject

data class ZeroTier(val token: String = "", val network: String = "") : ApiVpn() {
    override val key: String get() = "zerotier"
    override val label: String get() = "ZeroTier"
    override val blurb: String get() = "Finds hosts with the ZeroTier Central API."
    override val monogram: String get() = "ZT"
    override val logo: Int get() = R.drawable.ic_vpn_zerotier
    override val logoOnDark: Int get() = R.drawable.ic_vpn_zerotier_dark
    override val accessHint: String get() = " (ZeroTier flow rules)"
    override val canScan: Boolean get() = token.isNotBlank() && network.isNotBlank()

    override fun writeTo(settings: SettingsWriter) {
        settings.secret(TOKEN, token.trim())
        settings.string(NETWORK, network.trim())
    }

    override fun devices(): List<VpnDevice> {
        val id = network.trim().lowercase()
        require(NETWORK_ID.matches(id)) { "A ZeroTier network ID is 16 hex characters" }
        val result = http.get("$API/network/$id/member", "token ${token.trim()}")
        if (result.isNotFound) throw IllegalStateException("ZeroTier: no network $id on this account")
        val now = System.currentTimeMillis()
        return JSONArray(result.bodyOrThrow()).objects().mapNotNull { member -> member.toDevice(now) }
    }

    private fun JSONObject.toDevice(now: Long): VpnDevice? {
        val config = optJSONObject("config") ?: return null
        if (!config.optBoolean("authorized", true)) return null
        val ip = config.optJSONArray("ipAssignments").strings().firstOrNull { '.' in it } ?: return null
        val lastSeen = optLong("lastSeen", 0L)
        return VpnDevice(
            name = optString("name").ifBlank { optString("nodeId").ifBlank { ip } },
            ip = ip,
            online = when {
                has("online") -> optBoolean("online")
                lastSeen > 0 -> now - lastSeen < ONLINE_WINDOW_MS
                else -> true
            },
        )
    }

    @Composable
    override fun ApiFields(onChange: (Vpn) -> Unit) {
        VpnHint(
            "SunRelay lists your network's members through ZeroTier Central. Create an API token under " +
                "Account → API Access Tokens, and copy the 16-character network ID from the Networks page. " +
                "ZeroTier tokens can't be limited to read-only, so this one can manage your account: it's kept only on this phone.",
        )
        OpenUrlButton("Open ZeroTier Central", CONSOLE_URL)
        VpnField(network, "Network ID") { onChange(copy(network = it)) }
        VpnField(token, "API token", secret = true) { onChange(copy(token = it)) }
    }

    @Composable
    override fun ConnectActions(connected: Boolean, onAction: () -> Unit) =
        OpenVpnAppButton(APP_PACKAGE, label, onAction)

    companion object {
        private const val API = "https://api.zerotier.com/api/v1"
        private const val CONSOLE_URL = "https://my.zerotier.com/account"
        private const val APP_PACKAGE = "com.zerotier.one"
        private const val ONLINE_WINDOW_MS = 10 * 60_000L
        private const val TOKEN = "ztToken"
        private const val NETWORK = "ztNetwork"
        private val NETWORK_ID = Regex("[0-9a-f]{16}")

        fun readFrom(settings: SettingsReader) =
            ZeroTier(settings.secret(TOKEN).orEmpty(), settings.string(NETWORK).orEmpty())
    }
}
