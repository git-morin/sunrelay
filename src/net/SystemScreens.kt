package dev.morin.sunrelay.net

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

object SystemScreens {
    fun vpnSettings(context: Context) = context.startActivity(newTask(Settings.ACTION_VPN_SETTINGS))

    fun hotspotSettings(context: Context) {
        val candidates = listOf(
            "android.settings.TETHER_SETTINGS",
            "com.android.settings.WIFI_TETHER_SETTINGS",
            Settings.ACTION_WIRELESS_SETTINGS,
        )
        candidates.firstOrNull { runCatching { context.startActivity(newTask(it)) }.isSuccess }
    }

    @SuppressLint("BatteryLife")
    fun batteryOptimization(context: Context) = context.startActivity(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
    )

    fun url(context: Context, url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    private fun newTask(action: String) = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
