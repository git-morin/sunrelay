package dev.morin.sunrelay.ui

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.morin.sunrelay.config.RelayConfig
import dev.morin.sunrelay.net.NetworkMonitor
import dev.morin.sunrelay.net.TetherAddress
import dev.morin.sunrelay.sunshine.PendingPairing
import dev.morin.sunrelay.sunshine.SunshineProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Stable
class AppState(private val context: Context) {
    private val network = NetworkMonitor(context)

    var hotspot by mutableStateOf(emptyList<TetherAddress>())
        private set
    var vpnConnected by mutableStateOf(false)
        private set
    var permissions by mutableStateOf(PermissionStatus())
        private set
    var serverStatus by mutableStateOf<String?>(null)
        private set
    var serverReachable by mutableStateOf(false)
        private set
    var pendingPairings by mutableStateOf(emptyList<PendingPairing>())
        private set
    var pendingError by mutableStateOf<String?>(null)
        private set
    var refreshCount by mutableIntStateOf(0)
        private set
    var pollCount by mutableIntStateOf(0)
        private set

    val checkingServer: Boolean get() = serverStatus == CHECKING

    val hotspotIp: String? get() = hotspot.firstOrNull()?.ip

    fun refresh() {
        refreshCount++
    }

    fun refreshPermissions() {
        permissions = AppPermissions.status(context)
    }

    suspend fun watchDevice(continuous: Boolean) {
        do {
            hotspot = withContext(Dispatchers.IO) { network.hotspotAddresses() }
            vpnConnected = network.isVpnActive()
            refreshPermissions()
            pollCount++
            if (continuous) delay(POLL_MS)
        } while (continuous)
    }

    private var lastCheckedTarget: String? = null

    suspend fun checkServer(config: RelayConfig) {
        if (lastCheckedTarget != null && config.target != lastCheckedTarget) delay(TYPING_DEBOUNCE_MS)
        lastCheckedTarget = config.target
        if (!config.hasTarget) {
            serverReachable = false
            serverStatus = "no host set"
            return
        }
        serverStatus = CHECKING
        val result = withContext(Dispatchers.IO) { SunshineProbe.fetch(config.target.trim()) }
        serverReachable = result.isSuccess
        serverStatus = result.fold({ it.summary }, { "unreachable (${it.message ?: it.javaClass.simpleName})" })
    }

    suspend fun watchPairingRequests(config: RelayConfig) {
        pendingPairings = emptyList()
        pendingError = null
        if (!config.canUseSunshineApi) return
        delay(TYPING_DEBOUNCE_MS)
        val api = config.sunshineApi()
        while (true) {
            val result = withContext(Dispatchers.IO) { runCatching { api.pending() } }
            pendingPairings = result.getOrDefault(emptyList())
            pendingError = result.exceptionOrNull()?.message
            delay(POLL_MS)
        }
    }

    companion object {
        const val POLL_MS = 2000L
        private const val TYPING_DEBOUNCE_MS = 800L
        private const val CHECKING = "checking…"
    }
}
