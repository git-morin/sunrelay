package dev.morin.sunrelay.vpn

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import dev.morin.sunrelay.config.SettingsReader
import dev.morin.sunrelay.config.SettingsWriter
import dev.morin.sunrelay.net.LocalInterfaces

interface Vpn {
    val key: String
    val label: String
    val blurb: String
    val monogram: String
    @get:DrawableRes val logo: Int? get() = null
    @get:DrawableRes val logoOnDark: Int? get() = logo
    val logoScale: Float get() = 1f
    val connectName: String get() = label
    val hostLabel: String get() = "$label name or IP"
    val scanLabel: String get() = "Scan network"
    val accessHint: String get() = ""
    val hasApi: Boolean
    val canScan: Boolean

    fun scan(context: Context, onProgress: (ScanProgress) -> Unit): List<FoundHost>
    fun writeTo(settings: SettingsWriter)

    @Composable
    fun ApiFields(onChange: (Vpn) -> Unit) {}

    @Composable
    fun ConnectActions(connected: Boolean, onAction: () -> Unit)
}

abstract class ApiVpn : Vpn {
    final override val hasApi: Boolean get() = true

    protected val http: JsonHttp get() = JsonHttp(label)

    protected abstract fun devices(): List<VpnDevice>

    final override fun scan(context: Context, onProgress: (ScanProgress) -> Unit): List<FoundHost> {
        onProgress(ScanProgress("Listing $label devices…"))
        val own = LocalInterfaces.ipv4Addresses()
        val candidates = devices().filter { it.ip !in own }.sortedByDescending { it.online }
        return HostProber.forDevices.probe(candidates, VpnDevice::ip, ScanProgress.reporter("devices", onProgress)) { device, info ->
            FoundHost(device, info)
        }
    }
}

data class ScanProgress(val message: String, val fraction: Float? = null) {
    companion object {
        fun reporter(unit: String, onProgress: (ScanProgress) -> Unit): (Int, Int) -> Unit = { done, total ->
            onProgress(ScanProgress("Checked $done of $total $unit for Sunshine…", if (total == 0) 1f else done.toFloat() / total))
        }
    }
}

object Vpns {
    const val DEFAULT_KEY = Tailscale.KEY

    fun defaults(): List<Vpn> = load(SettingsReader.Empty)

    fun load(settings: SettingsReader): List<Vpn> = listOf(
        Tailscale.readFrom(settings),
        ZeroTier.readFrom(settings),
        NetBird.readFrom(settings),
        Headscale.readFrom(settings),
        OtherVpn,
    )
}
