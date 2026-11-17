package dev.morin.sunrelay.relay

import android.util.Log
import dev.morin.sunrelay.net.TetherAddress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RelayStatus(
    val running: Boolean = false,
    val starting: Boolean = false,
    val target: String = "",
    val targetIp: String = "",
    val hotspot: List<TetherAddress> = emptyList(),
    val tcpActive: Long = 0,
    val udpActive: Long = 0,
    val clients: Set<String> = emptySet(),
    val upBps: Long = 0,
    val downBps: Long = 0,
    val error: String? = null,
) {
    val isActive: Boolean get() = running || starting
    val hasClients: Boolean get() = running && clients.isNotEmpty()
    val hotspotIp: String? get() = hotspot.firstOrNull()?.ip
    val traffic: String get() = "↓ ${Bitrate.format(downBps)}  ↑ ${Bitrate.format(upBps)}"

    fun notificationText(): String {
        val ip = hotspotIp ?: "no hotspot or tethering"
        return if (tcpActive + udpActive == 0L) "$ip → $target · idle"
        else "$ip → $target · ${clients.size} client(s) · $traffic"
    }
}

object RelayState {
    private val mutableStatus = MutableStateFlow(RelayStatus())
    val status: StateFlow<RelayStatus> = mutableStatus.asStateFlow()

    internal fun update(status: RelayStatus) {
        mutableStatus.value = status
    }
}

object RelayLog {
    private const val MAX_LINES = 200
    private val mutableLines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = mutableLines.asStateFlow()

    fun append(line: String) {
        Log.i("SunRelay", line)
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        mutableLines.update { (it + "$time $line").takeLast(MAX_LINES) }
    }

    fun clear() {
        mutableLines.value = emptyList()
    }

    fun asText(): String = mutableLines.value.joinToString("\n", postfix = "\n")
}

object Bitrate {
    fun format(bps: Long): String = when {
        bps >= 1_000_000 -> "%.1f Mb/s".format(bps / 1e6)
        bps >= 1_000 -> "%.0f kb/s".format(bps / 1e3)
        else -> "$bps b/s"
    }
}
