package dev.morin.sunrelay.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager

data class PermissionStatus(
    val notifications: Boolean = true,
    val localNetwork: Boolean = true,
    val unrestrictedBattery: Boolean = true,
) {
    val runtimeGranted: Boolean get() = notifications && localNetwork

    val missingRuntime: Array<String>
        get() = listOfNotNull(
            Manifest.permission.POST_NOTIFICATIONS.takeUnless { notifications },
            AppPermissions.LOCAL_NETWORK.takeUnless { localNetwork },
        ).toTypedArray()

    val missingLabels: List<String>
        get() = listOfNotNull(
            "local network".takeUnless { localNetwork },
            "notifications".takeUnless { notifications },
            "unrestricted battery".takeUnless { unrestrictedBattery },
        )
}

object AppPermissions {
    const val LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
    val RUNTIME = arrayOf(LOCAL_NETWORK, Manifest.permission.POST_NOTIFICATIONS)

    fun status(context: Context) = PermissionStatus(
        notifications = context.granted(Manifest.permission.POST_NOTIFICATIONS),
        localNetwork = context.granted(LOCAL_NETWORK),
        unrestrictedBattery = context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName),
    )

    private fun Context.granted(permission: String) =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
