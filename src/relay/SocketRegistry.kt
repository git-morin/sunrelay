package dev.morin.sunrelay.relay

import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

internal class SocketRegistry {
    @Volatile
    var closed = false
        private set

    private val open: MutableSet<Closeable> = ConcurrentHashMap.newKeySet()

    fun <T : Closeable> track(socket: T): T = socket.also { open += it }

    fun release(socket: Closeable) {
        open -= socket
        runCatching { socket.close() }
    }

    fun closeAll() {
        closed = true
        open.toList().forEach { runCatching { it.close() } }
        open.clear()
    }
}
