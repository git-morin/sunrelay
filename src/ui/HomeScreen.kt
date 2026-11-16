@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.morin.sunrelay.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.unit.sp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.morin.sunrelay.R
import dev.morin.sunrelay.vpn.Vpn
import dev.morin.sunrelay.config.RelayConfig
import dev.morin.sunrelay.net.SystemScreens
import dev.morin.sunrelay.relay.RelayService
import dev.morin.sunrelay.relay.RelayState
import dev.morin.sunrelay.relay.RelayStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class HomeStep { Vpn, Sunshine, Hotspot, Relay, Moonlight }

private class HomeProgress(state: AppState, status: RelayStatus) {
    private val done = mapOf(
        HomeStep.Vpn to state.vpnConnected,
        HomeStep.Sunshine to state.serverReachable,
        HomeStep.Hotspot to state.hotspot.isNotEmpty(),
        HomeStep.Relay to status.running,
        HomeStep.Moonlight to status.hasClients,
    )
    private val current = HomeStep.entries.firstOrNull { done[it] != true }

    fun isDone(step: HomeStep) = done[step] == true
    fun isCurrent(step: HomeStep) = step == current
}

private enum class RelayPhase { Stopped, Starting, Running, Failed, Pairing }

private val RelayStatus.phase: RelayPhase
    get() = when {
        running -> RelayPhase.Running
        starting -> RelayPhase.Starting
        error != null -> RelayPhase.Failed
        else -> RelayPhase.Stopped
    }

@Composable
fun HomeScreen(config: RelayConfig, onChange: (RelayConfig) -> Unit, state: AppState, openSettings: () -> Unit) {
    val status by RelayState.status.collectAsState()
    val progress = HomeProgress(state, status)
    val pairing = rememberPairingController()

    RelayHero(config, onChange, state, status, pairing)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Text("Setup", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = state::refresh) { Text("Refresh") }
    }
    AutoRefreshBar(config, onChange, state)
    Column {
        VpnStep(config, state, progress, openSettings)
        SunshineStep(config, onChange, state, progress, openSettings)
        HotspotStep(state, progress)
        RelayStep(state, status, progress)
        MoonlightStep(config, onChange, state, status, progress, pairing, openSettings)
    }
}

