@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.morin.sunrelay.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.morin.sunrelay.config.DeveloperSettings
import dev.morin.sunrelay.config.RelayConfig
import dev.morin.sunrelay.net.SystemScreens
import dev.morin.sunrelay.update.UpdateChecker
import dev.morin.sunrelay.update.UpdateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun VersionSection(config: RelayConfig, onChange: (RelayConfig) -> Unit) {
    val context = LocalContext.current
    val version = remember { UpdateChecker.installedVersion(context) }
    var taps by remember { mutableIntStateOf(0) }
    var checking by remember { mutableStateOf(false) }

    fun onVersionTapped() {
        if (config.developer.unlocked) {
            toast(context, "Developer options are already on")
            return
        }
        taps++
        val remaining = UNLOCK_TAPS - taps
        when {
            remaining <= 0 -> {
                taps = 0
                onChange(config.copy(developer = config.developer.copy(unlocked = true)))
                toast(context, "Developer options are on")
            }
            remaining <= COUNTDOWN_FROM -> toast(context, "$remaining ${if (remaining == 1) "tap" else "taps"} away from developer options")
        }
    }

    Surface(
        onClick = ::onVersionTapped,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("SunRelay $version", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (config.developer.includePrereleases) "Pre-release updates" else "Stable updates",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = { checking = true }) { Text("Check for updates") }
        }
    }

    if (checking) UpdateDialog(version, config.developer.includePrereleases) { checking = false }
}

@Composable
fun DeveloperSection(config: RelayConfig, onChange: (RelayConfig) -> Unit) {
    val developer = config.developer
    fun update(settings: DeveloperSettings) = onChange(config.copy(developer = settings))

    val options = listOf(
        SegmentedRow(
            headline = "Pre-release updates",
            supporting = "Check for dev builds as well as stable releases",
            onClick = { update(developer.copy(prereleases = !developer.prereleases)) },
            trailing = { Switch(developer.prereleases, { update(developer.copy(prereleases = it)) }) },
        ),
    )

    Section("Developer options", "Options for testing and troubleshooting SunRelay.") {
        SegmentedList(options)
        SegmentedList(
            listOf(
                SegmentedRow(
                    headline = "Turn off developer options",
                    supporting = "Hides this section and resets every option above",
                    onClick = { update(DeveloperSettings()) },
                ),
            ),
        )
    }
}

private sealed interface UpdateState {
    data object Checking : UpdateState
    data class Done(val result: UpdateResult) : UpdateState
    data class Failed(val message: String) : UpdateState
}

@Composable
private fun UpdateDialog(currentVersion: String, includePrereleases: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Checking) }
    LaunchedEffect(Unit) {
        state = withContext(Dispatchers.IO) {
            runCatching { UpdateChecker().check(currentVersion, includePrereleases) }
                .fold({ UpdateState.Done(it) }, { UpdateState.Failed(it.message ?: "Update check failed") })
        }
    }
    val available = (state as? UpdateState.Done)?.result as? UpdateResult.Available

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    state is UpdateState.Checking -> "Checking for updates"
                    available != null -> "Update available"
                    state is UpdateState.Failed -> "Couldn't check"
                    else -> "You're up to date"
                },
            )
        },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (val s = state) {
                    UpdateState.Checking -> LoadingIndicator(Modifier.size(48.dp))
                    is UpdateState.Failed -> Text(s.message, style = MaterialTheme.typography.bodyMedium)
                    is UpdateState.Done -> Text(
                        when (val result = s.result) {
                            is UpdateResult.Available ->
                                "SunRelay ${result.version}${if (result.prerelease) " (pre-release)" else ""} is out. You have $currentVersion."
                            is UpdateResult.UpToDate -> "SunRelay ${result.current} is the latest" +
                                if (includePrereleases) " build, including pre-releases." else " release."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            if (available != null) {
                TextButton(onClick = { SystemScreens.url(context, available.url); onDismiss() }) { Text("Open release") }
            } else {
                TextButton(onClick = onDismiss) { Text("OK") }
            }
        },
        dismissButton = { if (available != null) TextButton(onClick = onDismiss) { Text("Later") } },
    )
}

private fun toast(context: Context, message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

private const val UNLOCK_TAPS = 7
private const val COUNTDOWN_FROM = 3
