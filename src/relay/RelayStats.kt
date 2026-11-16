package dev.morin.sunrelay.relay

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class RelayStats {
    val bytesUp = AtomicLong()
    val bytesDown = AtomicLong()
    val tcpActive = AtomicLong()
    val udpActive = AtomicLong()
    private val clientRefs = ConcurrentHashMap<String, Int>()

    val clients: Set<String> get() = clientRefs.keys.toSet()

    fun clientJoined(ip: String) {
        clientRefs.merge(ip, 1, Int::plus)
    }

    fun clientLeft(ip: String) {
        clientRefs.computeIfPresent(ip) { _, n -> if (n <= 1) null else n - 1 }
    }
}
