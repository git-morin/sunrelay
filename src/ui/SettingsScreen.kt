@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.morin.sunrelay.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import dev.morin.sunrelay.R
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.morin.sunrelay.config.RelayConfig
import dev.morin.sunrelay.config.RelaySettings
import dev.morin.sunrelay.relay.RelayLog
import dev.morin.sunrelay.relay.RelayState
import dev.morin.sunrelay.sunshine.PairedClient
import dev.morin.sunrelay.sunshine.SunshinePins
import dev.morin.sunrelay.ui.theme.Accent
import dev.morin.sunrelay.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(config: RelayConfig, onChange: (RelayConfig) -> Unit, state: AppState, openAbout: () -> Unit) {
    val status by RelayState.status.collectAsState()
    val relayActive = status.isActive

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = openAbout) {
            Icon(painterResource(R.drawable.ic_heart), contentDescription = "About SunRelay and supporting it", tint = HeartRed)
        }
    }
    OutlinedButton(onClick = { onChange(config.copy(onboarded = false)) }, modifier = Modifier.fillMaxWidth()) {
        Text("Run setup again")
    }
    Section("Network", "How this phone reaches the Sunshine host.") { VpnPicker(config, onChange) }
    HostSection(config, onChange, state, relayActive)
    if (config.vpn.hasApi) {
        Section("${config.vpn.label} API") { config.vpn.ApiFields { onChange(config.withVpn(it)) } }
    }
    SunshineLoginSection(config, onChange)
    AppearanceSection(config, onChange)
    AdvancedSection(config, onChange, relayActive)
    if (config.developer.unlocked) DeveloperSection(config, onChange)
    TroubleshootingSection()
    LogSection(exportEnabled = config.developer.unlocked)
    VersionSection(config, onChange)
}

@Composable
private fun HostSection(config: RelayConfig, onChange: (RelayConfig) -> Unit, state: AppState, relayActive: Boolean) =
    Section("Sunshine host") {
        OutlinedTextField(
            config.target, { onChange(config.copy(target = it)) }, Modifier.fillMaxWidth(),
            enabled = !relayActive,
            singleLine = true,
            label = { Text(config.vpn.hostLabel) },
            supportingText = {
                Text(if (relayActive) "Stop the relay to type a new one, or pick one from a scan" else state.serverStatus.orEmpty())
            },
        )
        if (config.canScan) {
            ScannerPanel(config, onChange, autoStart = false, onPicked = state::refresh)
        } else {
            Text("Add your ${config.vpn.label} API access below to scan for hosts.", style = MaterialTheme.typography.bodySmall)
        }
    }

