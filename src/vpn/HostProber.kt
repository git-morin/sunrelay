package dev.morin.sunrelay.vpn

import dev.morin.sunrelay.sunshine.ServerInfo
import dev.morin.sunrelay.sunshine.SunshineProbe
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal class HostProber(private val threads: Int, private val timeoutMs: Int, private val waitSeconds: Long) {

    fun <T> probe(
        items: List<T>,
        address: (T) -> String,
        onProbed: (done: Int, total: Int) -> Unit,
        toHost: (T, ServerInfo) -> FoundHost,
    ): List<FoundHost> {
        val done = AtomicInteger()
        onProbed(0, items.size)
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val futures = items.map { item ->
                pool.submit(Callable {
                    SunshineProbe.fetch(address(item), timeoutMs).getOrNull()?.let { toHost(item, it) }
                        .also { onProbed(done.incrementAndGet(), items.size) }
                })
            }
            return futures.mapNotNull { runCatching { it.get(waitSeconds, TimeUnit.SECONDS) }.getOrNull() }
        } finally {
            pool.shutdownNow()
        }
    }

    companion object {
        val forDevices = HostProber(threads = 16, timeoutMs = 4000, waitSeconds = 10)
        val forSubnets = HostProber(threads = 64, timeoutMs = 1500, waitSeconds = 30)
    }
}
