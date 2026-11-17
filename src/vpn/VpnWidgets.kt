package dev.morin.sunrelay.vpn

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import dev.morin.sunrelay.net.SystemScreens

@Composable
internal fun VpnHint(text: String) = Text(text, style = MaterialTheme.typography.bodySmall)

@Composable
internal fun VpnField(value: String, label: String, secret: Boolean = false, onChange: (String) -> Unit) =
    OutlinedTextField(
        value, onChange, Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
    )

@Composable
internal fun OpenUrlButton(text: String, url: String) {
    val context = LocalContext.current
    OutlinedButton(onClick = { SystemScreens.url(context, url) }) { Text(text) }
}

@Composable
internal fun OpenVpnAppButton(packageName: String?, appName: String, onAction: () -> Unit) {
    val context = LocalContext.current
    val launch = remember(packageName) { packageName?.let { context.packageManager.getLaunchIntentForPackage(it) } }
    if (launch != null) {
        OutlinedButton(onClick = { SystemScreens.open(context, launch); onAction() }) { Text("Open $appName") }
    } else {
        OutlinedButton(onClick = { SystemScreens.vpnSettings(context); onAction() }) { Text("Open VPN settings") }
    }
}
