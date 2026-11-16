@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.morin.sunrelay.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.morin.sunrelay.R
import dev.morin.sunrelay.config.ConfigRepository
import dev.morin.sunrelay.config.RelayConfig
import dev.morin.sunrelay.ui.theme.SunTheme

private enum class Tab(val label: String, val icon: Int) {
    Home("Home", R.drawable.ic_home),
    Settings("Settings", R.drawable.ic_settings),
}

@Composable
fun App() {
    val context = LocalContext.current
    val repository = remember { ConfigRepository(context) }
    var config by remember { mutableStateOf(repository.load()) }
    SunTheme(config.appearance.accent, config.appearance.themeMode) {
        AppContent(config) {
            config = it
            repository.save(it)
        }
    }
}

@Composable
private fun AppContent(config: RelayConfig, onChange: (RelayConfig) -> Unit) {
    val context = LocalContext.current
    val state = remember { AppState(context) }
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var showAbout by rememberSaveable { mutableStateOf(false) }

    RefreshOnResume(state)
    LaunchedEffect(state.refreshCount, config.autoCheck, state.vpnConnected) {
        state.watchDevice(config.autoCheck && state.vpnConnected)
    }
    LaunchedEffect(state.refreshCount, config.target, state.vpnConnected) { state.checkServer(config) }
    LaunchedEffect(config.target, config.login) { state.watchPairingRequests(config) }

    if (!config.onboarded) {
        LaunchedEffect(Unit) { tab = Tab.Home }
        Onboarding(config, onChange, state)
        return
    }

    if (showAbout) {
        BackHandler { showAbout = false }
        AboutScreen(onBack = { showAbout = false })
        return
    }

    Scaffold(
        bottomBar = {
            ShortNavigationBar {
                Tab.entries.forEach { t ->
                    ShortNavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = {
                            BadgedBox(badge = {
                                if (t == Tab.Home && state.pendingPairings.isNotEmpty()) Badge { Text("${state.pendingPairings.size}") }
                            }) { Icon(painterResource(t.icon), contentDescription = null) }
                        },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (slideInHorizontally { it / 6 * direction } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it / 6 * direction } + fadeOut())
            },
            label = "tab",
        ) { current ->
            ScrollingPage(padding) {
                when (current) {
                    Tab.Home -> HomeScreen(config, onChange, state) { tab = Tab.Settings }
                    Tab.Settings -> SettingsScreen(config, onChange, state) { showAbout = true }
                }
            }
        }
    }
}

@Composable
fun ScrollingPage(padding: PaddingValues, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .consumeWindowInsets(padding)
            .imePadding()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.widthIn(max = MAX_CONTENT_WIDTH).fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
private fun RefreshOnResume(state: AppState) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) state.refresh() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

private val MAX_CONTENT_WIDTH = 640.dp
