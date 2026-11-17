package dev.morin.sunrelay.vpn

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.morin.sunrelay.R
import dev.morin.sunrelay.config.SettingsReader
import dev.morin.sunrelay.config.SettingsWriter
import dev.morin.sunrelay.net.SystemScreens
import dev.morin.sunrelay.util.objects
import dev.morin.sunrelay.util.strings
import org.json.JSONObject

data class Tailscale(val clientId: String = "", val clientSecret: String = "") : ApiVpn() {
    override val key: String get() = KEY
    override val label: String get() = "Tailscale"
    override val blurb: String get() = "Connects Tailscale for you; finds hosts with the Tailscale API."
    override val monogram: String get() = "TS"
    override val logo: Int get() = R.drawable.ic_vpn_tailscale
    override val logoOnDark: Int get() = R.drawable.ic_vpn_tailscale_dark
    override val logoScale: Float get() = 0.7f
    override val scanLabel: String get() = "Scan tailnet"
    override val accessHint: String get() = " (Tailscale ACLs)"
    override val canScan: Boolean get() = clientId.isNotBlank() && clientSecret.isNotBlank()

    override fun writeTo(settings: SettingsWriter) {
        settings.string(CLIENT_ID, clientId.trim())
        settings.string(CLIENT_SECRET, clientSecret.trim())
    }

    override fun devices(): List<VpnDevice> {
        val body = http.get("$API/tailnet/-/devices", "Bearer ${accessToken()}")
            .bodyOrThrow(mapOf(403 to "The OAuth client needs the devices:core:read scope"))
        return JSONObject(body).optJSONArray("devices").objects().mapNotNull { d ->
            val ip = d.optJSONArray("addresses").strings().firstOrNull { '.' in it } ?: return@mapNotNull null
            val fqdn = d.optString("name")
            VpnDevice(
                name = fqdn.substringBefore('.').ifBlank { d.optString("hostname") },
                ip = ip,
                fqdn = fqdn,
                os = d.optString("os"),
                online = !d.has("connectedToControl") || d.optBoolean("connectedToControl"),
            )
        }
    }

    private fun accessToken(): String {
        val rejected = "Tailscale rejected the OAuth client ID/secret"
        val body = http.postForm(
            "$API/oauth/token",
            mapOf("grant_type" to "client_credentials", "client_id" to clientId.trim(), "client_secret" to clientSecret.trim()),
        ).bodyOrThrow(mapOf(400 to rejected, 401 to rejected))
        return JSONObject(body).getString("access_token")
    }

    @Composable
    override fun ApiFields(onChange: (Vpn) -> Unit) {
        VpnHint(
            "SunRelay finds Sunshine hosts through the Tailscale API. Create a read-only OAuth client once: " +
                "Tailscale admin console → Settings → OAuth clients → Generate, tick Devices › Core › Read.",
        )
        OpenUrlButton("Open Tailscale admin console", OAUTH_CONSOLE_URL)
        VpnField(clientId, "OAuth client ID") { onChange(copy(clientId = it)) }
        VpnField(clientSecret, "OAuth client secret", secret = true) { onChange(copy(clientSecret = it)) }
    }

    @Composable
    override fun ConnectActions(connected: Boolean, onAction: () -> Unit) = TailscaleAppActions(connected, onAction)

    companion object {
        const val KEY = "tailscale"
        private const val API = "https://api.tailscale.com/api/v2"
        private const val OAUTH_CONSOLE_URL = "https://login.tailscale.com/admin/settings/oauth"
        private const val CLIENT_ID = "tsId"
        private const val CLIENT_SECRET = "tsSecret"

        fun readFrom(settings: SettingsReader) =
            Tailscale(settings.string(CLIENT_ID).orEmpty(), settings.string(CLIENT_SECRET).orEmpty())
    }
}

object TailscaleApp {
    private const val PACKAGE = "com.tailscale.ipn"

    fun open(context: Context) {
        val store = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PACKAGE")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$PACKAGE"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        SystemScreens.open(context, *listOfNotNull(context.packageManager.getLaunchIntentForPackage(PACKAGE), store, web).toTypedArray())
    }

    fun connect(context: Context) =
        context.sendBroadcast(Intent("$PACKAGE.CONNECT_VPN").setClassName(PACKAGE, "$PACKAGE.IPNReceiver"))
}

@Composable
internal fun TailscaleAppActions(connected: Boolean, onAction: () -> Unit) {
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { TailscaleApp.connect(context); onAction() }, enabled = !connected) { Text("Connect") }
        OutlinedButton(onClick = { TailscaleApp.open(context) }) { Text("Open Tailscale") }
    }
}
