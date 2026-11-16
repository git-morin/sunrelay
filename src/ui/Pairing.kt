@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.morin.sunrelay.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import kotlinx.coroutines.CoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.morin.sunrelay.config.RelayConfig
import dev.morin.sunrelay.relay.RelayLog
import dev.morin.sunrelay.sunshine.PairedClient
import dev.morin.sunrelay.sunshine.PairingService
import dev.morin.sunrelay.sunshine.PendingPairing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Stable
class PairingController(private val scope: CoroutineScope) {
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    private val pins = mutableStateMapOf<String, String>()

    fun pin(request: PendingPairing): String = pins[request.id].orEmpty()

    fun updatePin(request: PendingPairing, value: String) {
        if (value.length <= PIN_LENGTH && value.all(Char::isDigit)) pins[request.id] = value
    }

    fun submit(config: RelayConfig, onChange: (RelayConfig) -> Unit, request: PendingPairing, pin: String) {
        busy = true
        message = null
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { PairingService(config.sunshineApi()).pair(request, pin) } }
                .onSuccess { result ->
                    message = if (result.accepted) "✓ Paired. Moonlight should now list the host." else "✗ Wrong PIN, try again."
                    if (result.accepted) pins.remove(request.id)
                    result.newClientUuid?.let { onChange(config.copy(login = config.login.copy(pairedUuid = it))) }
                }
                .onFailure { message = "✗ ${it.message}" }
            busy = false
        }
    }

    fun announce(text: String) {
        message = text
    }
}

@Composable
fun rememberPairingController(): PairingController {
    val scope = rememberCoroutineScope()
    return remember { PairingController(scope) }
}

fun AppState.activePairing(config: RelayConfig): PendingPairing? =
    pendingPairings.firstOrNull()?.takeIf { config.login.isComplete }

fun PendingPairing.requestText(): String {
    val address = address.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
    return "$displayName wants to pair$address"
}

@Composable
fun PairingPrompt(
    config: RelayConfig,
    onChange: (RelayConfig) -> Unit,
    state: AppState,
    pairing: PairingController,
    showMessage: Boolean = true,
) {
    val requests = state.pendingPairings
    requests.forEach { request ->
        if (requests.size > 1) Text(request.requestText(), style = MaterialTheme.typography.titleSmall)
        PinEntry(request, pairing) { pin -> pairing.submit(config, onChange, request, pin) }
    }
    if (showMessage) pairing.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
}

@Composable
fun PairingSection(
    config: RelayConfig,
    onChange: (RelayConfig) -> Unit,
    state: AppState,
    pairing: PairingController,
    openSettings: () -> Unit,
) {
    var showRepair by remember { mutableStateOf(false) }
    val request = state.activePairing(config)

    when {
        !config.login.isComplete -> {
            Text("Add your Sunshine web UI login to type Moonlight's PIN here instead of on the PC.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = openSettings) { Text("Add login") }
        }
        request != null -> {
            Text(
                "${request.requestText()}. Enter the PIN shown in Moonlight.",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            PairingPrompt(config, onChange, state, pairing, showMessage = false)
        }
        else -> {
            Text(
                "When Moonlight asks for a PIN, enter it here. \"Already paired\" but the host never shows up? Re-pair.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = { showRepair = true }, enabled = !pairing.busy) { Text("Re-pair a device") }
        }
    }
    state.pendingError?.let {
        Text("Sunshine: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    pairing.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

    if (showRepair) {
        RepairDialog(config, onDismiss = { showRepair = false }) { name ->
            showRepair = false
            pairing.announce("Removed \"$name\". Now add ${state.hotspotIp ?: "the address shown above"} in Moonlight again; the PIN request will appear above.")
        }
    }
}

@Composable
private fun PinEntry(request: PendingPairing, pairing: PairingController, onSubmit: (String) -> Unit) {
    val pin = pairing.pin(request)
    val busy = pairing.busy
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            pin, { pairing.updatePin(request, it) },
            Modifier.weight(1f),
            singleLine = true,
            label = { Text("PIN shown in Moonlight") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            textStyle = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
        )
        Button(onClick = { onSubmit(pin) }, enabled = pin.length == PIN_LENGTH && !busy, shapes = ButtonDefaults.shapes()) { Text(if (busy) "…" else "Pair") }
    }
}

@Composable
private fun RepairDialog(config: RelayConfig, onDismiss: () -> Unit, onRemoved: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var clients by remember { mutableStateOf<List<PairedClient>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { runCatching { config.sunshineApi().clients() } }
            .onSuccess { clients = it }
            .onFailure { error = it.message }
    }

    fun unpair(client: PairedClient) = scope.launch {
        withContext(Dispatchers.IO) { runCatching { config.sunshineApi().unpair(client.uuid) } }
            .onSuccess { RelayLog.append("unpaired ${client.name}"); onRemoved(client.name) }
            .onFailure { error = it.message }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Which device?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val list = clients
                when {
                    error != null -> Text("✗ $error", color = MaterialTheme.colorScheme.error)
                    list == null -> Text("Loading…")
                    list.isEmpty() -> Text("Nothing is paired. Just add the host in Moonlight.")
                    else -> {
                        Text("Its pairing is removed so Moonlight can pair again.", style = MaterialTheme.typography.bodySmall)
                        list.sortedByDescending { it.uuid == config.login.pairedUuid }.forEach { client ->
                            OutlinedButton(onClick = { unpair(client) }, modifier = Modifier.fillMaxWidth()) {
                                Text(client.name + if (client.uuid == config.login.pairedUuid) "  (paired via SunRelay)" else "")
                            }
                        }
                    }
                }
            }
        },
    )
}

private const val PIN_LENGTH = 4
