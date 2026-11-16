@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.morin.sunrelay.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import dev.morin.sunrelay.R
import dev.morin.sunrelay.config.RelayConfig
import dev.morin.sunrelay.net.SystemScreens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class OnboardingStep(val polygon: RoundedPolygon, val icon: Int) {
    Welcome(MaterialShapes.Sunny, R.drawable.ic_relay),
    Network(MaterialShapes.Cookie9Sided, R.drawable.ic_settings),
    Host(MaterialShapes.Clover4Leaf, R.drawable.ic_home),
    Login(MaterialShapes.Puffy, R.drawable.ic_settings),
}

@Composable
fun Onboarding(config: RelayConfig, onChange: (RelayConfig) -> Unit, state: AppState) {
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    val steps = OnboardingStep.entries
    val isLast = stepIndex == steps.lastIndex
    val progress by animateFloatAsState((stepIndex + 1f) / steps.size, label = "progress")
    fun finish() = onChange(config.copy(onboarded = true))

    Scaffold(
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (stepIndex > 0) TextButton(onClick = { stepIndex-- }) { Text("Back") }
                    else TextButton(onClick = ::finish) { Text("Skip setup") }
                    Button(onClick = { if (isLast) finish() else stepIndex++ }, shapes = ButtonDefaults.shapes()) {
                        Text(if (isLast) "Done" else "Next")
                    }
                }
            }
        },
    ) { padding ->
        ScrollingPage(padding) {
            LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Text("Step ${stepIndex + 1} of ${steps.size}", style = MaterialTheme.typography.labelLarge)
            AnimatedContent(
                targetState = steps[stepIndex],
                transitionSpec = {
                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInHorizontally { it / 4 * direction } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 4 * direction } + fadeOut())
                },
                label = "onboarding",
            ) { step ->
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    StepIllustration(step)
                    when (step) {
                        OnboardingStep.Welcome -> WelcomeStep(state)
                        OnboardingStep.Network -> NetworkStep(config, onChange, state)
                        OnboardingStep.Host -> HostStep(config, onChange, state)
                        OnboardingStep.Login -> LoginStep(config, onChange)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepIllustration(step: OnboardingStep) {
    val cs = MaterialTheme.colorScheme
    ShapedBadge(step.polygon, cs.primaryContainer, ILLUSTRATION_SIZE, rotating = step == OnboardingStep.Welcome) {
        Icon(painterResource(step.icon), contentDescription = null, tint = cs.onPrimaryContainer, modifier = Modifier.size(40.dp))
    }
}

@Composable
private fun StepTitle(text: String) = Text(text, style = MaterialTheme.typography.headlineLarge)

@Composable
private fun WelcomeStep(state: AppState) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        state.refreshPermissions()
    }
    StepTitle("Welcome to SunRelay")
    Text(
        "SunRelay lets a device on this phone's hotspot (like a Switch running Moonlight) stream from a " +
            "Sunshine host on your tailnet or VPN. A few permissions keep it working with the screen off:",
        style = MaterialTheme.typography.bodyLarge,
    )
    StatusRow(state.permissions.runtimeGranted, "Local network access & notifications", "Allow") {
        launcher.launch(AppPermissions.RUNTIME)
    }
    StatusRow(state.permissions.unrestrictedBattery, "Unrestricted battery", "Allow") {
        SystemScreens.batteryOptimization(context)
    }
}

@Composable
private fun NetworkStep(config: RelayConfig, onChange: (RelayConfig) -> Unit, state: AppState) {
    StepTitle("How do you reach Sunshine?")
    VpnPicker(config, onChange)
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    StatusRow(state.vpnConnected, "${config.vpn.connectName.replaceFirstChar { it.uppercase() }} connected")
    if (!state.vpnConnected) config.vpn.ConnectActions(state.vpnConnected, state::refresh)
    Text("If your VPN app has split tunneling, don't exclude SunRelay.", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun HostStep(config: RelayConfig, onChange: (RelayConfig) -> Unit, state: AppState) {
    StepTitle("Find your Sunshine host")
    if (config.vpn.hasApi) config.vpn.ApiFields { onChange(config.withVpn(it)) }
    else Text("SunRelay will look for Sunshine on your VPN's network.", style = MaterialTheme.typography.bodyLarge)
    ScannerPanel(config, onChange, autoStart = true, onPicked = state::refresh)
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Text("Or enter it yourself", style = MaterialTheme.typography.titleSmall)
    OutlinedTextField(
        config.target, { onChange(config.copy(target = it)) }, Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(config.vpn.hostLabel) },
        supportingText = { Text(state.serverStatus.orEmpty()) },
    )
}

@Composable
private fun LoginStep(config: RelayConfig, onChange: (RelayConfig) -> Unit) {
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    StepTitle("Sunshine login (optional)")
    Text(
        "With your Sunshine web UI login, you can pair Moonlight by typing the PIN on this phone, " +
            "and fix \"Already paired\" problems without going to the PC.",
        style = MaterialTheme.typography.bodyLarge,
    )
    CredentialsNotice()
    SunshineLoginFields(config, onChange)
    OutlinedButton(
        onClick = {
            message = "…"
            scope.launch {
                message = withContext(Dispatchers.IO) { runCatching { config.sunshineApi().clients() } }
                    .fold({ "✓ Logged in, ${it.size} paired device(s)" }, { "✗ ${it.message}" })
            }
        },
        enabled = config.canUseSunshineApi,
    ) { Text("Test") }
    message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Color.Unspecified) }
}

private val ILLUSTRATION_SIZE = 112.dp
