package dev.morin.sunrelay.relay

internal class ThroughputMeter(private val intervalMs: Long) {
    private var lastUp = 0L
    private var lastDown = 0L

    fun reset() {
        lastUp = 0
        lastDown = 0
    }

    fun sample(bytesUp: Long, bytesDown: Long): Pair<Long, Long> {
        val rates = bitsPerSecond(bytesUp - lastUp) to bitsPerSecond(bytesDown - lastDown)
        lastUp = bytesUp
        lastDown = bytesDown
        return rates
    }

    private fun bitsPerSecond(bytes: Long) = bytes * 8 * 1000 / intervalMs
}
