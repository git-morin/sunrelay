package dev.morin.sunrelay.relay

import java.io.Closeable
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

internal class RelayClosedException : IOException("relay stopped")

internal class SocketRegistry {
    @Volatile
    var closed = false
        private set

    private val open: MutableSet<Closeable> = ConcurrentHashMap.newKeySet()

    internal val size: Int get() = open.size

    /**
     * Registers [socket] so [closeAll] closes it. Once the registry is closed, the socket is closed right away and
     * [RelayClosedException] is thrown, so nothing opened during or after a stop outlives it.
     */
    fun <T : Closeable> track(socket: T): T {
        open += socket
        // closeAll sets [closed] before it snapshots [open], so a socket added after that snapshot is caught here.
        if (closed) {
            release(socket)
            throw RelayClosedException()
        }
        return socket
    }

    /** Tracks [socket], then runs [setup] on it, closing it if [setup] fails. */
    fun <T : Closeable> open(socket: T, setup: (T) -> Unit): T {
        track(socket)
        try {
            setup(socket)
        } catch (e: Throwable) {
            release(socket)
            throw e
        }
        return socket
    }

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
