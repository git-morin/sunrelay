package dev.morin.sunrelay.net

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

object SystemScreens {
    fun vpnSettings(context: Context) {
        open(context, newTask(Settings.ACTION_VPN_SETTINGS), newTask(Settings.ACTION_WIRELESS_SETTINGS))
    }

    fun hotspotSettings(context: Context) {
        open(
            context,
            newTask("android.settings.TETHER_SETTINGS"),
            newTask("com.android.settings.WIFI_TETHER_SETTINGS"),
            newTask(Settings.ACTION_WIRELESS_SETTINGS),
        )
    }

    @SuppressLint("BatteryLife")
    fun batteryOptimization(context: Context) {
        open(
            context,
            newTask(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(Uri.parse("package:${context.packageName}")),
            newTask(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        )
    }

    fun url(context: Context, url: String) {
        open(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Starts the first intent some app can handle, and says so when none can instead of crashing. */
    fun open(context: Context, vararg candidates: Intent): Boolean {
        val opened = candidates.any { intent ->
            try {
                context.startActivity(intent)
                true
            } catch (_: ActivityNotFoundException) {
                false
            } catch (_: SecurityException) {
                false
            }
        }
        if (!opened) Toast.makeText(context, "No app on this phone can open that", Toast.LENGTH_SHORT).show()
        return opened
    }

    private fun newTask(action: String) = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
