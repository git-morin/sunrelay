package dev.morin.sunrelay.relay

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.service.quicksettings.TileService
import dev.morin.sunrelay.config.ConfigRepository
import dev.morin.sunrelay.config.RelayConfig
import dev.morin.sunrelay.net.NetworkMonitor
import java.net.InetAddress
import kotlin.concurrent.thread

class RelayService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val notifications by lazy { RelayNotifications(this) }
    private val locks by lazy { RelayLocks(this) }
    private val mdns by lazy { MdnsAdvertiser(this, RelayLog::append) }
    private val network by lazy { NetworkMonitor(this) }
    private val meter = ThroughputMeter(TICK_MS)
    private val ticker = Runnable { tick() }
    private var relay: Relay? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_RESTART && relay != null) {
            teardown()
            RelayLog.append("restarting with new settings")
        }
        startForeground(RelayNotifications.ID, notifications.build("Starting…"), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        if (relay == null && !RelayState.status.value.starting) startRelay(ConfigRepository(this).load())
        return START_STICKY
    }

    override fun onDestroy() {
        val status = RelayState.status.value
        val error = status.error.takeIf { !status.running }
        teardown()
        RelayState.update(RelayStatus(error = error))
        RelayLog.append("relay stopped")
        refreshTile()
        super.onDestroy()
    }

    private fun startRelay(config: RelayConfig) {
        if (!config.hasTarget) {
            fail("No Sunshine host set. Pick one in SunRelay first.")
            return
        }
        RelayState.update(RelayStatus(starting = true, target = config.target))
        locks.acquire()
        thread(name = "relay-start") {
            val address = runCatching { InetAddress.getByName(config.target) }.getOrElse {
                fail("Can't resolve ${config.target}: ${it.message}")
                return@thread
            }
            val started = Relay(address, config.relay, RelayLog::append)
            val errors = started.start()
            handler.post { onRelayStarted(config, address, started, errors) }
        }
    }

    private fun onRelayStarted(config: RelayConfig, address: InetAddress, started: Relay, errors: List<String>) {
        if (!RelayState.status.value.starting) {
            started.stop()
            return
        }
        relay = started
        meter.reset()
        RelayState.update(
            RelayStatus(
                running = true,
                target = config.target,
                targetIp = address.hostAddress.orEmpty(),
                error = errors.takeIf { it.isNotEmpty() }?.joinToString("\n", "Some ports failed to bind:\n"),
            ),
        )
        if (config.relay.mdns) mdns.start()
        tick()
        refreshTile()
    }

    private fun fail(message: String) {
        RelayLog.append(message)
        handler.post {
            RelayState.update(RelayStatus(error = message))
            stopSelf()
        }
    }

    private fun tick() {
        val stats = relay?.stats ?: return
        val up = stats.bytesUp.get()
        val down = stats.bytesDown.get()
        val (upBps, downBps) = meter.sample(up, down)
        val status = RelayState.status.value.copy(
            hotspot = network.hotspotAddresses(),
            tcpActive = stats.tcpActive.get(),
            udpActive = stats.udpActive.get(),
            clients = stats.clients,
            upBps = upBps,
            downBps = downBps,
        )
        RelayState.update(status)
        notifications.update(status.notificationText())
        handler.postDelayed(ticker, TICK_MS)
    }

    private fun teardown() {
        handler.removeCallbacks(ticker)
        mdns.stop()
        relay?.stop()
        relay = null
        locks.release()
        RelayState.update(RelayStatus())
    }

    private fun refreshTile() = TileService.requestListeningState(this, ComponentName(this, RelayTile::class.java))

    companion object {
        private const val ACTION_STOP = "dev.morin.sunrelay.STOP"
        private const val ACTION_RESTART = "dev.morin.sunrelay.RESTART"
        private const val TICK_MS = 2000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, RelayService::class.java))
        }

        fun restartIfRunning(context: Context) {
            if (RelayState.status.value.isActive) {
                context.startForegroundService(Intent(context, RelayService::class.java).setAction(ACTION_RESTART))
            }
        }

        fun stop(context: Context) {
            context.startService(stopIntent(context))
        }

        internal fun stopIntent(context: Context): Intent =
            Intent(context, RelayService::class.java).setAction(ACTION_STOP)
    }
}
