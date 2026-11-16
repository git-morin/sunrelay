package dev.morin.sunrelay.relay

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.morin.sunrelay.MainActivity

class RelayTile : TileService() {
    override fun onStartListening() = refresh()

    override fun onClick() {
        if (RelayState.status.value.isActive) RelayService.stop(this) else startOrOpenApp()
        refresh()
    }

    private fun startOrOpenApp() {
        runCatching { RelayService.start(this) }.onFailure {
            val intent = Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_AUTOSTART, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        }
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val status = RelayState.status.value
        tile.state = if (status.isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = when {
            status.running -> status.hotspotIp ?: "on"
            status.starting -> "starting"
            else -> "off"
        }
        tile.updateTile()
    }
}
