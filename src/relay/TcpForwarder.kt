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
) {
    fun start() {
        val server = sockets.track(ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(port), BACKLOG)
        })
        thread(name = "tcp-accept-$port", isDaemon = true) { acceptLoop(server) }
    }

    private fun acceptLoop(server: ServerSocket) {
        while (!sockets.closed) {
            val client = try {
                server.accept()
            } catch (e: Exception) {
                if (!sockets.closed) log("tcp $port accept: ${e.message}")
                return
            }
            if (!clients.allows(client.inetAddress)) {
                log("tcp $port: refused ${client.remoteSocketAddress}, not on the hotspot")
                runCatching { client.close() }
                continue
            }
            thread(name = "tcp-$port-${client.remoteSocketAddress}", isDaemon = true) { forward(client) }
        }
    }

    private fun forward(client: Socket) {
        sockets.track(client)
        val clientIp = client.inetAddress.hostAddress ?: "?"
        val upstream = sockets.track(Socket())
        var counted = false
        try {
            upstream.connect(InetSocketAddress(target, port), CONNECT_TIMEOUT_MS)
            tune(client)
            tune(upstream)
            val fromClient = client.getInputStream()
            val toClient = client.getOutputStream()
            val fromUpstream = upstream.getInputStream()
            val toUpstream = upstream.getOutputStream()
            stats.tcpActive.incrementAndGet()
            stats.clientJoined(clientIp)
            counted = true
            log("tcp $port: ${client.remoteSocketAddress} connected")
            val uplink = thread(name = "tcp-$port-up", isDaemon = true) {
                pump(fromClient, toUpstream, stats.bytesUp)
                runCatching { upstream.shutdownOutput() }
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
            sockets.release(upstream)
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