@Composable
private fun SunshineLoginSection(config: RelayConfig, onChange: (RelayConfig) -> Unit) {
    val scope = rememberCoroutineScope()
    var clients by remember { mutableStateOf<List<PairedClient>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var pinned by remember(config.target, message) { mutableStateOf(config.hasTarget && SunshinePins.has(config.target)) }

    fun loadClients() {
        message = "…"
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { config.sunshineApi().clients() } }
                .onSuccess { clients = it; message = "✓ Logged in" }
                .onFailure { clients = null; message = "✗ ${it.message}" }
        }
    }

    fun unpair(client: PairedClient) = scope.launch {
        withContext(Dispatchers.IO) { runCatching { config.sunshineApi().unpair(client.uuid) } }
            .onSuccess { RelayLog.append("unpaired ${client.name}"); loadClients() }
            .onFailure { message = "✗ ${it.message}" }
    }

    LaunchedEffect(Unit) { if (config.canUseSunshineApi) loadClients() }

    Section("Sunshine login", "The web UI login. Lets you pair and unpair devices from this phone.") {
        CredentialsNotice()
        SunshineLoginFields(config, onChange)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = ::loadClients, enabled = config.canUseSunshineApi) { Text("Test") }
            Spacer(Modifier.width(12.dp))
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
        if (pinned) {
            TextButton(onClick = {
                SunshinePins.forget(config.target)
                pinned = false
                message = "Certificate forgotten. The next connection trusts Sunshine's current one."
            }) { Text("Forget certificate") }
        }
        clients?.let { list ->
            Text("Paired devices", style = MaterialTheme.typography.titleSmall)
            if (list.isEmpty()) Text("None", style = MaterialTheme.typography.bodySmall)
            else SegmentedList(
                list.map { client ->
                    SegmentedRow(
                        headline = client.name,
                        supporting = "Paired via SunRelay".takeIf { client.uuid == config.login.pairedUuid },
                        trailing = { TextButton(onClick = { unpair(client) }) { Text("Unpair") } },
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSection(config: RelayConfig, onChange: (RelayConfig) -> Unit) {
    val appearance = config.appearance
    Section("Appearance") {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ThemeMode.entries.forEachIndexed { i, mode ->
                SegmentedButton(
                    selected = appearance.themeMode == mode,
                    onClick = { onChange(config.copy(appearance = appearance.copy(themeMode = mode))) },
                    shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                ) { Text(mode.label) }
            }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Accent", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Accent.entries.forEach { accent ->
                        AccentSwatch(accent, appearance.accent == accent) {
                            onChange(config.copy(appearance = appearance.copy(accent = accent)))
                        }
                    }
                }
                Text(appearance.accent.description, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AccentSwatch(accent: Accent, selected: Boolean, onSelect: () -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val color = accent.seed?.let { Color(it) } ?: dynamicLightColorScheme(context).primary
    val shape = (if (selected) MaterialShapes.Cookie9Sided else MaterialShapes.Circle).toShape()
    Box(
        Modifier
            .size(SWATCH_SIZE)
            .clip(shape)
            .background(color)
            .border(if (selected) 3.dp else 1.dp, if (selected) cs.onSurface else cs.outlineVariant, shape)
            .clickable(role = Role.RadioButton, onClick = onSelect)
            .semantics {
                contentDescription = accent.label
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Text("✓", fontWeight = FontWeight.Bold, color = if (color.luminance() > 0.5f) Color.Black else Color.White)
    }
}

@Composable
private fun AdvancedSection(config: RelayConfig, onChange: (RelayConfig) -> Unit, relayActive: Boolean) {
    val relay = config.relay
    fun update(settings: RelaySettings) = onChange(config.copy(relay = settings))

    Section("Advanced", "Ports, UDP timeout and mDNS") {
        if (relayActive) Text("Stop the relay to change these.", style = MaterialTheme.typography.bodySmall)
        SegmentedList(
            listOf(
                SegmentedRow(
                    headline = "Advertise to connected devices (mDNS)",
                    supporting = "Lets Moonlight's host search find the phone",
                    onClick = { if (!relayActive) update(relay.copy(mdns = !relay.mdns)) },
                    trailing = { Switch(relay.mdns, { update(relay.copy(mdns = it)) }, enabled = !relayActive) },
                ),
            ),
        )
        OutlinedTextField(
            relay.tcpPorts, { update(relay.copy(tcpPorts = it)) }, Modifier.fillMaxWidth(),
            enabled = !relayActive, singleLine = true, label = { Text("TCP ports") },
        )
        OutlinedTextField(
            relay.udpPorts, { update(relay.copy(udpPorts = it)) }, Modifier.fillMaxWidth(),
            enabled = !relayActive, singleLine = true, label = { Text("UDP ports") },
        )
        OutlinedTextField(
            relay.udpIdleSec.toString(), { v -> v.toIntOrNull()?.let { update(relay.withUdpIdle(it)) } }, Modifier.fillMaxWidth(),
            enabled = !relayActive, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            label = { Text("UDP idle timeout (s)") },
        )
        TextButton(onClick = { update(RelaySettings()) }, enabled = !relayActive) { Text("Reset to defaults") }
    }
}

@Composable
private fun TroubleshootingSection() = Section("Troubleshooting") {
    SegmentedList(
        listOf(
            "Host shows \"Wake up\" or offline" to "Moonlight cached an old address. Remove it and add the address shown on Home again; use Re-pair on Home if it says \"Already paired\".",
            "Pairing or connecting hangs" to "Sunshine may be stuck on a dead connection. Restart Sunshine.",
            "Nothing reaches the host" to "Make sure SunRelay isn't excluded from your VPN (split tunneling).",
        ).map { (problem, fix) -> SegmentedRow(headline = problem, supporting = fix) },
    )
}

@Composable
private fun LogSection(exportEnabled: Boolean) {
    val context = LocalContext.current
    val log by RelayLog.lines.collectAsState()
    val lines = log.takeLast(LOG_LINES).reversed()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val saved = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(RelayLog.asText().toByteArray()) }
        }.isSuccess
        Toast.makeText(context, if (saved) "Log exported" else "Couldn't export the log", Toast.LENGTH_SHORT).show()
    }
    Section("Log") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = RelayLog::clear, enabled = log.isNotEmpty()) { Text("Clear") }
            if (exportEnabled) {
                OutlinedButton(onClick = { exportLauncher.launch(logFileName()) }, enabled = log.isNotEmpty()) { Text("Export") }
            }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (lines.isEmpty()) Text("—", style = MaterialTheme.typography.bodySmall)
                lines.forEach { Text(it, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace) }
            }
        }
    }
}

private fun logFileName(): String =
    "sunrelay-log-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"

private val SWATCH_SIZE = 48.dp
private const val LOG_LINES = 60
