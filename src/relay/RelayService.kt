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
import java.util.concurrent.Executors

class RelayService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val notifications by lazy { RelayNotifications(this) }
    private val locks by lazy { RelayLocks(this) }
    private val mdns by lazy { MdnsAdvertiser(this, RelayLog::append) }
    private val network by lazy { NetworkMonitor(this) }
    private val meter = ThroughputMeter(TICK_MS)
    private val ticker = Runnable { tick() }
    private var relay: Relay? = null

    /** Starts run one at a time, so a stale start always lets go of its ports before the next one binds. */
    private val starter = Executors.newSingleThreadExecutor { Thread(it, "relay-start") }

    /**
     * Bumped on every start and teardown, under [lock]; a start whose generation is no longer current is discarded.
     * [pending] is a relay that has bound its ports but isn't adopted yet, so teardown can close it right away.
     */
    private val lock = Any()
    private var generation = 0
    private var pending: Relay? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_RESTART && RelayState.status.value.isActive) {
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
        starter.shutdown()
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
        val current = synchronized(lock) { ++generation }
        RelayState.update(RelayStatus(starting = true, target = config.target))
        locks.acquire()
        starter.execute {
            if (!isCurrent(current)) return@execute
            val address = runCatching { InetAddress.getByName(config.target) }.getOrElse {
                if (isCurrent(current)) fail("Can't resolve ${config.target}: ${it.message}")
                return@execute
            }
            val started = Relay(address, config.relay, ClientFilter(network::hotspotPrefixes), RelayLog::append)
            val errors = started.start()
            val adopted = synchronized(lock) { (current == generation).also { if (it) pending = started } }
            if (!adopted) {
                started.stop()
                return@execute
            }
            handler.post { onRelayStarted(current, config, address, started, errors) }
        }
    }

    private fun onRelayStarted(current: Int, config: RelayConfig, address: InetAddress, started: Relay, errors: List<String>) {
        val adopted = synchronized(lock) {
            (current == generation && pending === started).also { if (it) pending = null }
        }
        if (!adopted) return
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

    private fun isCurrent(start: Int) = synchronized(lock) { start == generation }

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
        synchronized(lock) {
            generation++
            pending?.stop()
            pending = null
        }
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
