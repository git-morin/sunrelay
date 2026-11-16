package dev.morin.sunrelay.relay

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager

internal class RelayLocks(private val context: Context) {
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    @Suppress("DEPRECATION")
    fun acquire() {
        wakeLock = context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SunRelay:relay").apply { acquire() }
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "SunRelay:wifi").apply { acquire() }
        multicastLock = wifi.createMulticastLock("SunRelay:mdns").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    fun release() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wifiLock?.takeIf { it.isHeld }?.release()
        multicastLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        wifiLock = null
        multicastLock = null
    }
}