@Composable
private fun AutoRefreshBar(config: RelayConfig, onChange: (RelayConfig) -> Unit, state: AppState) {
    val active = config.autoCheck && state.vpnConnected
    val status = when {
        !config.autoCheck -> "Paused"
        !state.vpnConnected -> "Waits for the VPN (step 1)"
        else -> "Every ${AppState.POLL_MS / 1000} s"
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .toggleable(value = config.autoCheck, role = Role.Switch) { onChange(config.copy(autoCheck = it)) }
                .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(TIMER_SIZE), contentAlignment = Alignment.Center) {
                if (active || state.checkingServer) {
                    PollTimer(state)
                } else {
                    CircularWavyProgressIndicator(progress = { 0f }, modifier = Modifier.size(TIMER_SIZE - 8.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text("Auto refresh", style = MaterialTheme.typography.titleSmall)
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = config.autoCheck, onCheckedChange = null)
        }
    }
}

@Composable
private fun PollTimer(state: AppState) {
    val cycle = remember { Animatable(0f) }
    LaunchedEffect(state.pollCount) {
        cycle.snapTo(0f)
        cycle.animateTo(1f, tween(AppState.POLL_MS.toInt(), easing = LinearEasing))
    }
    val description = if (state.checkingServer) "Checking Sunshine" else "Next check in under 2 seconds"
    Box(
        Modifier.size(TIMER_SIZE).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (state.checkingServer) {
            LoadingIndicator(Modifier.size(TIMER_SIZE))
        } else {
            CircularWavyProgressIndicator(progress = { cycle.value }, modifier = Modifier.size(TIMER_SIZE - 8.dp))
        }
    }
}

@Composable
private fun RelayHero(
    config: RelayConfig,
    onChange: (RelayConfig) -> Unit,
    state: AppState,
    status: RelayStatus,
    pairing: PairingController,
) {
    val cs = MaterialTheme.colorScheme
    val request = state.activePairing(config)
    val phase = if (request != null) RelayPhase.Pairing else status.phase
    val container by animateColorAsState(
        when (phase) {
            RelayPhase.Running -> cs.primaryContainer
            RelayPhase.Pairing -> cs.tertiaryContainer
            RelayPhase.Starting -> cs.secondaryContainer
            RelayPhase.Failed -> cs.errorContainer
            RelayPhase.Stopped -> cs.surfaceContainerHigh
        },
        label = "hero",
    )
    val onContainer = when (phase) {
        RelayPhase.Running -> cs.onPrimaryContainer
        RelayPhase.Pairing -> cs.onTertiaryContainer
        RelayPhase.Starting -> cs.onSecondaryContainer
        RelayPhase.Failed -> cs.onErrorContainer
        RelayPhase.Stopped -> cs.onSurface
    }

    Surface(
        color = container,
        contentColor = onContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                HeroBadge(phase, config.vpn)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        when (phase) {
                            RelayPhase.Running -> "SunRelay running"
                            RelayPhase.Pairing -> "Pairing request"
                            RelayPhase.Starting -> "SunRelay starting…"
                            RelayPhase.Failed -> "SunRelay failed"
                            RelayPhase.Stopped -> "SunRelay stopped"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    val subtitle = when {
                        request != null -> request.requestText()
                        status.hasClients -> "${status.clients.size} client(s) · ${status.traffic}"
                        status.running -> "→ ${status.target} · waiting for Moonlight"
                        else -> status.error
                    }
                    subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            if (request != null) {
                PairingPrompt(config, onChange, state, pairing)
            } else {
                state.hotspot.firstOrNull()?.let { HeroAddress(it.ip, it.kind.label) }
                RelayToggleButton(state, status)
            }
        }
    }
}

@Composable
private fun RelayToggleButton(state: AppState, status: RelayStatus, compact: Boolean = false) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val startLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        state.refreshPermissions()
        RelayService.start(context)
    }
    Button(
        onClick = {
            when {
                status.isActive -> RelayService.stop(context)
                state.permissions.missingRuntime.isNotEmpty() -> startLauncher.launch(state.permissions.missingRuntime)
                else -> RelayService.start(context)
            }
        },
        shapes = ButtonDefaults.shapes(),
        colors = if (status.isActive) {
            ButtonDefaults.buttonColors(containerColor = cs.error, contentColor = cs.onError)
        } else {
            ButtonDefaults.buttonColors()
        },
        contentPadding = if (compact) ButtonDefaults.ContentPadding else ButtonDefaults.MediumContentPadding,
        modifier = if (compact) Modifier else Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
    ) {
        Icon(painterResource(R.drawable.ic_relay), contentDescription = null, modifier = Modifier.size(if (compact) 18.dp else 24.dp))
        Text(
            if (status.isActive) "Stop relay" else "Start relay",
            style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun HeroBadge(phase: RelayPhase, vpn: Vpn) {
    val cs = MaterialTheme.colorScheme
    AnimatedContent(
        targetState = phase,
        transitionSpec = { (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
        label = "heroBadge",
    ) { p ->
        when (p) {
            RelayPhase.Starting -> LoadingIndicator(Modifier.size(HERO_BADGE), color = cs.secondary)
            RelayPhase.Running -> ShapedBadge(MaterialShapes.Sunny, cs.surface, HERO_BADGE, rotating = true) {
                AppVpnLogo(vpn, HERO_LOGO)
            }
            RelayPhase.Pairing -> ShapedBadge(MaterialShapes.Cookie9Sided, cs.surface, HERO_BADGE, rotating = true) {
                AppVpnLogo(vpn, HERO_LOGO)
            }
            RelayPhase.Failed -> ShapedBadge(MaterialShapes.SoftBurst, cs.error, HERO_BADGE) {
                Text("!", color = cs.onError, style = MaterialTheme.typography.headlineMedium)
            }
            RelayPhase.Stopped -> MoonBadge(cs.surfaceContainerHighest, HERO_BADGE) {
                AppVpnLogo(vpn, HERO_LOGO, greyscale = true)
            }
        }
    }
}

@Composable
private fun HeroAddress(ip: String, via: String) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Add this host in Moonlight · via $via", style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                ip,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                style = MaterialTheme.typography.displaySmall.copy(fontFamily = FontFamily.Monospace, color = LocalContentColor.current),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = MaterialTheme.typography.displaySmall.fontSize),
            )
            FilledTonalButton(onClick = { copyAddress(context, ip) }) { Text("Copy") }
        }
    }
}

private fun copyAddress(context: android.content.Context, ip: String) =
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("SunRelay address", ip))

@Composable
private fun Step(
    step: HomeStep,
    title: String,
    progress: HomeProgress,
    summary: String?,
    alwaysExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) = StepperItem(
    number = step.ordinal + 1,
    title = title,
    done = progress.isDone(step),
    current = progress.isCurrent(step),
    summary = summary,
    isLast = step == HomeStep.entries.last(),
    alwaysExpanded = alwaysExpanded,
    content = content,
)

