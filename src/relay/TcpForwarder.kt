package dev.morin.sunrelay.relay

import android.os.ParcelFileDescriptor
import android.system.Os
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

internal class TcpForwarder(
    private val target: InetAddress,
    private val port: Int,
    private val clients: ClientFilter,
    private val stats: RelayStats,
    private val sockets: SocketRegistry,
    private val log: (String) -> Unit,
    private val bindAddress: InetAddress? = null,
) {
    fun start() {
        val server = sockets.open(ServerSocket()) {
            it.reuseAddress = true
            it.bind(InetSocketAddress(bindAddress, port), BACKLOG)
        }
        thread(name = "tcp-accept-$port", isDaemon = true) { acceptLoop(server) }
    }

    private fun acceptLoop(server: ServerSocket) {
        while (!sockets.closed) {
            // Tracked as soon as it's accepted, so a stop from here on closes it.
            val client = try {
                sockets.track(server.accept())
            } catch (e: Exception) {
                if (!sockets.closed) log("tcp $port accept: ${e.message}")
                return
            }
            if (!clients.allows(client.inetAddress)) {
                log("tcp $port: refused ${client.remoteSocketAddress}, not on the hotspot")
                sockets.release(client)
                continue
            }
            thread(name = "tcp-$port-${client.remoteSocketAddress}", isDaemon = true) { forward(client) }
        }
    }

    private fun forward(client: Socket) {
        val clientIp = client.inetAddress.hostAddress ?: "?"
        var upstream: Socket? = null
        var counted = false
        try {
            val remote = sockets.track(Socket()).also { upstream = it }
            remote.connect(InetSocketAddress(target, port), CONNECT_TIMEOUT_MS)
            tune(client)
            tune(remote)
            val fromClient = client.getInputStream()
            val toClient = client.getOutputStream()
            val fromUpstream = remote.getInputStream()
            val toUpstream = remote.getOutputStream()
            stats.tcpActive.incrementAndGet()
            stats.clientJoined(clientIp)
            counted = true
            log("tcp $port: ${client.remoteSocketAddress} connected")
            val uplink = thread(name = "tcp-$port-up", isDaemon = true) {
                pump(fromClient, toUpstream, stats.bytesUp)
                runCatching { remote.shutdownOutput() }
            }
            pump(fromUpstream, toClient, stats.bytesDown)
            runCatching { client.shutdownOutput() }
            uplink.join()
            log("tcp $port: ${client.remoteSocketAddress} closed")
        } catch (e: Exception) {
            if (!sockets.closed) log("tcp $port: ${e.message}")
        } finally {
            if (counted) {
                stats.tcpActive.decrementAndGet()
                stats.clientLeft(clientIp)
            }
            upstream?.let(sockets::release)
            sockets.release(client)
        }
    }

    private fun pump(source: InputStream, sink: OutputStream, counter: AtomicLong) {
        val buffer = ByteArray(BUFFER_SIZE)
        runCatching {
            while (true) {
                val n = source.read(buffer)
                if (n < 0) break
                sink.write(buffer, 0, n)
                counter.addAndGet(n.toLong())
            }
        }
    }

    private fun tune(socket: Socket) {
        socket.tcpNoDelay = true
        socket.keepAlive = true
        runCatching {
            ParcelFileDescriptor.fromSocket(socket).use { pfd ->
                val fd = pfd.fileDescriptor
                Os.setsockoptInt(fd, IPPROTO_TCP, TCP_KEEPIDLE, KEEPALIVE_IDLE_SEC)
                Os.setsockoptInt(fd, IPPROTO_TCP, TCP_KEEPINTVL, KEEPALIVE_INTERVAL_SEC)
                Os.setsockoptInt(fd, IPPROTO_TCP, TCP_KEEPCNT, KEEPALIVE_PROBES)
            }
        }.onFailure { log("keepalive tuning failed: ${it.message}") }
    }

    private companion object {
        const val BACKLOG = 64
        const val CONNECT_TIMEOUT_MS = 5000
        const val BUFFER_SIZE = 32 * 1024
        const val IPPROTO_TCP = 6
        const val TCP_KEEPIDLE = 4
        const val TCP_KEEPINTVL = 5
        const val TCP_KEEPCNT = 6
        const val KEEPALIVE_IDLE_SEC = 15
        const val KEEPALIVE_INTERVAL_SEC = 15
        const val KEEPALIVE_PROBES = 4
    }
}
