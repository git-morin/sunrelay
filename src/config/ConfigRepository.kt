package dev.morin.sunrelay.config

import android.content.Context
import android.content.SharedPreferences
import dev.morin.sunrelay.ui.theme.Accent
import dev.morin.sunrelay.ui.theme.ThemeMode
import dev.morin.sunrelay.vpn.Vpns

class ConfigRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): RelayConfig {
        val defaults = RelayConfig()
        val reader = object : SettingsReader {
            override fun string(key: String): String? = prefs.getString(key, null)
        }
        return RelayConfig(
            network = NetworkSettings(
                selectedKey = prefs.getString(NETWORK, null) ?: defaults.network.selectedKey,
                vpns = Vpns.load(reader),
            ),
            target = prefs.getString(TARGET, null) ?: defaults.target,
            relay = RelaySettings(
                tcpPorts = prefs.getString(TCP, null) ?: defaults.relay.tcpPorts,
                udpPorts = prefs.getString(UDP, null) ?: defaults.relay.udpPorts,
                udpIdleSec = prefs.getInt(UDP_IDLE, defaults.relay.udpIdleSec),
                mdns = prefs.getBoolean(MDNS, defaults.relay.mdns),
            ),
            login = SunshineLogin(
                user = prefs.getString(SUNSHINE_USER, null).orEmpty(),
                password = prefs.getString(SUNSHINE_PASSWORD, null).orEmpty(),
                pairedUuid = prefs.getString(PAIRED_UUID, null).orEmpty(),
            ),
            appearance = Appearance(
                accent = Accent.of(prefs.getString(ACCENT, null)),
                themeMode = ThemeMode.of(prefs.getString(THEME_MODE, null)),
            ),
            onboarded = prefs.getBoolean(ONBOARDED, false),
            autoCheck = prefs.getBoolean(AUTO_CHECK, defaults.autoCheck),
            developer = DeveloperSettings(
                unlocked = prefs.getBoolean(DEV_UNLOCKED, false),
                prereleases = prefs.getBoolean(DEV_PRERELEASES, false),
            ),
        )
    }

    fun save(config: RelayConfig) {
        val editor = prefs.edit()
        val writer = object : SettingsWriter {
            override fun string(key: String, value: String) {
                editor.putString(key, value)
            }
        }
        config.network.vpns.forEach { it.writeTo(writer) }
        editor
            .putString(NETWORK, config.network.selectedKey)
            .putString(TARGET, config.target.trim())
            .putString(TCP, config.relay.tcpPorts)
            .putString(UDP, config.relay.udpPorts)
            .putInt(UDP_IDLE, config.relay.udpIdleSec)
            .putBoolean(MDNS, config.relay.mdns)
            .putString(SUNSHINE_USER, config.login.user.trim())
            .putString(SUNSHINE_PASSWORD, config.login.password)
            .putString(PAIRED_UUID, config.login.pairedUuid)
            .putBoolean(ONBOARDED, config.onboarded)
            .putBoolean(AUTO_CHECK, config.autoCheck)
            .putBoolean(DEV_UNLOCKED, config.developer.unlocked)
            .putBoolean(DEV_PRERELEASES, config.developer.prereleases)
            .putString(ACCENT, config.appearance.accent.key)
            .putString(THEME_MODE, config.appearance.themeMode.key)
            .apply()
    }

    private companion object {
        const val PREFS = "relay"
        const val NETWORK = "netMode"
        const val TARGET = "target"
        const val TCP = "tcp"
        const val UDP = "udp"
        const val UDP_IDLE = "udpIdle"
        const val MDNS = "mdns"
        const val SUNSHINE_USER = "sunUser"
        const val SUNSHINE_PASSWORD = "sunPass"
        const val PAIRED_UUID = "pairedUuid"
        const val ONBOARDED = "onboarded"
        const val AUTO_CHECK = "autoCheck"
        const val DEV_UNLOCKED = "devUnlocked"
        const val DEV_PRERELEASES = "devPrereleases"
        const val ACCENT = "accent"
        const val THEME_MODE = "themeMode"
    }
}
