@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.morin.sunrelay.ui

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.morin.sunrelay.config.RelayConfig
import dev.morin.sunrelay.relay.RelayLog
import dev.morin.sunrelay.relay.RelayService
import dev.morin.sunrelay.vpn.FoundHost
import dev.morin.sunrelay.vpn.ScanProgress
import dev.morin.sunrelay.vpn.Vpn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Stable
class HostScanState {
    var scanning by mutableStateOf(false)
        private set
    var progress by mutableStateOf<ScanProgress?>(null)
        private set
    var results by mutableStateOf<List<FoundHost>?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    suspend fun scan(context: Context, vpn: Vpn): List<FoundHost>? {
        if (scanning) return null
        scanning = true
        error = null
        results = null
        val outcome = withContext(Dispatchers.IO) { runCatching { vpn.scan(context) { progress = it } } }
        scanning = false
        progress = null
        outcome.onSuccess { results = it }.onFailure { error = it.message ?: it.javaClass.simpleName }
        return outcome.getOrNull()
    }
}

@Composable
private fun ScanIndicator(fraction: Float?) {
    Box(Modifier.size(SCAN_INDICATOR), contentAlignment = Alignment.Center) {
        if (fraction == null) {
            LoadingIndicator(Modifier.size(SCAN_INDICATOR))
        } else {
            val animated by animateFloatAsState(fraction, label = "scan")
            CircularWavyProgressIndicator(progress = { animated }, modifier = Modifier.size(SCAN_INDICATOR - 8.dp))
        }
    }
}

private val SCAN_INDICATOR = 40.dp

@Composable
fun VpnPicker(config: RelayConfig, onChange: (RelayConfig) -> Unit) {
    SegmentedList(
        config.network.vpns.map { vpn ->
            val selected = config.vpn.key == vpn.key
            SegmentedRow(
                headline = vpn.label,
                supporting = vpn.blurb,
                selected = selected,
                onClick = { onChange(config.selecting(vpn)) },
                leading = { RadioButton(selected, onClick = null) },
            )
        },
    )
}

@Composable
fun ScannerPanel(config: RelayConfig, onChange: (RelayConfig) -> Unit, autoStart: Boolean, onPicked: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(config)
    val state = remember { HostScanState() }

    fun pick(host: FoundHost) {
        scope.launch {
            val address = withContext(Dispatchers.IO) { host.preferredAddress() }
            onChange(latest.copy(target = address))
            RelayService.restartIfRunning(context)
            RelayLog.append("target set to $address (${host.info.hostname ?: host.device.ip})")
            onPicked()
        }
    }

    fun scan() {
        if (!latest.canScan) return
        scope.launch { state.scan(context, latest.vpn)?.singleOrNull()?.let(::pick) }
    }

    LaunchedEffect(Unit) { if (autoStart) scan() }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = ::scan, enabled = config.canScan && !state.scanning, shapes = ButtonDefaults.shapes()) {
            Text(if (state.results != null) "Scan again" else config.vpn.scanLabel)
        }
        if (state.scanning) ScanIndicator(state.progress?.fraction)
    }
    state.progress?.let { Text(it.message, style = MaterialTheme.typography.bodySmall) }
    state.error?.let { Text("✗ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    state.results?.let { found ->
        when {
            found.isEmpty() -> Text(
                "No Sunshine host answered. Check Sunshine is running and that this phone can reach port 47989 on it" +
                    config.vpn.accessHint + ".",
                style = MaterialTheme.typography.bodyMedium,
            )
            found.size > 1 -> Text("Found ${found.size} hosts. Pick one:", style = MaterialTheme.typography.bodyMedium)
        }
        if (found.isNotEmpty()) {
            SegmentedList(
                found.map { host ->
                    val selected = host.isTarget(config.target)
                    SegmentedRow(
                        headline = host.device.name,
                        supporting = host.details,
                        selected = selected,
                        onClick = { pick(host) },
                        leading = { RadioButton(selected, onClick = null) },
                    )
                },
            )
        }
    }
}