@Composable
private fun VpnStep(config: RelayConfig, state: AppState, progress: HomeProgress, openSettings: () -> Unit) {
    val scope = rememberCoroutineScope()
    val vpn = config.vpn
    Step(HomeStep.Vpn, "Connect ${vpn.connectName}", progress, summary = "Connected") {
        Text(
            if (vpn.hasApi) "SunRelay reaches your Sunshine host through ${vpn.label}. This step ticks off by itself."
            else "Turn on the VPN that reaches your Sunshine host. This step ticks off by itself.",
            style = MaterialTheme.typography.bodyMedium,
        )
        vpn.ConnectActions(state.vpnConnected) { scope.launch { delay(RECHECK_DELAY_MS); state.refresh() } }
        TextButton(onClick = openSettings) { Text("Use a different network setup") }
    }
}

@Composable
private fun SunshineStep(
    config: RelayConfig,
    onChange: (RelayConfig) -> Unit,
    state: AppState,
    progress: HomeProgress,
    openSettings: () -> Unit,
) {
    Step(HomeStep.Sunshine, "Reach Sunshine", progress, summary = state.serverStatus) {
        Text(
            when {
                !state.vpnConnected -> "Waiting for the VPN (step 1)."
                !config.hasTarget -> "No Sunshine host set yet. Scan for it or enter it in Settings."
                else -> "Can't reach Sunshine at ${config.target}: ${state.serverStatus ?: "…"}"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = state::refresh) { Text("Retry") }
            OutlinedButton(onClick = openSettings) { Text("Change host") }
        }
        if (state.vpnConnected) {
            if (config.canScan) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("Or find it automatically", style = MaterialTheme.typography.labelLarge)
                ScannerPanel(config, onChange, autoStart = false, onPicked = state::refresh)
            } else {
                TextButton(onClick = openSettings) { Text("Set up ${config.vpn.label} scanning") }
            }
        }
    }
}

@Composable
private fun HotspotStep(state: AppState, progress: HomeProgress) {
    val context = LocalContext.current
    Step(HomeStep.Hotspot, "Share this phone's connection", progress, summary = state.hotspot.joinToString { "${it.kind.label} · ${it.ip}" }) {
        Text(
            "Turn on the Wi-Fi hotspot, USB tethering or Bluetooth tethering, then connect your Moonlight device to it. " +
                "This step ticks off by itself.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = { SystemScreens.hotspotSettings(context) }) { Text("Open hotspot & tethering") }
    }
}

@Composable
private fun RelayStep(state: AppState, status: RelayStatus, progress: HomeProgress) {
    val context = LocalContext.current
    val allowLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        state.refreshPermissions()
        if (!state.permissions.unrestrictedBattery) SystemScreens.batteryOptimization(context)
    }
    val permissions = state.permissions
    Step(
        HomeStep.Relay, "Start SunRelay", progress,
        alwaysExpanded = permissions.missingLabels.isNotEmpty() || status.running,
        summary = if (status.running) "Relaying to ${status.target}" else null,
    ) {
        if (permissions.missingLabels.isNotEmpty()) {
            Text(
                "Needs: ${permissions.missingLabels.joinToString()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            OutlinedButton(onClick = {
                when {
                    permissions.missingRuntime.isNotEmpty() -> allowLauncher.launch(permissions.missingRuntime)
                    !permissions.unrestrictedBattery -> SystemScreens.batteryOptimization(context)
                }
            }) { Text("Allow all") }
        }
        Text(
            if (status.running) "SunRelay is on." else "SunRelay needs to be on.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RelayToggleButton(state, status, compact = true)
            if (status.running) OutlinedButton(onClick = { RelayService.restartIfRunning(context) }) { Text("Restart relay") }
        }
    }
}

@Composable
private fun MoonlightStep(
    config: RelayConfig,
    onChange: (RelayConfig) -> Unit,
    state: AppState,
    status: RelayStatus,
    progress: HomeProgress,
    pairing: PairingController,
    openSettings: () -> Unit,
) {
    Step(
        HomeStep.Moonlight, "Connect from Moonlight", progress, alwaysExpanded = true,
        summary = if (progress.isDone(HomeStep.Moonlight)) "${status.clients.size} connected" else null,
    ) {
        val address = state.hotspotIp
        if (address != null) {
            Text(
                buildAnnotatedString {
                    append("Add ")
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)) { append(address) }
                    append(" as a host in Moonlight.")
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text(
                "The address to enter in Moonlight shows up top once the hotspot or tethering is on.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (status.hasClients) Text(status.clients.joinToString(), style = MaterialTheme.typography.bodyMedium)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        PairingSection(config, onChange, state, pairing, openSettings)
    }
}

private val HERO_BADGE = 88.dp
private val HERO_LOGO = 88.dp
private val TIMER_SIZE = 32.dp
private const val RECHECK_DELAY_MS = 1500L
