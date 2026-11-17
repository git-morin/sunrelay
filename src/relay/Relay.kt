package dev.morin.sunrelay.relay

import dev.morin.sunrelay.config.RelaySettings
import java.net.InetAddress

class Relay(
    private val target: InetAddress,
    private val settings: RelaySettings,
    private val clients: ClientFilter,
    private val log: (String) -> Unit,
) {
    val stats = RelayStats()
    private val sockets = SocketRegistry()

    fun start(): List<String> {
        val errors = mutableListOf<String>()
        settings.tcpPortList.forEach { port ->
            runCatching { TcpForwarder(target, port, clients, stats, sockets, log).start() }
                .onFailure { errors += "tcp/$port: ${it.message}" }
        }
        settings.udpPortList.forEach { port ->
            runCatching { UdpForwarder(target, port, settings.udpIdleMs, clients, stats, sockets, log).start() }
                .onFailure { errors += "udp/$port: ${it.message}" }
        }
        log("relaying hotspot clients -> ${target.hostAddress} (tcp ${settings.tcpPortList}, udp ${settings.udpPortList})")
        errors.forEach { log("bind failed: $it") }
        return errors
    }

    fun stop() = sockets.closeAll()
}
