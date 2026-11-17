package dev.morin.sunrelay.relay

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

internal class UdpForwarder(
    target: InetAddress,
    private val port: Int,
    private val idleMs: Long,
    private val clients: ClientFilter,
    private val stats: RelayStats,
    private val sockets: SocketRegistry,
    private val log: (String) -> Unit,
    private val bindAddress: InetAddress? = null,
) {
    private val destination = InetSocketAddress(target, port)
    private val sessions = ConcurrentHashMap<SocketAddress, Session>()
    private val refused = mutableSetOf<SocketAddress>()
    private var full = false

    fun start() {
        // Built outside apply: in there, `port` would be DatagramSocket.getPort(), which is -1 until connected.
        val local = InetSocketAddress(bindAddress, port)
        val listener = sockets.track(DatagramSocket(null).apply {
            reuseAddress = true
            receiveBufferSize = SOCKET_BUFFER
            sendBufferSize = SOCKET_BUFFER
            bind(local)
        })
        thread(name = "udp-$port", isDaemon = true) { receiveLoop(listener) }
    }

    private fun receiveLoop(listener: DatagramSocket) {
        val buffer = ByteArray(MAX_DATAGRAM)
        val packet = DatagramPacket(buffer, buffer.size)
        while (!sockets.closed) {
            packet.setData(buffer, 0, buffer.size)
            try {
                listener.receive(packet)
            } catch (e: Exception) {
                if (!sockets.closed) log("udp $port read: ${e.message}")
                return
            }
            val source = packet.socketAddress
            val session = sessions[source] ?: openSession(listener, packet) ?: continue
            session.sendUpstream(buffer, packet.length)
        }
    }

    private fun openSession(listener: DatagramSocket, packet: DatagramPacket): Session? {
        val source = packet.socketAddress
        if (!clients.allows(packet.address)) {
            if (refused.size < MAX_REFUSED_LOGGED && refused.add(source)) log("udp $port: refused $source, not on the hotspot")
            return null
        }
        if (sessions.size >= MAX_SESSIONS) {
            if (!full) log("udp $port: $MAX_SESSIONS sessions open, refusing new ones until some go idle")
            full = true
            return null
        }
        full = false
        return runCatching { Session(listener, source).also { it.start() } }
            .onFailure { log("udp $port dial: ${it.message}") }
            .getOrNull()
    }

    private inner class Session(private val listener: DatagramSocket, private val source: SocketAddress) {
        private val clientIp = (source as? InetSocketAddress)?.address?.hostAddress ?: "?"
        private val upstream = sockets.track(DatagramSocket().apply {
            receiveBufferSize = SOCKET_BUFFER
            sendBufferSize = SOCKET_BUFFER
            soTimeout = RECEIVE_TIMEOUT_MS
            connect(destination)
        })

        @Volatile
        private var lastSeen = System.currentTimeMillis()

        fun start() {
            sessions[source] = this
            stats.udpActive.incrementAndGet()
            stats.clientJoined(clientIp)
            log("udp $port: new session $source")
            thread(name = "udp-$port-$source", isDaemon = true) { replyLoop() }
        }

        fun sendUpstream(data: ByteArray, length: Int) {
            lastSeen = System.currentTimeMillis()
            runCatching {
                upstream.send(DatagramPacket(data, length))
                stats.bytesUp.addAndGet(length.toLong())
            }
        }

        private fun replyLoop() {
            val buffer = ByteArray(MAX_DATAGRAM)
            val packet = DatagramPacket(buffer, buffer.size)
            while (!sockets.closed) {
                packet.setData(buffer, 0, buffer.size)
                try {
                    upstream.receive(packet)
                } catch (_: SocketTimeoutException) {
                    if (System.currentTimeMillis() - lastSeen > idleMs) break
                    continue
                } catch (_: Exception) {
                    break
                }
                lastSeen = System.currentTimeMillis()
                runCatching {
                    listener.send(DatagramPacket(buffer, packet.length, source))
                    stats.bytesDown.addAndGet(packet.length.toLong())
                }
            }
            close()
        }

        private fun close() {
            sessions.remove(source)
            sockets.release(upstream)
            stats.udpActive.decrementAndGet()
            stats.clientLeft(clientIp)
            log("udp $port: session $source closed")
        }
    }

    private companion object {
        const val SOCKET_BUFFER = 4 shl 20
        const val MAX_DATAGRAM = 65535
        const val RECEIVE_TIMEOUT_MS = 1000
        const val MAX_REFUSED_LOGGED = 32

        /** Each session holds a thread and two 4 MB buffers; a few Moonlight clients need a handful per port. */
        const val MAX_SESSIONS = 32
    }
}
